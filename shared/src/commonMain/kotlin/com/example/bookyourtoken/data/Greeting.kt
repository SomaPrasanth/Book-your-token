package com.example.bookyourtoken.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The Tomorrow screen's "Good evening, Soma 👋". */
object Greeting {
    /** Longest name the user can set in Settings. */
    const val MAX_USER_NAME = 30

    /** [hour] is the Asia/Kolkata hour, 0..23. */
    fun salutation(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }

    fun text(hour: Int, name: String?): String =
        if (name.isNullOrBlank()) "${salutation(hour)} 👋" else "${salutation(hour)}, $name 👋"

    /** "SOMA PRASANTH S" / "S. SOMA PRASANTH" / "S.SOMA PRASANTH" → "Soma"; null if it's all initials. */
    fun shortName(full: String): String? {
        val first = full.trim()
            .split(Regex("""[\s.,]+"""))
            .firstOrNull { it.length >= 2 }
            ?: return null
        return first.lowercase().replaceFirstChar { it.titlecase() }
    }

    /** What Settings saves: trimmed and capped at [MAX_USER_NAME]; null (no override) when blank. */
    fun cleanUserName(input: String): String? =
        input.trim().take(MAX_USER_NAME).trim().takeIf { it.isNotEmpty() }
}

/**
 * Finds the student's name in the portal's studDetails response. The field isn't pinned down yet,
 * so it's searched for defensively. Only the name is read — the rest of that response is personal
 * details and is never stored or logged.
 */
object StudentNames {
    private val json = Json { isLenient = true }

    private val PREFERRED_KEYS = listOf(
        "stud_name", "student_name", "studname", "sname", "name",
        "studentname", "stu_name", "full_name", "fullname"
    )

    /** Keys containing "name" that belong to someone or something else. */
    private val EXCLUDED_WORDS = listOf(
        "father", "mother", "parent", "guardian", "hostel", "college", "dept", "department", "course",
        "room", "block", "mess", "user", "file", "image", "photo", "bank", "staff", "warden", "manager"
    )

    /** The student's full name with spaces tidied, or null when it can't be found. Never throws. */
    fun findFullName(body: String): String? {
        val objects = objectsToSearch(body)
        return objects.firstNotNullOfOrNull(::preferredName)
            ?: objects.firstNotNullOfOrNull(::anyStudentName)
    }

    /** Key names only, never values — for the debug log, so the right field can be pinned later. */
    fun keyNames(body: String): List<String> = objectsToSearch(body).flatMap { it.keys }

    /** The root (or an array's first element), plus — one level only — its single nested object/array. */
    private fun objectsToSearch(body: String): List<JsonObject> {
        val root = try {
            json.parseToJsonElement(body)
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        val outer = root.firstObject() ?: return emptyList()
        val nested = outer.values.filter { it is JsonObject || it is JsonArray }
        val inner = nested.singleOrNull()?.firstObject()
        return listOfNotNull(outer, inner)
    }

    private fun JsonElement.firstObject(): JsonObject? = when (this) {
        is JsonObject -> this
        is JsonArray -> firstOrNull() as? JsonObject
        else -> null
    }

    private fun preferredName(o: JsonObject): String? =
        PREFERRED_KEYS.firstNotNullOfOrNull { wanted ->
            o.entries.firstOrNull { it.key.equals(wanted, ignoreCase = true) }?.value?.nameValue()
        }

    private fun anyStudentName(o: JsonObject): String? =
        o.entries.firstNotNullOfOrNull { (key, value) ->
            val k = key.lowercase()
            if ("name" in k && EXCLUDED_WORDS.none { it in k }) value.nameValue() else null
        }

    private fun JsonElement.nameValue(): String? =
        (this as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf { it.isNotEmpty() }
}

/**
 * The greeting's name, in plain settings (none of it is secret). The user's own name always wins
 * over the portal's; the portal fetch never touches it.
 */
class GreetingStore(private val settings: Settings) {

    private val _displayName = MutableStateFlow(computeDisplayName())

    /** The name to greet with, or null for a greeting without one. */
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    /** The portal's full name, e.g. "SOMA PRASANTH S", for the hint in Settings. */
    val portalFullName: String? get() = settings.getStringOrNull(KEY_PORTAL_FULL)

    /** The name the user set in Settings, or null when the portal's is used. */
    val userName: String? get() = settings.getStringOrNull(KEY_USER)

    /** At most one studDetails call a day. */
    fun isFetchDue(today: LocalDate): Boolean = settings.getStringOrNull(KEY_LAST_FETCH) != today.toString()

    /** A studDetails response arrived. A name that couldn't be found keeps the one already saved. */
    fun onPortalFetched(fullName: String?, today: LocalDate) {
        if (fullName != null) {
            settings.putString(KEY_PORTAL_FULL, fullName)
            Greeting.shortName(fullName)?.let { settings.putString(KEY_PORTAL_SHORT, it) }
                ?: settings.remove(KEY_PORTAL_SHORT)
        }
        settings.putString(KEY_LAST_FETCH, today.toString())
        refresh()
    }

    /** Settings → Save. A blank name clears the override, as "Use portal name" does. */
    fun setUserName(input: String) {
        Greeting.cleanUserName(input)?.let { settings.putString(KEY_USER, it) } ?: settings.remove(KEY_USER)
        refresh()
    }

    fun usePortalName() {
        settings.remove(KEY_USER)
        refresh()
    }

    /** Signed out, or a different student signed in. */
    fun clear() {
        listOf(KEY_PORTAL_FULL, KEY_PORTAL_SHORT, KEY_USER, KEY_LAST_FETCH).forEach(settings::remove)
        refresh()
    }

    private fun computeDisplayName(): String? =
        settings.getStringOrNull(KEY_USER) ?: settings.getStringOrNull(KEY_PORTAL_SHORT)

    private fun refresh() {
        _displayName.value = computeDisplayName()
    }

    private companion object {
        const val KEY_PORTAL_FULL = "greeting_portal_full_name"
        const val KEY_PORTAL_SHORT = "greeting_portal_short_name"
        const val KEY_USER = "greeting_user_name"
        /** yyyy-MM-dd of the last studDetails response. */
        const val KEY_LAST_FETCH = "greeting_last_fetch_date"
    }
}
