package com.example.bookyourtoken.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** A build published on the public releases repo (tag `v<versionCode>`, one `.apk` asset). */
data class UpdateRelease(
    val versionCode: Int,
    val versionName: String,
    /** Release notes as plain text, with the [GitHubReleases.REQUIRED_MARKER] already removed. */
    val notes: String,
    /** The notes contained [GitHubReleases.REQUIRED_MARKER]: no Later button, can't be dismissed. */
    val required: Boolean,
    val apkUrl: String,
    /** The asset's size in bytes — the progress fallback when the download has no Content-Length. */
    val apkSize: Long
)

object GitHubReleases {
    const val REQUIRED_MARKER = "[required]"
    private val TAG = Regex("""v(\d+)""")

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    /**
     * The `GET /repos/{owner}/{repo}/releases/latest` body, or null when it isn't a release the app
     * can use: a draft or prerelease, a tag that isn't `v<number>`, or no `.apk` asset.
     * Throws IllegalArgumentException if the body isn't a JSON object.
     */
    fun parseLatest(body: String): UpdateRelease? {
        val o = Json.parseToJsonElement(body).jsonObject
        if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) return null
        if ((o["prerelease"] as? JsonPrimitive)?.booleanOrNull == true) return null
        val tag = o.string("tag_name")?.trim() ?: return null
        val versionCode = TAG.matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val apk = (o["assets"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .firstOrNull { it.string("name")?.endsWith(".apk") == true }
            ?: return null
        val url = apk.string("browser_download_url")?.takeIf { it.isNotBlank() } ?: return null
        val notes = o.string("body").orEmpty()
        return UpdateRelease(
            versionCode = versionCode,
            versionName = o.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: tag,
            notes = cleanNotes(notes),
            required = REQUIRED_MARKER in notes,
            apkUrl = url,
            apkSize = (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0L
        )
    }

    private fun cleanNotes(body: String): String =
        body.replace(REQUIRED_MARKER, "")
            .replace("\r\n", "\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}

/** When automatic checks run and when the dialog may show itself. */
object UpdatePolicy {
    /** Unauthenticated GitHub calls are limited per IP, and a whole hostel shares one. */
    const val CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
    const val SNOOZE_MILLIS = 24 * 60 * 60 * 1000L

    /** A clock that went backwards also counts as due, so a wrong clock can't block checks forever. */
    fun isCheckDue(lastCheckAt: Long, now: Long): Boolean =
        lastCheckAt <= 0L || now < lastCheckAt || now - lastCheckAt >= CHECK_INTERVAL_MILLIS

    /** "Later" holds back that one version; a newer release, or a required one, shows anyway. */
    fun isSnoozed(release: UpdateRelease, snoozedVersionCode: Int, snoozedUntil: Long, now: Long): Boolean =
        !release.required && release.versionCode == snoozedVersionCode && now < snoozedUntil
}

/** What the update dialog shows. Null in [com.example.bookyourtoken.AppUpdater.dialog] = no dialog. */
sealed interface UpdateDialogState {
    val release: UpdateRelease

    /** Release notes with Later / Update. */
    data class Available(override val release: UpdateRelease) : UpdateDialogState

    /** [progress] is 0..1, or null when neither Content-Length nor the asset size is known. */
    data class Downloading(override val release: UpdateRelease, val progress: Float?) : UpdateDialogState

    data class DownloadFailed(override val release: UpdateRelease) : UpdateDialogState

    /** The APK failed the package-name / version check. It has been deleted and is never offered. */
    data class InvalidFile(override val release: UpdateRelease) : UpdateDialogState

    /** "Install unknown apps" is off. [denied]: the user came back from Settings without allowing it. */
    data class NeedsPermission(override val release: UpdateRelease, val denied: Boolean) : UpdateDialogState

    /** Android's installer was opened; Install opens it again if the user backed out. */
    data class ReadyToInstall(override val release: UpdateRelease) : UpdateDialogState
}

/** The result of Settings → "Check for updates". */
sealed interface UpdateCheckResult {
    data class UpToDate(val versionName: String) : UpdateCheckResult

    /** The update dialog is now showing. */
    data object UpdateFound : UpdateCheckResult

    data class Failed(val message: String) : UpdateCheckResult
}
