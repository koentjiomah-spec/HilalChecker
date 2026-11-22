package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.firebase.CloudObservationsUiState
import com.example.domain.falak.FalakCatalog
import com.example.domain.falak.FalakEphemerisEngine
import com.example.domain.falak.HilalResult
import com.example.domain.falak.RukyatCriteria
import com.example.domain.falak.VerdictStatus
import com.example.ui.HilalUiState
import com.example.ui.components.FalakMetricTile
import com.example.ui.components.LunarPhaseVisualizationCard
import com.example.ui.components.MabimsMathematicalModelCard
import com.example.ui.components.MarkazLocationBar
import com.example.ui.components.RukyatObservationsCard
import com.example.ui.components.VerdictBadge
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale

@Composable
fun HisabScreen(
    state: HilalUiState,
    observationsState: CloudObservationsUiState = CloudObservationsUiState.Loading,
    onSelectMonth: (Int) -> Unit,
    onShiftYear: (Int) -> Unit,
    onSelectCriteria: (RukyatCriteria) -> Unit,
    onOpenMarkazSheet: () -> Unit,
    onOpenSimulation: () -> Unit,
    onLunarDayOffsetChange: (Int) -> Unit = {},
    onExportReport: () -> Unit = {},
    onSaveObservation: (hilalSighted: Boolean, notes: String) -> Unit = { _, _ -> },
    onUpdateObservation: (observationId: String, hilalSighted: Boolean, notes: String) -> Unit = { _, _, _ -> },
    onDeleteObservation: (observationId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val result = state.hilalResult
    var showAdvancedData by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MarkazLocationBar(
                markaz = state.markaz,
                onClick = onOpenMarkazSheet
            )
        }

        item {
            HeroTwilightCard(
                hijriYear = state.selectedHijriYear,
                hijriMonth = state.selectedHijriMonth,
                result = result,
                onSelectMonth = onSelectMonth,
                onShiftYear = onShiftYear
            )
        }

        if (result != null) {
            item {
                CriteriaEvaluationCard(
                    result = result,
                    selectedCriteria = state.selectedCriteria,
                    onSelectCriteria = onSelectCriteria,
                    onOpenSimulation = onOpenSimulation
                )
            }

            item {
                EphemerisMetricsGrid(result = result)
            }

            if (state.mabimsPrediction != null) {
                item {
                    MabimsMathematicalModelCard(prediction = state.mabimsPrediction)
                }
            }

            if (state.lunarPhaseData != null) {
                item {
                    LunarPhaseVisualizationCard(
                        lunarPhase = state.lunarPhaseData,
                        dayOffset = state.lunarDayOffset,
                        onDayOffsetChange = onLunarDayOffsetChange
                    )
                }
            }

            item {
                AdvancedEphemerisCard(
                    result = result,
                    expanded = showAdvancedData,
                    onToggle = { showAdvancedData = !showAdvancedData }
                )
            }

            item {
                FilledTonalButton(
                    onClick = onExportReport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("hisab_export_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ekspor Laporan Prediksi Hilal & Istiwak (PDF / Teks)")
                }
            }

            item {
                RukyatObservationsCard(
                    currentHilal = result,
                    observationsState = observationsState,
                    isSavingObservation = state.isSavingObservation,
                    onSaveObservation = onSaveObservation,
                    onUpdateObservation = onUpdateObservation,
                    onDeleteObservation = onDeleteObservation
                )
            }
        }
    }
}

