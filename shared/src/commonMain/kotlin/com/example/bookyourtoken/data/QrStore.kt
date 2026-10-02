package com.example.bookyourtoken.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Instant

/**
 * Files only this app can read: Context.filesDir on Android (excluded from backups), Application
 * Support on iOS (file protection on, excluded from backups). Never shared storage or the gallery.
 */
interface PrivateFiles {
    fun read(name: String): ByteArray?

    /** Replaces the file atomically. */
    fun write(name: String, bytes: ByteArray)

    fun delete(name: String)
}

/** The last QR fetched successfully, for showing at the counter when there's no signal. */
class QrSnapshot(
    val png: ByteArray,
    val rows: List<QrTokenRow>,
    val fetchedAt: Instant,
    /** Food dates (dd-MM-yyyy) this QR covers; once all are before today the copy is deleted. */
    val coversDates: List<String>
)

/**
 * The offline copy of the QR. It's a redemption credential, so it lives in [PrivateFiles] only and
 * is deleted once stale, on sign-out and whenever the credentials change.
 */
class QrStore(private val files: PrivateFiles) {

    fun save(snapshot: QrSnapshot) {
        val meta = JsonObject(
            mapOf(
                "fetchedAt" to JsonPrimitive(snapshot.fetchedAt.toEpochMilliseconds()),
                "coversDates" to JsonArray(snapshot.coversDates.map(::JsonPrimitive)),
                "rows" to JsonArray(
                    snapshot.rows.map {
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(it.tokenName),
                                "date" to JsonPrimitive(it.date),
                                "meal" to JsonPrimitive(it.mealTime),
                                "qty" to JsonPrimitive(it.quantity)
                            )
                        )
                    }
                )
            )
        )
        files.write(PNG_FILE, snapshot.png)
        files.write(META_FILE, meta.toString().encodeToByteArray())
    }

    /** The saved copy, or null if there's none (or it's unreadable). Doesn't check staleness. */
    fun load(): QrSnapshot? {
        val png = files.read(PNG_FILE)?.takeIf { it.isNotEmpty() } ?: return null
        val meta = files.read(META_FILE) ?: return null
        return runCatching {
            val obj = Json.parseToJsonElement(meta.decodeToString()).jsonObject
            QrSnapshot(
                png = png,
                rows = obj.getValue("rows").jsonArray.map { row ->
                    val o = row.jsonObject
                    QrTokenRow(o.text("name"), o.text("date"), o.text("meal"), o.text("qty"))
                },
                fetchedAt = Instant.fromEpochMilliseconds(obj.getValue("fetchedAt").jsonPrimitive.long),
                coversDates = obj.getValue("coversDates").jsonArray.map { it.jsonPrimitive.content }
            )
        }.getOrNull()
    }

    /** The saved copy if it still covers today or later; deletes it otherwise. */
    fun loadValid(today: LocalDate = DateUtils.today()): QrSnapshot? {
        val snapshot = load()
        if (snapshot == null || isStale(snapshot, today)) {
            clear()
            return null
        }
        return snapshot
    }

    fun deleteIfStale(today: LocalDate = DateUtils.today()) {
        loadValid(today)
    }

    fun clear() {
        files.delete(PNG_FILE)
        files.delete(META_FILE)
    }

    private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

    companion object {
        private const val PNG_FILE = "qr.png"
        private const val META_FILE = "qr.json"

        /**
         * Stale once every covered food date is before today (Asia/Kolkata). If no date could be read
         * at all, fall back to the day it was fetched.
         */
        internal fun isStale(snapshot: QrSnapshot, today: LocalDate): Boolean {
            val dates = snapshot.coversDates.mapNotNull { DateUtils.parseLooseDate(it) }
            val fetchedOn = DateUtils.dateOf(snapshot.fetchedAt)
            return if (dates.isEmpty()) fetchedOn < today else dates.all { it < today }
        }
    }
}
