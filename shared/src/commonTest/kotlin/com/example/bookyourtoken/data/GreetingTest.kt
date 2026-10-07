package com.example.bookyourtoken.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GreetingTest {

    @Test
    fun `salutation follows the hostel clock`() {
        assertEquals("Hello", Greeting.salutation(4))
        assertEquals("Good morning", Greeting.salutation(5))
        assertEquals("Good morning", Greeting.salutation(11))
        assertEquals("Good afternoon", Greeting.salutation(12))
        assertEquals("Good afternoon", Greeting.salutation(16))
        assertEquals("Good evening", Greeting.salutation(17))
        assertEquals("Good evening", Greeting.salutation(21))
        assertEquals("Hello", Greeting.salutation(22))
        assertEquals("Hello", Greeting.salutation(0))
    }

    @Test
    fun `greeting with and without a name`() {
        assertEquals("Good evening, Soma 👋", Greeting.text(18, "Soma"))
        assertEquals("Good evening 👋", Greeting.text(18, null))
        assertEquals("Hello 👋", Greeting.text(23, " "))
    }

    @Test
    fun `short name drops initials and fixes the case`() {
        assertEquals("Soma", Greeting.shortName("SOMA PRASANTH S"))
        assertEquals("Soma", Greeting.shortName("S. SOMA PRASANTH"))
        assertEquals("Soma", Greeting.shortName("S.SOMA PRASANTH"))
        assertEquals("Soma", Greeting.shortName("  soma   prasanth "))
        assertNull(Greeting.shortName("S. K."))
        assertNull(Greeting.shortName(""))
    }

    @Test
    fun `user name is trimmed, capped and blank means none`() {
        assertEquals("Soma", Greeting.cleanUserName("  Soma "))
        assertEquals(30, Greeting.cleanUserName("x".repeat(40))?.length)
        assertNull(Greeting.cleanUserName("   "))
    }

    @Test
    fun `preferred keys match case-insensitively`() {
        assertEquals("SOMA PRASANTH S", StudentNames.findFullName("""{"ROLLNO":"23I362","STUD_NAME":"SOMA PRASANTH S"}"""))
        assertEquals("SOMA PRASANTH S", StudentNames.findFullName("""[{"Name":" SOMA  PRASANTH S "}]"""))
    }

    @Test
    fun `preferred keys win over other name keys`() {
        val body = """{"father_name":"RAJ K","hostel_name":"A BLOCK","sname":"SOMA PRASANTH S"}"""
        assertEquals("SOMA PRASANTH S", StudentNames.findFullName(body))
    }

    @Test
    fun `falls back to a name-like key that isn't someone else's`() {
        val body = """{"FATHERNAME":"RAJ K","ROOMNAME":"A12","UserName":"23I362","STUDENT_FULLNAME":"SOMA PRASANTH S"}"""
        assertEquals("SOMA PRASANTH S", StudentNames.findFullName(body))
    }

    @Test
    fun `looks one level into a single wrapper`() {
        assertEquals("SOMA PRASANTH S", StudentNames.findFullName("""{"status":1,"data":[{"stud_name":"SOMA PRASANTH S"}]}"""))
        assertEquals("SOMA PRASANTH S", StudentNames.findFullName("""{"result":{"studname":"SOMA PRASANTH S"}}"""))
    }

    @Test
    fun `blank values, non-strings and unknown shapes give no name`() {
        assertNull(StudentNames.findFullName("""{"stud_name":"  ","name":null,"sname":42}"""))
        assertNull(StudentNames.findFullName("""{"father_name":"RAJ K","mother_name":"LATHA"}"""))
        assertNull(StudentNames.findFullName("[]"))
        assertNull(StudentNames.findFullName("<html>login</html>"))
        assertNull(StudentNames.findFullName("""{"a":{"name":"X"},"b":{"name":"Y"}}"""))
    }

    @Test
    fun `key names never include values`() {
        assertEquals(listOf("rollno", "stud_name"), StudentNames.keyNames("""{"rollno":"23I362","stud_name":"SOMA"}"""))
        assertEquals(emptyList(), StudentNames.keyNames("not json"))
    }
}
