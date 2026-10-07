package com.example.bookyourtoken.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.example.bookyourtoken.AppUpdater
import com.example.bookyourtoken.BuildConfig
import com.example.bookyourtoken.data.GitHubReleases
import com.example.bookyourtoken.data.UpdateCheckResult
import com.example.bookyourtoken.data.UpdateDialogState
import com.example.bookyourtoken.data.UpdatePolicy
import com.example.bookyourtoken.data.UpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Updates from the public releases repo on GitHub (README → "Releasing an update"). No token, ever:
 * the repo is public. Automatic checks run at most every 6 hours, send the last ETag, and fail
 * silently; only Settings → "Check for updates" reports errors. Nothing is installed without passing
 * the package-name/version check, and Android's own install screen always asks the user.
 *
 * Application-scoped, so the dialog and a running download survive rotation.
 */
class UpdateManager(context: Context) : AppUpdater {

    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchMutex = Mutex()
    private val updatesDir get() = File(context.cacheDir, "updates")

    // GitHub's browser_download_url redirects to its CDN; OkHttp follows redirects by default.
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val owner = BuildConfig.UPDATE_REPO_OWNER
    private val repo = BuildConfig.UPDATE_REPO_NAME

    /** False until the owner/repo placeholders in app/build.gradle.kts are filled in. */
    private val isConfigured = listOf(owner, repo).all { it.isNotBlank() && '<' !in it }

    override val releasesPageUrl = "https://github.com/$owner/$repo/releases/latest"
    private val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
    private val userAgent = "StayEasy/${BuildConfig.VERSION_NAME}"

    private val _dialog = MutableStateFlow<UpdateDialogState?>(null)
    override val dialog: StateFlow<UpdateDialogState?> = _dialog.asStateFlow()

    private var downloadJob: Job? = null
    @Volatile private var downloadCall: Call? = null
    /** The verified APK and the version it belongs to. */
    @Volatile private var downloaded: Pair<Int, File>? = null
    private var updatedMessage: String? = null

    /** Opens the "Install unknown apps" page. Set by MainActivity, which owns the result launcher. */
    var permissionRequester: (() -> Unit)? = null

    // ---- Launch, notification, worker ----

    /** A fresh MainActivity start: post-update cleanup, then a background check. Never blocks. */
    fun onAppLaunch() {
        val lastRun = prefs.getInt(KEY_LAST_RUN_VERSION, 0)
        if (lastRun != BuildConfig.VERSION_CODE) {
            prefs.edit { putInt(KEY_LAST_RUN_VERSION, BuildConfig.VERSION_CODE) }
            scope.launch { updatesDir.deleteRecursively() }
            // 0 = first run of an install that predates this feature, or a new install: nothing to announce.
            if (lastRun in 1 until BuildConfig.VERSION_CODE) updatedMessage = "Updated to ${BuildConfig.VERSION_NAME}"
        }
        if (!isConfigured) return
        scope.launch {
            if (isCheckDue()) fetchLatest()
            val release = availableUpdate() ?: return@launch
            val snoozed = UpdatePolicy.isSnoozed(
                release,
                prefs.getInt(KEY_SNOOZED_VERSION, 0),
                prefs.getLong(KEY_SNOOZED_UNTIL, 0L),
                System.currentTimeMillis()
            )
            if (!snoozed) offer(release)
        }
    }

    /** The "Update available" notification was tapped: show the dialog even if it was put off. */
    fun showFromNotification() {
        scope.launch { availableUpdate()?.let(::offer) }
    }

    /**
     * Run by the daily reminder worker after its own work. Returns the release to notify about, at
     * most once per version.
     */
    suspend fun releaseToNotify(): UpdateRelease? {
        if (!isConfigured) return null
        if (isCheckDue()) fetchLatest()
        val release = availableUpdate() ?: return null
        if (prefs.getInt(KEY_NOTIFIED_VERSION, 0) >= release.versionCode) return null
        prefs.edit { putInt(KEY_NOTIFIED_VERSION, release.versionCode) }
        return release
    }

