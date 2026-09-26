package com.anton.weekwidget

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Читает события из системной базы календаря Android.
 * Google Calendar синхронизируется туда сам — никаких Google API не нужно.
 */
object CalendarRepository {

    private val PROJECTION = arrayOf(
        Instances.EVENT_ID,      // 0
        Instances.TITLE,         // 1
        Instances.BEGIN,         // 2
        Instances.END,           // 3
        Instances.ALL_DAY,       // 4
        Instances.DISPLAY_COLOR, // 5
    )

    // Только видимые календари, без отклонённых и отменённых событий.
    private val SELECTION =
        "${Instances.VISIBLE} = 1" +
        " AND IFNULL(${Instances.SELF_ATTENDEE_STATUS}, 0) != ${Attendees.ATTENDEE_STATUS_DECLINED}" +
        " AND IFNULL(${Instances.STATUS}, 0) != ${Events.STATUS_CANCELED}"

    private const val FALLBACK_COLOR = 0xFF378ADD.toInt()

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Возвращает [dayCount] дней, начиная с сегодня, или null, если нет доступа к календарю. */
    fun loadDays(context: Context, dayCount: Int = 7): List<DayEvents>? {
        if (!hasPermission(context)) return null

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val lastDay = today.plusDays(dayCount - 1L)

        // Берём запас в сутки с каждой стороны: события «на весь день» хранятся в UTC.
        val queryStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val queryEnd = lastDay.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, queryStart); ContentUris.appendId(it, queryEnd) }
            .build()

        val byDay = (0 until dayCount).associate { today.plusDays(it.toLong()) to mutableListOf<CalEvent>() }

        context.contentResolver.query(uri, PROJECTION, SELECTION, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val begin = c.getLong(2)
                val end = c.getLong(3)
                val allDay = c.getInt(4) == 1
                val color = if (c.isNull(5)) FALLBACK_COLOR else c.getInt(5)
                val ev = CalEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(без названия)",
                    begin = begin, end = end, allDay = allDay, color = color,
                )

                val (first, last) = if (allDay) {
                    val s = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                    val e = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
                    s to maxOf(s, e)
                } else {
                    val s = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                    val e = Instant.ofEpochMilli(maxOf(begin, end - 1)).atZone(zone).toLocalDate()
                    s to e
                }

                // Многодневные события попадают в каждый свой день внутри окна.
                var d = maxOf(first, today)
                val stop = minOf(last, lastDay)
                while (!d.isAfter(stop)) {
                    byDay[d]?.add(ev)
                    d = d.plusDays(1)
                }
            }
        }

        return byDay.map { (date, list) ->
            DayEvents(date, list.sortedWith(compareByDescending<CalEvent> { it.allDay }.thenBy { it.begin }))
        }
    }
}
