package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.FalakPreferencesRepository
import com.example.domain.falak.FalakCatalog
import com.example.domain.falak.FalakEphemerisEngine
import com.example.domain.falak.HijriCalendarEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class HijriCalendarAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_HIJRI_CALENDAR"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HijriCalendarAppWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            for (id in ids) {
                updateSingleWidget(context, manager, id)
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val nowMillis = System.currentTimeMillis()
            val markaz = runBlocking {
                withTimeoutOrNull(1500L) {
                    FalakPreferencesRepository(context.applicationContext)
                        .preferencesFlow
                        .first()
                        .markaz
                }
            } ?: FalakCatalog.markazStations.first()

            val istiwak = FalakEphemerisEngine.calculateIstiwakAndPrayerTimes(nowMillis, markaz)
            val (hYear, hMonth) = FalakEphemerisEngine.currentEstimatedHijriYearMonth(nowMillis)
            val calendar = HijriCalendarEngine.buildMonthCalendar(
                hijriYear = hYear,
                hijriMonth = hMonth,
                markaz = markaz,
                nowMillis = nowMillis
            )

            val todayCell = calendar.cells.firstOrNull { it.isToday }
            val gregorianAndMarkaz = buildString {
                if (todayCell != null) {
                    append("${todayCell.dayOfWeekLabel} ${todayCell.pasaranJawa}, ${todayCell.gregorianDateLabel}")
                } else {
                    append(istiwak.gregorianDateLabel)
                }
                append(" • ${markaz.name}")
            }

            val prayerStrip = calendar.upcomingPrayerEvents
                .take(3)
                .joinToString(" • ") { entry ->
                    "${entry.name.substringBefore(" ")} ${entry.timeLabel.take(5)}"
                } + " ${markaz.timezoneLabel}"

            val keyEventsText = calendar.upcomingKeyEvents
                .take(3)
                .joinToString("\n") { ev ->
                    val delta = HijriCalendarEngine.formatDaysDelta(ev.daysFromToday)
                    "• ${ev.hijriDay} ${calendar.hijriMonthName}: ${ev.title} ($delta)"
                }
                .ifBlank { "• Tidak ada agenda tersisa bulan ini" }

            val views = RemoteViews(context.packageName, R.layout.widget_hijri_calendar).apply {
                setTextViewText(R.id.widget_hijri_date, calendar.todayHijri.formatFull())
                setTextViewText(R.id.widget_gregorian_date, gregorianAndMarkaz)
                setTextViewText(R.id.widget_next_prayer, istiwak.nextPrayerCountdown)
                setTextViewText(R.id.widget_prayer_strip, prayerStrip)
                setTextViewText(
                    R.id.widget_key_events_title,
                    "Tanggal Penting ${calendar.hijriMonthName} ${calendar.hijriYear} H:"
                )
                setTextViewText(R.id.widget_key_events_list, keyEventsText)

                val launchAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val launchPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    launchAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, launchPendingIntent)

                val refreshIntent = Intent(context, HijriCalendarAppWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