    /** The user left the app: no foreground service, so a running download is dropped. */
    fun onAppBackgrounded() = cancelDownload()

    override fun takeUpdatedMessage(): String? = updatedMessage.also { updatedMessage = null }

    // ---- Checking ----

    override suspend fun checkNow(): UpdateCheckResult {
        if (!isConfigured) return UpdateCheckResult.Failed("Updates aren't set up in this build.")
        return when (fetchLatest()) {
            Fetch.RateLimited -> UpdateCheckResult.Failed("GitHub is limiting requests from this network. Try again in an hour.")
            Fetch.Failed -> UpdateCheckResult.Failed("Couldn't check for updates. Try again later.")
            Fetch.Ok -> availableUpdate()
                ?.let { offer(it); UpdateCheckResult.UpdateFound }
                ?: UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
        }
    }

    private enum class Fetch { Ok, RateLimited, Failed }

    private fun isCheckDue() =
        UpdatePolicy.isCheckDue(prefs.getLong(KEY_LAST_CHECK, 0L), System.currentTimeMillis())

    /**
     * One GET of /releases/latest, with If-None-Match when the last body is cached. The parsed result
     * always comes from the cached body afterwards, so a 304 needs nothing new.
     */
    private suspend fun fetchLatest(): Fetch = withContext(Dispatchers.IO) {
        fetchMutex.withLock {
            val cachedBody = prefs.getString(KEY_BODY, null)
            val etag = prefs.getString(KEY_ETAG, null)?.takeIf { cachedBody != null }
            try {
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", userAgent)
                    .apply { if (etag != null) header("If-None-Match", etag) }
                    .build()
                client.newCall(request).execute().use { response ->
                    // Anything that reached GitHub counts towards the 6 hours; being offline doesn't.
                    prefs.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
                    when {
                        response.code == 304 && cachedBody != null -> Fetch.Ok
                        response.code == 403 || response.code == 429 -> Fetch.RateLimited
                        // No release published yet.
                        response.code == 404 -> {
                            prefs.edit { remove(KEY_BODY); remove(KEY_ETAG) }
                            Fetch.Ok
                        }
                        response.isSuccessful -> {
                            val body = response.body.string()
                            GitHubReleases.parseLatest(body) // Throws if it isn't a JSON object.
                            prefs.edit {
                                putString(KEY_BODY, body)
                                putString(KEY_ETAG, response.header("ETag"))
                            }
                            Fetch.Ok
                        }
                        else -> Fetch.Failed
                    }
                }
            } catch (_: IOException) {
                Fetch.Failed
            } catch (_: IllegalArgumentException) {
                // A malformed URL or a body that isn't a JSON object.
                Fetch.Failed
            }
        }
    }

    /** The cached latest release, if it's newer than this build and has an APK. */
    private fun availableUpdate(): UpdateRelease? {
        val body = prefs.getString(KEY_BODY, null) ?: return null
        val release = runCatching { GitHubReleases.parseLatest(body) }.getOrNull() ?: return null
        return release.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Shows [release]'s notes unless a dialog is already further along (downloading, installing…). */
    private fun offer(release: UpdateRelease) {
        _dialog.update { current ->
            if (current == null || current is UpdateDialogState.Available) UpdateDialogState.Available(release) else current
        }
    }

    // ---- Dialog actions ----

    override fun later() {
        val current = _dialog.value ?: return
        if (current.release.required || current is UpdateDialogState.Downloading) return
        prefs.edit {
            putInt(KEY_SNOOZED_VERSION, current.release.versionCode)
            putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + UpdatePolicy.SNOOZE_MILLIS)
        }
        _dialog.value = null
    }

