package dev.davidfdev.stela.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBodyTest {

    @Test
    fun shortDescription_returnedUnchanged() {
        assertEquals("Buy milk", notificationBody("Buy milk"))
    }

    @Test
    fun blankDescription_returnedUnchanged() {
        assertEquals("", notificationBody(""))
    }

    @Test
    fun exactlyAtCap_returnedUnchanged() {
        val text = "a".repeat(140)
        assertEquals(text, notificationBody(text))
    }

    @Test
    fun longDescription_isCutAndEllipsised() {
        val text = "a".repeat(301)
        val result = notificationBody(text)
        val body = result.dropLast(1)
        assertTrue("ends with ellipsis", result.endsWith("…"))
        assertTrue("body is a strict prefix of the original", body.length < text.length && text.startsWith(body))
    }

    @Test
    fun longDescription_breaksOnWordBoundary() {
        // 70 five-char words ("word ") = 350 chars, with a space at the cap.
        val text = "word ".repeat(70)
        val result = notificationBody(text)
        assertTrue("does not cut mid-word", result.endsWith("word…"))
    }

    @Test
    fun longDescription_withoutNearbySpace_hardCuts() {
        val text = "a".repeat(350)
        val result = notificationBody(text)
        assertEquals("a".repeat(140) + "…", result)
    }
}
