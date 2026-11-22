package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.falak.HijriCalendarDayCell
import com.example.domain.falak.HijriCalendarEngine
import com.example.domain.falak.HijriEventType
import com.example.domain.falak.HijriMonthCalendar
import com.example.domain.falak.MarkazLocation
import com.example.domain.falak.PrayerTimeEntry
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun HijriCalendarWidgetCard(
    calendar: HijriMonthCalendar,
    markaz: MarkazLocation,
    onShiftMonth: (Int) -> Unit,
    onResetCurrentMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultSelectedDay = remember(calendar.hijriYear, calendar.hijriMonth, calendar.todayHijri) {
        if (calendar.hijriYear == calendar.todayHijri.year &&
            calendar.hijriMonth == calendar.todayHijri.month
        ) {
            calendar.todayHijri.day
        } else {
            1
        }
    }
    var selectedHijriDay by remember(calendar.hijriYear, calendar.hijriMonth) {
        mutableIntStateOf(defaultSelectedDay)
    }

    val selectedCell = calendar.cells.firstOrNull { it.hijriDay == selectedHijriDay }
        ?: calendar.cells.first()

    val selectedDayPrayers = remember(
        selectedCell.gregorianYear,
        selectedCell.gregorianMonth,
        selectedCell.gregorianDay,
        markaz.id,
        markaz.latitude,
        markaz.longitude
    ) {
        HijriCalendarEngine.calculatePrayerEventsForDay(
            gregorianYear = selectedCell.gregorianYear,
            gregorianMonth = selectedCell.gregorianMonth,
            gregorianDay = selectedCell.gregorianDay,
            markaz = markaz
        ).filter { it.id != "imsak" && it.id != "dhuha" }
    }

    val isCurrentMonth = calendar.hijriYear == calendar.todayHijri.year &&
        calendar.hijriMonth == calendar.todayHijri.month

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("hijri_calendar_widget_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Kalender Hijriah & Agenda Sholat",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hari Ini: ${calendar.todayHijri.formatFull()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!isCurrentMonth) {
                    TextButton(
                        onClick = onResetCurrentMonth,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hijri_calendar_today_button")
                    ) {
                        Text("Bulan Ini")
                    }
                }
            }

            // Month Stepper
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onShiftMonth(-1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hijri_calendar_prev_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Bulan Hijriah sebelumnya"
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${calendar.hijriMonthName} ${calendar.hijriYear} H",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("hijri_calendar_month_title")
                        )
                        Text(
                            text = calendar.gregorianSpanLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onShiftMonth(1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hijri_calendar_next_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Bulan Hijriah berikutnya"
                        )
                    }
                }
            }

            // Legend Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot("Hari Besar", MaterialTheme.colorScheme.primary)
                LegendDot("Puasa Sunnah", MaterialTheme.colorScheme.secondary)
                LegendDot("Rukyat / Awal Bulan", MaterialTheme.colorScheme.tertiary)
                LegendDot("Senin / Kamis", MaterialTheme.colorScheme.outline)
            }

            // Weekday Headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Ahd", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab").forEachIndexed { idx, day ->
                    Text(
                        text = day,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (idx == 5) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 7-column Hijri Month Grid
            HijriMonthGrid(
                calendar = calendar,
                selectedHijriDay = selectedHijriDay,
                onSelectDay = { selectedHijriDay = it }
            )

            // Selected Day Details + Prayer Events for that day
            SelectedDayDetailPanel(
                cell = selectedCell,
                monthName = calendar.hijriMonthName,
                prayerTimes = selectedDayPrayers,
                timezoneLabel = markaz.timezoneLabel
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Upcoming Prayer Events Strip (Live Next Prayers)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Event Sholat Mendatang (${calendar.nextPrayerCountdown})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                calendar.upcomingPrayerEvents.forEach { entry ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (entry.isNext) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (entry.isNext) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (entry.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${entry.timeLabel} ${markaz.timezoneLabel}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Key Dates & Upcoming Islamic Events for Current Month
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Tanggal Penting Bulan ${calendar.hijriMonthName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                calendar.keyEvents.forEach { ev ->
                    val accent = eventTypeColor(ev.type)
                    val isSelectedEvent = ev.hijriDay == selectedHijriDay
                    Surface(
                        onClick = { selectedHijriDay = ev.hijriDay },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelectedEvent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (isSelectedEvent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("hijri_event_item_${ev.hijriDay}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${ev.hijriDay}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = accent
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ev.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ev.dayOfWeekLabel}, ${ev.gregorianDateLabel} • ${ev.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (ev.daysFromToday >= 0) {
                                    accent.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                }
                            ) {
                                Text(
                                    text = HijriCalendarEngine.formatDaysDelta(ev.daysFromToday),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ev.daysFromToday >= 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HijriMonthGrid(
    calendar: HijriMonthCalendar,
    selectedHijriDay: Int,
    onSelectDay: (Int) -> Unit
) {
    val totalSlots = calendar.startDayOfWeekOffset + calendar.daysInMonth
    val rows = (totalSlots + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (c in 0 until 7) {
                    val slotIndex = r * 7 + c
                    val dayIndex = slotIndex - calendar.startDayOfWeekOffset
                    if (dayIndex in calendar.cells.indices) {
                        val cell = calendar.cells[dayIndex]
                        HijriDayCellItem(
                            cell = cell,
                            isSelected = cell.hijriDay == selectedHijriDay,
                            onClick = { onSelectDay(cell.hijriDay) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HijriDayCellItem(
    cell: HijriCalendarDayCell,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eventDotColor = when (cell.keyEvent?.type) {
        HijriEventType.MAJOR_HOLIDAY -> MaterialTheme.colorScheme.primary
        HijriEventType.SUNNAH_FASTING -> MaterialTheme.colorScheme.secondary
        HijriEventType.RUKYAT_OBSERVATION -> MaterialTheme.colorScheme.tertiary
        null -> if (cell.isSunnahMonThu) MaterialTheme.colorScheme.outline else Color.Transparent
    }

    val bgColor = when {
        cell.isToday -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val borderStroke = when {
        isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        cell.isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        else -> null
    }

    val cdText = buildString {
        append("${cell.hijriDay} ${cell.dayOfWeekLabel}, ${cell.gregorianDateLabel}")
        if (cell.isToday) append(", Hari ini")
        if (cell.keyEvent != null) append(", ${cell.keyEvent.title}")
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(
                if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = cdText }
            .testTag("hijri_day_cell_${cell.hijriDay}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${cell.hijriDay}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = if (cell.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (cell.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${cell.gregorianDay}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (eventDotColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(eventDotColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayDetailPanel(
    cell: HijriCalendarDayCell,
    monthName: String,
    prayerTimes: List<PrayerTimeEntry>,
    timezoneLabel: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${cell.hijriDay} $monthName ${cell.hijriYear} H",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("hijri_selected_day_title")
                    )
                    Text(
                        text = "${cell.dayOfWeekLabel} ${cell.pasaranJawa}, ${cell.gregorianDateLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cell.isToday) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Hari Ini",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (cell.keyEvent != null) {
                val accent = eventTypeColor(cell.keyEvent.type)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.14f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${cell.keyEvent.type.label}: ${cell.keyEvent.title}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                        Text(
                            text = cell.keyEvent.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (cell.isSunnahMonThu) {
                Text(
                    text = "Amalan Sunnah: Puasa Sunnah hari ${cell.dayOfWeekLabel}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                prayerTimes.forEach { entry ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.timeLabel.substring(0, 5),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = "Jadwal sholat di atas dalam zona waktu $timezoneLabel untuk tanggal terpilih.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun eventTypeColor(type: HijriEventType): Color = when (type) {
    HijriEventType.MAJOR_HOLIDAY -> MaterialTheme.colorScheme.primary
    HijriEventType.SUNNAH_FASTING -> MaterialTheme.colorScheme.secondary
    HijriEventType.RUKYAT_OBSERVATION -> MaterialTheme.colorScheme.tertiary
}