    override fun startDownload() {
        val current = _dialog.value ?: return
        if (current is UpdateDialogState.Downloading) return
        val release = current.release
        val previous = downloadJob
        downloaded = null
        _dialog.value = UpdateDialogState.Downloading(release, initialProgress(release))
        downloadJob = scope.launch {
            // A cancelled download deletes its partial file first, so it can't delete this one.
            previous?.join()
            val apk = File(updatesDir, "update-${release.versionCode}.apk")
            try {
                download(release, apk)
            } catch (e: CancellationException) {
                apk.delete()
                throw e
            } catch (_: Exception) {
                apk.delete()
                // Cancel also aborts the call, which surfaces here as an IOException.
                if (isActive) _dialog.value = UpdateDialogState.DownloadFailed(release)
                return@launch
            }
            if (!isValidUpdate(apk)) {
                apk.delete()
                if (isActive) _dialog.value = UpdateDialogState.InvalidFile(release)
                return@launch
            }
            if (!isActive) {
                apk.delete()
                return@launch
            }
            downloaded = release.versionCode to apk
            withContext(Dispatchers.Main) { install() }
        }
    }

    override fun cancelDownload() {
        val current = _dialog.value as? UpdateDialogState.Downloading ?: return
        downloadJob?.cancel()
        downloadCall?.cancel()
        _dialog.value = UpdateDialogState.Available(current.release)
    }

    override fun requestInstallPermission() {
        val requester = permissionRequester
        if (requester == null) onInstallPermissionResult() else requester()
    }

    /** Back from the "Install unknown apps" page (or it couldn't be opened). */
    fun onInstallPermissionResult() {
        val current = _dialog.value as? UpdateDialogState.NeedsPermission ?: return
        if (canInstall()) install() else _dialog.value = current.copy(denied = true)
    }

    override fun install() {
        val release = _dialog.value?.release ?: return
        val apk = downloaded?.takeIf { it.first == release.versionCode }?.second?.takeIf { it.isFile }
        if (apk == null) {
            // The verified file is gone (cache cleared): fetch it again rather than install anything else.
            _dialog.value = UpdateDialogState.Available(release)
            return startDownload()
        }
        if (!canInstall()) {
            _dialog.value = UpdateDialogState.NeedsPermission(release, denied = false)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            _dialog.value = UpdateDialogState.ReadyToInstall(release)
        } catch (_: ActivityNotFoundException) {
            _dialog.value = UpdateDialogState.DownloadFailed(release)
        }
    }

    // ---- Download + verification ----

    private fun initialProgress(release: UpdateRelease): Float? = if (release.apkSize > 0) 0f else null

    private suspend fun download(release: UpdateRelease, target: File) {
        val dir = updatesDir
        dir.listFiles()?.forEach { it.deleteRecursively() }
        dir.mkdirs()

        val call = client.newCall(
            Request.Builder().url(release.apkUrl).header("User-Agent", userAgent).build()
        )
        downloadCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                var read = 0L
                var shownPercent = -1
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != shownPercent) {
                                    shownPercent = percent
                                    // Only while still downloading: Cancel may already have reset the dialog.
                                    _dialog.update {
                                        if (it is UpdateDialogState.Downloading) it.copy(progress = percent / 100f) else it
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            downloadCall = null
        }
    }

    /** Hard rule: never hand anything to the installer that fails this. */
    private fun isValidUpdate(apk: File): Boolean {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return false
        return info.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(info) > BuildConfig.VERSION_CODE
    }

    private fun canInstall() = context.packageManager.canRequestPackageInstalls()

    private companion object {
        const val PREFS_NAME = "app_updates"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_ETAG = "etag"
        const val KEY_BODY = "latest_body"
        const val KEY_SNOOZED_VERSION = "snoozed_version"
        const val KEY_SNOOZED_UNTIL = "snoozed_until"
        const val KEY_NOTIFIED_VERSION = "notified_version"
        const val KEY_LAST_RUN_VERSION = "last_run_version"
    }
}
