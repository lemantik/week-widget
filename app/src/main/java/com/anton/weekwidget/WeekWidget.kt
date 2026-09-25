package com.anton.weekwidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

// ─── Настройки внешнего вида (крутите здесь) ─────────────────────────────

/** Относительная ширина колонок: сегодня самая широкая, дальние дни уже. */
private val COLUMN_WEIGHTS = floatArrayOf(2.2f, 1.6f, 1.25f, 1.0f, 0.9f, 0.85f, 0.85f)

private enum class TimeMode { TWO_LINES, INLINE, NONE }

private data class Tier(
    val header: TextUnit,   // размер заголовка дня
    val text: TextUnit,     // размер текста событий
    val time: TimeMode,     // как показывать время
    val maxLines: Int,      // строк на событие
    val chipHeight: Dp,     // примерная высота события — для расчёта «+N»
)

private fun tierFor(index: Int) = when (index) {
    0 -> Tier(14.sp, 13.sp, TimeMode.TWO_LINES, 3, 44.dp)
    1 -> Tier(13.sp, 12.sp, TimeMode.INLINE, 2, 26.dp)
    2, 3 -> Tier(12.sp, 11.sp, TimeMode.NONE, 1, 21.dp)
    else -> Tier(11.sp, 10.sp, TimeMode.NONE, 1, 20.dp)
}

/** В Glance в Column не больше 10 дочерних элементов: заголовок + события + «+N». */
private const val MAX_EVENTS = 7

// ─────────────────────────────────────────────────────────────────────────

class WeekWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val json = prefs[WidgetUpdater.DATA_KEY]
            GlanceTheme {
                val today = LocalDate.now()
                val days = json?.let { WeekCodec.decode(it) }
                    ?.filter { !it.date.isBefore(today) } // страховка, если полночное обновление опоздало
                if (days.isNullOrEmpty()) {
                    Placeholder(noPermission = prefs[WidgetUpdater.NO_PERMISSION_KEY] == true)
                } else {
                    WeekContent(days)
                }
            }
        }
    }
}

@Composable
private fun Placeholder(noPermission: Boolean) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (noPermission) "Нажмите, чтобы дать доступ к календарю" else "Загрузка…",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        )
    }
}

@Composable
private fun WeekContent(days: List<DayEvents>) {
    val size = LocalSize.current
    val outer = 4.dp
    val innerWidth = size.width - outer * 2
    val innerHeight = size.height - outer * 2
    val weights = COLUMN_WEIGHTS.take(days.size)
    val total = weights.sum()

    Row(
        GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(outer)
    ) {
        days.forEachIndexed { index, day ->
            DayColumn(day, index, innerWidth * (weights[index] / total), innerHeight)
        }
    }
}

@Composable
private fun DayColumn(day: DayEvents, index: Int, width: Dp, height: Dp) {
    val tier = tierFor(index)
    val isToday = day.date == LocalDate.now()
    val isWeekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY

    val headerColor = when {
        isToday -> GlanceTheme.colors.onPrimaryContainer
        isWeekend -> GlanceTheme.colors.error
        else -> GlanceTheme.colors.onSurface
    }
    val textColor = if (isToday) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface

    // Сколько событий влезет по высоте; остальное свернём в «+N».
    val headerHeight = (tier.header.value * 1.6f).dp
    val fit = ((height - 8.dp - headerHeight) / tier.chipHeight).toInt().coerceIn(1, MAX_EVENTS)
    val shown = if (day.events.size > fit) day.events.take(fit - 1) else day.events
    val hidden = day.events.size - shown.size

    var columnModifier = GlanceModifier.fillMaxSize()
    if (isToday) columnModifier = columnModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(10.dp)
    columnModifier = columnModifier.padding(4.dp).clickable(actionStartActivity(openDayIntent(day.date)))

    Box(GlanceModifier.width(width).fillMaxHeight().padding(horizontal = 1.dp)) {
        Column(columnModifier) {
            Text(
                text = dayLabel(day.date),
                maxLines = 1,
                style = TextStyle(
                    fontSize = tier.header,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = headerColor,
                ),
            )
            // Пустой день — просто пустая колонка с заголовком.
            shown.forEach { EventChip(it, tier, textColor) }
            if (hidden > 0) {
                Text(
                    text = "+$hidden",
                    modifier = GlanceModifier.padding(top = 2.dp),
                    style = TextStyle(fontSize = tier.text, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun EventChip(ev: CalEvent, tier: Tier, textColor: ColorProvider) {
    Box(GlanceModifier.fillMaxWidth().padding(top = 3.dp)) {
        Box(
            GlanceModifier.fillMaxWidth()
                .background(Color(ev.color).copy(alpha = 0.28f))
                .cornerRadius(4.dp)
                .padding(horizontal = 3.dp, vertical = 2.dp)
                .clickable(actionStartActivity(openEventIntent(ev)))
        ) {
            Text(
                text = eventLabel(ev, tier.time),
                maxLines = tier.maxLines,
                style = TextStyle(fontSize = tier.text, color = textColor),
            )
        }
    }
}

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

private fun eventLabel(ev: CalEvent, mode: TimeMode): String {
    if (ev.allDay || mode == TimeMode.NONE) return ev.title
    val time = Instant.ofEpochMilli(ev.begin).atZone(ZoneId.systemDefault()).format(TIME_FMT)
    return if (mode == TimeMode.TWO_LINES) "$time\n${ev.title}" else "$time ${ev.title}"
}

private fun dayLabel(date: LocalDate): String {
    val locale = Locale.getDefault()
    val dow = date.dayOfWeek.getDisplayName(JTextStyle.SHORT, locale)
        .replaceFirstChar { it.titlecase(locale) }
    return "$dow ${date.dayOfMonth}"
}
