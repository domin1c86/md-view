package com.mdview.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mdview.R
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * "2 hours ago" for a card's timestamp.
 *
 * Hand-rolled rather than `DateUtils.getRelativeTimeSpanString`, which resolves its
 * strings against `Resources.getSystem()` — the *system* locale. With an in-app language
 * set, that would render the one English phrase on an otherwise Chinese screen.
 */
@Composable
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val elapsed = (now - epochMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)

    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        hours < 1 -> pluralStringResource(R.plurals.time_minutes_ago, minutes.toInt(), minutes.toInt())
        days < 1 -> pluralStringResource(R.plurals.time_hours_ago, hours.toInt(), hours.toInt())
        days == 1L -> stringResource(R.string.time_yesterday)
        days < 7 -> pluralStringResource(R.plurals.time_days_ago, days.toInt(), days.toInt())
        // Past a week the exact gap stops being useful and the date is easier to place.
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
    }
}
