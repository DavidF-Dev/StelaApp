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

    private const val MINUTE_MILLIS = 60_000L

    /// The instant from which to format an *upcoming* event, floored to one minute ahead of [now]. Pure,
    /// so it is unit-testable without the platform formatter.
    fun upcomingInstant(epochMillis: Long, now: Long): Long = maxOf(epochMillis, now + MINUTE_MILLIS)

    /// Relative span for an upcoming event, never reading less than "in 1 minute": a sub-minute time —
    /// or a slightly overdue one, since inexact alarms can fire late — still reads as imminent rather than
    /// "in 0 minutes" or in the past. Lower-cased to sit inside a sentence; see [lowercaseFirst].
    fun relativeUpcoming(epochMillis: Long, now: Long = System.currentTimeMillis()): CharSequence =
        relative(upcomingInstant(epochMillis, now), now).lowercaseFirst()

    /// Drops the leading capital the platform adds so a relative span can stand alone ("In 2 hours"),
    /// which reads as a mistake once the span is embedded mid-sentence ("Pins In 2 hours").
    internal fun CharSequence.lowercaseFirst(): CharSequence =
        if (isEmpty() || !first().isUpperCase()) this else first().lowercaseChar() + substring(1)
}