@Composable
private fun HeroTwilightCard(
    hijriYear: Int,
    hijriMonth: Int,
    result: HilalResult?,
    onSelectMonth: (Int) -> Unit,
    onShiftYear: (Int) -> Unit
) {
    val monthName = FalakCatalog.hijriMonths.firstOrNull { it.first == hijriMonth }?.second ?: "Ramadhan"

    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_twilight_banner),
                    contentDescription = stringResource(id = R.string.cd_twilight_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x66070D1C),
                                    Color(0xCC070D1C)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0x660E172E),
                            border = BorderStroke(1.dp, Color(0x55F4C464))
                        ) {
                            Text(
                                text = "HISAB EPHEMERIS RUKYAT",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF4C464),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x880E172E))
                        ) {
                            IconButton(
                                onClick = { onShiftYear(-1) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("prev_hijri_year_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "Tahun Hijriah sebelumnya",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = "$hijriYear H",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                color = Color.White
                            )
                            IconButton(
                                onClick = { onShiftYear(1) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("next_hijri_year_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Tahun Hijriah berikutnya",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "Awal $monthName $hijriYear H",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        if (result != null) {
                            Text(
                                text = "Rukyat Sore: ${result.observationDayLabel}, ${result.observationDateLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD6E0F5)
                            )
                        }
                    }
                }
            }

            // Hijri month horizontal selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FalakCatalog.hijriMonths.forEach { (num, name) ->
                    val selected = num == hijriMonth
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMonth(num) },
                        label = { Text(name) },
                        modifier = Modifier.testTag("hijri_month_chip_$num"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CriteriaEvaluationCard(
    result: HilalResult,
    selectedCriteria: RukyatCriteria,
    onSelectCriteria: (RukyatCriteria) -> Unit,
    onOpenSimulation: () -> Unit
) {
    val activeVerdict = result.verdicts[selectedCriteria]
        ?: result.verdicts.values.first()

    val borderColor = when (activeVerdict.status) {
        VerdictStatus.MEMENUHI -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        VerdictStatus.MARGINAL -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
        VerdictStatus.TIDAK_MEMENUHI -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerdictBadge(
                    status = activeVerdict.status,
                    label = activeVerdict.summary,
                    modifier = Modifier.testTag("verdict_badge")
                )
                Surface(
                    onClick = onOpenSimulation,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("open_horizon_sim_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WbTwilight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Lihat Ufuk",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = selectedCriteria.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = activeVerdict.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Criteria Switcher Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RukyatCriteria.entries.forEach { criteria ->
                    val verdict = result.verdicts[criteria]
                    val isSelected = criteria == selectedCriteria
                    val dotColor = when (verdict?.status) {
                        VerdictStatus.MEMENUHI -> MaterialTheme.colorScheme.secondary
                        VerdictStatus.MARGINAL -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCriteria(criteria) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        },
                        label = { Text(criteria.shortCode) },
                        modifier = Modifier.testTag("criteria_chip_${criteria.name}")
                    )
                }
            }
        }
    }
}

@Composable
private fun EphemerisMetricsGrid(result: HilalResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FalakMetricTile(
                title = "Tinggi Mar'i",
                value = FalakEphemerisEngine.formatDms(result.moonAltitudeMariDeg, signed = true),
                subtitle = "Haqiqi: ${FalakEphemerisEngine.formatDegSimple(result.moonAltitudeHaqiqiDeg)}",
                icon = Icons.Outlined.Height,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_altitude")
            )
            FalakMetricTile(
                title = "Elongasi Topo",
                value = FalakEphemerisEngine.formatDms(result.elongationTopoDeg),
                subtitle = "Geosentris: ${FalakEphemerisEngine.formatDegSimple(result.elongationGeoDeg)}",
                icon = Icons.Outlined.CompareArrows,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_elongation")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val ageSign = if (result.moonAgeHours < 0) "-" else ""
            val absAge = kotlin.math.abs(result.moonAgeHours)
            val ageH = absAge.toInt()
            val ageM = ((absAge - ageH) * 60).toInt()
            FalakMetricTile(
                title = "Umur Bulan",
                value = "$ageSign${ageH}j ${ageM}m",
                subtitle = if (result.ijtimaBeforeSunset) "Pasca Ijtima' saat Ghurub" else "Ijtima' belum terjadi",
                icon = Icons.Outlined.AccessTime,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_moon_age")
            )
            FalakMetricTile(
                title = "Lama Hilal",
                value = String.format(Locale.US, "%.1f mnt", result.mukutsMinutes),
                subtitle = "Mukuts di atas ufuk",
                icon = Icons.Outlined.Schedule,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_mukuts")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FalakMetricTile(
                title = "Ghurub Syams",
                value = result.sunsetLocalLabel.substringBefore(" "),
                subtitle = "Terbenam Matahari (${result.markaz.timezoneLabel})",
                icon = Icons.Outlined.WbTwilight,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_sunset")
            )
            FalakMetricTile(
                title = "Ghurub Qamar",
                value = if (result.moonsetUtcMillis != null) {
                    result.moonsetLocalLabel.substringBefore(" ")
                } else {
                    "--:--:--"
                },
                subtitle = if (result.moonsetUtcMillis != null) {
                    "Terbenam Bulan (${result.markaz.timezoneLabel})"
                } else {
                    "Di bawah ufuk saat Maghrib"
                },
                icon = Icons.Outlined.NightsStay,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_moonset")
            )
        }
    }
}

@Composable
private fun AdvancedEphemerisCard(
    result: HilalResult,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp)
                    .testTag("toggle_advanced_ephemeris"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Brightness3,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Data Falak Lengkap & Ijtima'",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ijtima': ${result.ijtimaLocalLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Tutup detail" else "Buka detail",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EphemerisDataRow("Waktu Ijtima' (Konjungsi)", result.ijtimaLocalLabel)
                    EphemerisDataRow("Azimuth Matahari saat Ghurub", FalakEphemerisEngine.formatDms(result.sunAzimuthDeg))
                    EphemerisDataRow("Azimuth Hilal saat Ghurub", FalakEphemerisEngine.formatDms(result.moonAzimuthDeg))
                    EphemerisDataRow(
                        "Beda Azimuth (Bulan - Matahari)",
                        FalakEphemerisEngine.formatDms(result.azimuthDifferenceDeg, signed = true)
                    )
                    EphemerisDataRow(
                        "Tinggi Hilal Haqiqi (Geosentris)",
                        FalakEphemerisEngine.formatDms(result.moonAltitudeHaqiqiDeg, signed = true)
                    )
                    EphemerisDataRow(
                        "Lebar Sabit (Semidiameter Arc)",
                        String.format(Locale.US, "%.3f arcmin", result.crescentWidthArcmin)
                    )
                    EphemerisDataRow(
                        "Iluminasi Piringan Bulan",
                        String.format(Locale.US, "%.2f %%", result.illuminationPercent)
                    )
                    EphemerisDataRow(
                        "Sudut Kemiringan Sabit",
                        String.format(Locale.US, "%.1f°", result.crescentTiltDeg)
                    )
                }
            }
        }
    }
}

@Composable
private fun EphemerisDataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
