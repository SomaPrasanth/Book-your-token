package com.example.bookyourtoken.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalResponseTest {

    @Test
    fun `booked tokens are read from StudentGetToken rows, tolerating numbers and nulls`() {
        val body = """
            [{"MESS_ID":"1","TOKEN_NAME":"BOILED EGG","TOKEN_ID":94,"IssueDate":null,"ExpireDate":"29-09-2026",
              "TOKEN_NO":null,"TOKEN_QTY":"2","Status":"N","ViewStatus":"0","MEALTIME":"Lunch","COUNT":null},
             "not an object"]
        """.trimIndent()
        val tokens = HostelClient.parseBookedTokens(body)
        assertEquals(1, tokens.size)
        val t = tokens.single()
        assertEquals("BOILED EGG", t.tokenName)
        assertEquals(94, t.tokenId)
        assertEquals(2, t.tokenQty)
        assertEquals("29-09-2026", t.expireDate)
        assertEquals("Lunch", t.mealTime)
        assertNull(t.issueDate)
        assertNull(t.count)
        assertTrue(t.canCancel)
    }

    @Test
    fun `a booked tokens body that isn't an array is rejected`() {
        assertFailsWith<IllegalArgumentException> { HostelClient.parseBookedTokens("""{"error":1}""") }
    }

    @Test
    fun `result code is read from an object or a one-element array`() {
        assertEquals(1, HostelClient.resultCode("""{"oresult":1,"Count":0}"""))
        assertEquals(0, HostelClient.resultCode("""[{"oresult":"0"}]"""))
        assertNull(HostelClient.resultCode("""[]"""))
        assertNull(HostelClient.resultCode("OK"))
        // A garbled code must never read as 0, which means "cancelled" for cancel requests.
        assertNull(HostelClient.resultCode("""{"oresult":"x"}"""))
    }

    @Test
    fun `portal dates round-trip as dd-MM-yyyy`() {
        val date = DateUtils.parsePortalDate("05-10-2026")!!
        assertEquals("05-10-2026", DateUtils.formatPortalDate(date))
        assertEquals("Monday, 5 Oct", DateUtils.friendlyLabel(date))
        assertNull(DateUtils.parsePortalDate("2026-10-05"))
        assertNull(DateUtils.parsePortalDate("31-02-2026"))
        assertNull(DateUtils.parsePortalDate(null))
    }
}
