package com.example.bookyourtoken.data

import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.data.models.cancelMessage
import com.example.bookyourtoken.data.models.messageForResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class CancelTokenTest {

    // Shaped like the real StudentGetToken row from the spec.
    private val boiledEgg = BookedToken(
        messId = "1", tokenName = "BOILED EGG", tokenId = 94, issueDate = null, expireDate = "29-09-2026",
        tokenNo = null, tokenQty = 1, status = "N", viewStatus = "0", mealTime = "Lunch", count = null
    )

    @Test
    fun `cancel form uses the site's swapped field names exactly`() {
        val fields = HostelClient.cancelFormFields("23i362", boiledEgg)
        assertEquals(
            listOf(
                "rollno" to "23I362",
                "Tokenno" to "View",
                "ISSUE_DATE" to "BOILED EGG",
                "TOKEN_ID" to "29-09-2026",
                "MEALTIME" to "Lunch"
            ),
            fields
        )
    }

    @Test
    fun `token name is sent verbatim and the numeric id is never sent`() {
        val fields = HostelClient.cancelFormFields("X", boiledEgg.copy(tokenName = "Boiled Egg "))!!.toMap()
        assertEquals("Boiled Egg ", fields["ISSUE_DATE"])
        assertFalse(fields.values.contains("94"))
    }

    @Test
    fun `cancel form is refused when a required field is missing`() {
        assertNull(HostelClient.cancelFormFields("X", boiledEgg.copy(tokenName = null)))
        assertNull(HostelClient.cancelFormFields("X", boiledEgg.copy(mealTime = null)))
    }

    @Test
    fun `cancel success is oresult 0, the opposite of booking`() {
        assertEquals("Token cancelled", cancelMessage(0, bulk = false))
        assertEquals("Tokens cancelled", cancelMessage(0, bulk = true))
        assertEquals("Token already cancelled", cancelMessage(1, bulk = false))
        assertEquals("No data found", cancelMessage(1, bulk = true))
        assertEquals("Cancel time expired", cancelMessage(2, bulk = false))
        assertEquals("Error occurred", cancelMessage(null, bulk = true))
        assertEquals("Token Booked", messageForResult(1, null))
    }

    @Test
    fun `only unused rows with complete details can be cancelled`() {
        assertTrue(boiledEgg.canCancel)
        assertFalse(boiledEgg.copy(status = "Y").canCancel)
        assertTrue(boiledEgg.copy(status = "Y").isUsed)
        assertFalse(boiledEgg.copy(status = null).canCancel)
        assertFalse(boiledEgg.copy(expireDate = null).canCancel)
    }
}
