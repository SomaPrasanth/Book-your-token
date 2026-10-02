package com.example.bookyourtoken.data

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import kotlin.io.encoding.Base64

/** One row of the table under the QR: what the QR will redeem. Text exactly as the portal shows it. */
data class QrTokenRow(
    val tokenName: String,
    val date: String,
    val mealTime: String,
    val quantity: String
)

/**
 * The QR page, reduced to the two things the app shows. [png] is the server's image exactly as sent
 * — never decoded as a QR, regenerated or altered. Deliberately not a data class, so it can't end up
 * printed anywhere by accident.
 */
class QrPage(val png: ByteArray, val rows: List<QrTokenRow>)

/**
 * Reads GET /Hostel/QRCode/QRcodeGenerate. The page also carries three hidden inputs; they are never
 * read, stored or sent.
 */
object QrPageParser {

    /** Null when the page has no usable QR image — the caller shows "not generated yet". */
    fun parse(html: String): QrPage? {
        val doc = Ksoup.parse(html)
        val png = extractImage(doc) ?: return null
        return QrPage(png, parseRows(doc))
    }

    /**
     * `alt="QR Code"` first; otherwise the first *data:* image. Never just the first <img> — the
     * decorative /Hostel/img/veg.jpg comes before the QR.
     */
    private fun extractImage(doc: Document): ByteArray? {
        val img = doc.select("img").firstOrNull { it.attr("alt").trim().equals("QR Code", ignoreCase = true) }
            ?: doc.select("img").firstOrNull { it.attr("src").trim().startsWith("data:image/") }
            ?: return null
        val src = img.attr("src").trim()
        if (!src.startsWith("data:image/")) return null
        val comma = src.indexOf(',')
        if (comma < 0) return null
        val base64 = src.substring(comma + 1).filterNot { it.isWhitespace() }
        if (base64.isEmpty()) return null
        return runCatching { Base64.Default.decode(base64) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** The table whose header row has a "Token Name" column. Rows with missing cells are skipped. */
    internal fun parseRows(doc: Document): List<QrTokenRow> {
        for (table in doc.select("table")) {
            val rows = table.select("tr")
            val headerIndex = rows.indexOfFirst { row -> row.cellTexts().any { it.equals("Token Name", ignoreCase = true) } }
            if (headerIndex < 0) continue

            val header = rows[headerIndex].cellTexts()
            fun column(name: String) = header.indexOfFirst { it.equals(name, ignoreCase = true) }
            val name = column("Token Name")
            val date = column("Issue Date")
            val meal = column("Meal Time")
            val qty = column("Token Qty")
            val needed = maxOf(name, date, meal, qty) + 1

            return rows.drop(headerIndex + 1).mapNotNull { row ->
                val cells = row.select("td").map { it.text().trim() }
                if (cells.size < needed || cells[name].isEmpty()) return@mapNotNull null
                QrTokenRow(
                    tokenName = cells[name],
                    date = cells.getOrNull(date).orEmpty(),
                    mealTime = cells.getOrNull(meal).orEmpty(),
                    quantity = cells.getOrNull(qty).orEmpty()
                )
            }
        }
        return emptyList()
    }

    private fun Element.cellTexts(): List<String> = select("th, td").map { it.text().trim() }
}
