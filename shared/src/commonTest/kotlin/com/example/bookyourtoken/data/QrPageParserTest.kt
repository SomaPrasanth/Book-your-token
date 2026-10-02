package com.example.bookyourtoken.data

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrPageParserTest {

    // The 8-byte PNG signature, base64-encoded — enough to tell which <img> was picked.
    private val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private val pngBase64 = "iVBORw0KGgo="

    private val table = """
        <table class="table">
          <thead><tr><th>S.No</th><th>Hostel Name</th><th>Token Name</th><th>Issue Date</th><th>Meal Time</th><th>Token Qty</th></tr></thead>
          <tbody>
            <tr><td>1</td><td>Hostel A</td><td>BOILED EGG</td><td>02-10-2026</td><td>Lunch</td><td>2</td></tr>
            <tr><td>2</td><td>Hostel A</td><td>GOBI CHILLI</td></tr>
            <tr><td>3</td><td>Hostel A</td><td>OMELETTE</td><td>02-10-2026</td><td>Dinner</td><td>1</td></tr>
          </tbody>
        </table>
    """.trimIndent()

    private fun page(images: String, body: String = table) = """
        <html><head><title>Example of QR Code Generator</title></head><body>
          $images
          <input type="hidden" id="a" value="02-10-2026 08:00" />
          <input type="hidden" id="b" value="9f2c4e" />
          <input type="hidden" id="c" value="ab12cd" />
          $body
        </body></html>
    """.trimIndent()

    @Test
    fun `picks the img with alt QR Code, not the decorative image before it`() {
        val html = page("""<img src="/Hostel/img/veg.jpg"><img alt="QR Code" src="data:image/png;base64,$pngBase64">""")
        val result = assertNotNull(QrPageParser.parse(html))
        assertContentEquals(pngSignature, result.png)
    }

    @Test
    fun `falls back to the first data image, never the first img overall`() {
        val html = page("""<img src="/Hostel/img/veg.jpg"><img src="data:image/png;base64,$pngBase64">""")
        assertContentEquals(pngSignature, assertNotNull(QrPageParser.parse(html)).png)
    }

    @Test
    fun `tolerates line breaks inside the base64`() {
        val html = page("<img alt=\"QR Code\" src=\"data:image/png;base64,iVBO\nRw0K\nGgo=\">")
        assertContentEquals(pngSignature, assertNotNull(QrPageParser.parse(html)).png)
    }

    @Test
    fun `no QR image means not available`() {
        assertNull(QrPageParser.parse(page("""<img src="/Hostel/img/veg.jpg">""")))
        assertNull(QrPageParser.parse(page("""<img alt="QR Code" src="data:image/png;base64,">""")))
        assertNull(QrPageParser.parse(page("""<img alt="QR Code" src="data:image/png;base64,@@not base64@@">""")))
        assertNull(QrPageParser.parse("<html><body>Session expired</body></html>"))
    }

    @Test
    fun `reads the token table by header names and skips short rows`() {
        val rows = assertNotNull(QrPageParser.parse(page("""<img alt="QR Code" src="data:image/png;base64,$pngBase64">"""))).rows
        assertEquals(
            listOf(
                QrTokenRow("BOILED EGG", "02-10-2026", "Lunch", "2"),
                QrTokenRow("OMELETTE", "02-10-2026", "Dinner", "1")
            ),
            rows
        )
    }

    @Test
    fun `missing table gives no rows, and other tables are ignored`() {
        val other = "<table><tr><th>Name</th><th>Value</th></tr><tr><td>x</td><td>y</td></tr></table>"
        val result = QrPageParser.parse(page("""<img alt="QR Code" src="data:image/png;base64,$pngBase64">""", body = other))
        assertTrue(assertNotNull(result).rows.isEmpty())
    }

    @Test
    fun `header row made of td cells is still found`() {
        val html = """
            <table><tr><td>S.No</td><td>Token Name</td><td>Issue Date</td><td>Meal Time</td><td>Token Qty</td></tr>
            <tr><td>1</td><td>EGG DOSA</td><td>03-10-2026</td><td>Breakfast</td><td>1</td></tr></table>
        """.trimIndent()
        assertEquals(listOf(QrTokenRow("EGG DOSA", "03-10-2026", "Breakfast", "1")), QrPageParser.parseRows(Ksoup.parse(html)))
    }
}
