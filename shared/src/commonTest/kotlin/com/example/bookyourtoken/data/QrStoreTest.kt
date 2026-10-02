package com.example.bookyourtoken.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryFiles : PrivateFiles {
    val files = mutableMapOf<String, ByteArray>()
    override fun read(name: String) = files[name]
    override fun write(name: String, bytes: ByteArray) {
        files[name] = bytes
    }
    override fun delete(name: String) {
        files.remove(name)
    }
}

class QrStoreTest {

    private val oct2 = LocalDate(2026, 10, 2)
    private val fetchedAt = LocalDateTime(2026, 10, 2, 7, 42).toInstant(DateUtils.ZONE)
    private val png = byteArrayOf(1, 2, 3)
    private val rows = listOf(QrTokenRow("BOILED EGG", "02-10-2026", "Lunch", "2"))

    private fun snapshot(covers: List<String>) = QrSnapshot(png, rows, fetchedAt, covers)

    @Test
    fun `saved copy round-trips`() {
        val store = QrStore(MemoryFiles())
        store.save(snapshot(listOf("02-10-2026")))
        val loaded = assertNotNull(store.load())
        assertContentEquals(png, loaded.png)
        assertEquals(rows, loaded.rows)
        assertEquals(fetchedAt, loaded.fetchedAt)
        assertEquals(listOf("02-10-2026"), loaded.coversDates)
    }

    @Test
    fun `kept while any covered date is today or later, deleted after`() {
        val files = MemoryFiles()
        val store = QrStore(files)
        store.save(snapshot(listOf("01-10-2026", "02-10-2026")))

        assertNotNull(store.loadValid(today = oct2))
        assertNull(store.loadValid(today = LocalDate(2026, 10, 3)))
        assertTrue(files.files.isEmpty(), "stale copy must be deleted, not just hidden")
    }

    @Test
    fun `without readable dates it expires the day after it was fetched`() {
        assertFalse(QrStore.isStale(snapshot(listOf("soon")), oct2))
        assertTrue(QrStore.isStale(snapshot(emptyList()), LocalDate(2026, 10, 3)))
    }

    @Test
    fun `clear removes everything and a half-written copy is ignored`() {
        val files = MemoryFiles()
        val store = QrStore(files)
        store.save(snapshot(listOf("02-10-2026")))
        store.clear()
        assertTrue(files.files.isEmpty())

        files.write("qr.png", png)
        assertNull(store.load())
        files.write("qr.json", "{not json".encodeToByteArray())
        assertNull(store.load())
    }

    @Test
    fun `loose dates accept the formats the QR table might use`() {
        assertEquals(oct2, DateUtils.parseLooseDate("02-10-2026"))
        assertEquals(oct2, DateUtils.parseLooseDate("2/10/2026 12:00:00 AM"))
        assertNull(DateUtils.parseLooseDate("31-02-2026"))
        assertNull(DateUtils.parseLooseDate(""))
    }
}
