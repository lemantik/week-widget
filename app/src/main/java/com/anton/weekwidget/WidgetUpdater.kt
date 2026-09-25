package com.anton.weekwidget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/** Загружает неделю из календаря, кладёт её в состояние каждого виджета и перерисовывает. */
object WidgetUpdater {
    val DATA_KEY = stringPreferencesKey("week_json")
    val NO_PERMISSION_KEY = booleanPreferencesKey("no_permission")

    suspend fun refresh(context: Context) {
        val json = CalendarRepository.loadDays(context)?.let(WeekCodec::encode)
        val ids = GlanceAppWidgetManager(context).getGlanceIds(WeekWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                if (json == null) {
                    prefs.remove(DATA_KEY)
                    prefs[NO_PERMISSION_KEY] = true
                } else {
                    prefs[DATA_KEY] = json
                    prefs[NO_PERMISSION_KEY] = false
                }
            }
            WeekWidget().update(context, id)
        }
    }
}
