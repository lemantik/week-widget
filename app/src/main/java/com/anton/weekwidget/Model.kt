package com.anton.weekwidget

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
)

data class DayEvents(val date: LocalDate, val events: List<CalEvent>)

/** Сериализация недели в JSON, чтобы хранить её в состоянии виджета. */
object WeekCodec {
    fun encode(days: List<DayEvents>): String = JSONArray().apply {
        days.forEach { day ->
            put(JSONObject()
                .put("date", day.date.toString())
                .put("events", JSONArray().apply {
                    day.events.forEach { e ->
                        put(JSONObject()
                            .put("id", e.id).put("title", e.title)
                            .put("begin", e.begin).put("end", e.end)
                            .put("allDay", e.allDay).put("color", e.color))
                    }
                }))
        }
    }.toString()

    fun decode(json: String): List<DayEvents> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val evs = o.getJSONArray("events")
            DayEvents(
                LocalDate.parse(o.getString("date")),
                (0 until evs.length()).map { j ->
                    val e = evs.getJSONObject(j)
                    CalEvent(e.getLong("id"), e.getString("title"), e.getLong("begin"),
                        e.getLong("end"), e.getBoolean("allDay"), e.getInt("color"))
                }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/** Открыть событие в календарном приложении (обычно Google Calendar). */
fun openEventIntent(ev: CalEvent): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ev.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ev.end)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Открыть календарь на нужном дне. */
fun openDayIntent(date: LocalDate): Intent {
    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        .also { ContentUris.appendId(it, millis) }.build()
    return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
