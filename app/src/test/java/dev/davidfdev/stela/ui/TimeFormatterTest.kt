package dev.davidfdev.stela.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class TimeFormatterTest {

    // 2026-06-08T14:32:00Z
    private val epochMillis =
        ZonedDateTime.of(2026, 6, 8, 14, 32, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

    @Test
    fun absolute_includesDateAndTimeForLocale() {
        val formatted = TimeFormatter.absolute(epochMillis, Locale.US, ZoneId.of("UTC"))

        // Robust against CLDR pattern drift across JDKs.
        assertTrue(formatted, formatted.contains("2026"))
        assertTrue(formatted, formatted.contains("Jun"))
        assertTrue(formatted, formatted.contains("32"))
    }

    @Test
    fun absolute_isDeterministic() {
        assertEquals(
            TimeFormatter.absolute(epochMillis, Locale.US, ZoneId.of("UTC")),
            TimeFormatter.absolute(epochMillis, Locale.US, ZoneId.of("UTC")),
        )
    }

    @Test
    fun absolute_respectsZone() {
        val utc = TimeFormatter.absolute(epochMillis, Locale.US, ZoneId.of("UTC"))
        val tokyo = TimeFormatter.absolute(epochMillis, Locale.US, ZoneId.of("Asia/Tokyo"))
        // 14:32 UTC is 23:32 in Tokyo, so the rendered strings must differ.
        assertTrue(utc != tokyo)
    }
}
