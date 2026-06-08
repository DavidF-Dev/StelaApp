package dev.davidfdev.stela.ui

import android.text.format.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object TimeFormatter {

    /// Absolute, locale-formatted date and time (e.g. "8 Jun 2026, 14:32"). Pure, so
    /// it is unit-testable with a fixed instant, locale, and zone.
    fun absolute(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val formatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
        return Instant.ofEpochMilli(epochMillis).atZone(zone).format(formatter)
    }

    /// Relative, localized span (e.g. "2 hours ago", "Yesterday"). A thin platform
    /// call so localisation is handled for every language without extra strings.
    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): CharSequence =
        DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS)
}
