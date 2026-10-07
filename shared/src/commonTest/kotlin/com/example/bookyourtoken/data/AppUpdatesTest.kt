package com.example.bookyourtoken.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdatesTest {

    private fun release(
        tag: String = "v7",
        name: String? = "1.3.0",
        body: String? = "Fixes the Leave tab.",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[
            {"name": "checksums.txt", "browser_download_url": "https://example.com/checksums.txt", "size": 80},
            {"name": "StayEasy-1.3.0.apk", "browser_download_url": "https://example.com/app.apk", "size": 9000000}
        ]"""
    ) = """{
        "tag_name": "$tag",
        "name": ${name?.let { "\"$it\"" } ?: "null"},
        "body": ${body?.let { "\"$it\"" } ?: "null"},
        "draft": $draft,
        "prerelease": $prerelease,
        "assets": $assets
    }"""

    @Test
    fun `parses tag, name, notes and the apk asset`() {
        val parsed = assertNotNull(GitHubReleases.parseLatest(release()))
        assertEquals(7, parsed.versionCode)
        assertEquals("1.3.0", parsed.versionName)
        assertEquals("Fixes the Leave tab.", parsed.notes)
        assertFalse(parsed.required)
        assertEquals("https://example.com/app.apk", parsed.apkUrl)
        assertEquals(9_000_000L, parsed.apkSize)
    }

    @Test
    fun `required marker anywhere makes it mandatory and is stripped from the notes`() {
        val parsed = assertNotNull(GitHubReleases.parseLatest(release(body = "Portal changed.\\n\\n[required]")))
        assertTrue(parsed.required)
        assertEquals("Portal changed.", parsed.notes)
    }

    @Test
    fun `tags that aren't v-number are ignored`() {
        assertNull(GitHubReleases.parseLatest(release(tag = "1.3.0")))
        assertNull(GitHubReleases.parseLatest(release(tag = "v1.3.0")))
        assertNull(GitHubReleases.parseLatest(release(tag = "release-7")))
    }

    @Test
    fun `drafts and prereleases are ignored`() {
        assertNull(GitHubReleases.parseLatest(release(draft = true)))
        assertNull(GitHubReleases.parseLatest(release(prerelease = true)))
    }

    @Test
    fun `no apk asset means no update`() {
        assertNull(GitHubReleases.parseLatest(release(assets = "[]")))
        assertNull(
            GitHubReleases.parseLatest(
                release(assets = """[{"name": "notes.txt", "browser_download_url": "https://example.com/n", "size": 1}]""")
            )
        )
    }

    @Test
    fun `missing name falls back to the tag and missing body to empty notes`() {
        val parsed = assertNotNull(GitHubReleases.parseLatest(release(name = null, body = null)))
        assertEquals("v7", parsed.versionName)
        assertEquals("", parsed.notes)
        assertFalse(parsed.required)
    }

    @Test
    fun `non-json body throws`() {
        assertFailsWith<IllegalArgumentException> { GitHubReleases.parseLatest("<html>rate limited</html>") }
    }

    @Test
    fun `automatic checks are due after six hours`() {
        val now = 100 * UpdatePolicy.CHECK_INTERVAL_MILLIS
        assertTrue(UpdatePolicy.isCheckDue(lastCheckAt = 0L, now = now))
        assertFalse(UpdatePolicy.isCheckDue(lastCheckAt = now - UpdatePolicy.CHECK_INTERVAL_MILLIS + 1, now = now))
        assertTrue(UpdatePolicy.isCheckDue(lastCheckAt = now - UpdatePolicy.CHECK_INTERVAL_MILLIS, now = now))
        // Clock moved backwards.
        assertTrue(UpdatePolicy.isCheckDue(lastCheckAt = now + 1000, now = now))
    }

    @Test
    fun `later holds back only that version, and never a required one`() {
        val v7 = assertNotNull(GitHubReleases.parseLatest(release()))
        val v8 = v7.copy(versionCode = 8)
        val until = 1_000_000L
        assertTrue(UpdatePolicy.isSnoozed(v7, snoozedVersionCode = 7, snoozedUntil = until, now = until - 1))
        assertFalse(UpdatePolicy.isSnoozed(v7, snoozedVersionCode = 7, snoozedUntil = until, now = until))
        assertFalse(UpdatePolicy.isSnoozed(v8, snoozedVersionCode = 7, snoozedUntil = until, now = 0))
        assertFalse(UpdatePolicy.isSnoozed(v7.copy(required = true), snoozedVersionCode = 7, snoozedUntil = until, now = 0))
    }
}
