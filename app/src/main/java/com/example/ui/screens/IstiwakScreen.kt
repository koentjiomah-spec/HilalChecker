package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.falak.FalakEphemerisEngine
import com.example.domain.falak.IstiwakAndPrayerResult
import com.example.domain.falak.TrueNoonIstiwakResult
import com.example.domain.falak.WaktuIstiwakCalculator
import com.example.ui.HilalUiState
import com.example.ui.components.HijriCalendarWidgetCard
import com.example.ui.components.MarkazLocationBar
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale

@Composable
fun IstiwakScreen(
    state: HilalUiState,
    onOpenMarkazSheet: () -> Unit,
    onShiftIstiwakDay: (Int) -> Unit = {},
    onResetIstiwakToday: () -> Unit = {},
    onRefreshGpsDirect: () -> Unit = {},
    onPermissionDenied: (String) -> Unit = {},
    onExportReport: () -> Unit = {},
    onShiftCalendarMonth: (Int) -> Unit = {},
    onResetCalendarMonth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val istiwak = state.istiwakResult
    val trueNoon = state.trueNoonResult

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onRefreshGpsDirect()
        } else {
            onPermissionDenied("Izin lokasi GPS ditolak. Pilih lokasi dari daftar atau isi koordinat manual.")
        }
    }

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

        if (state.gpsStatusMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = state.gpsStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (trueNoon != null) {
            item {
                TrueNoonCalculatorCard(
                    trueNoon = trueNoon,
                    dayOffset = state.istiwakDayOffset,
                    isFetchingGps = state.isFetchingGps,
                    onShiftDay = onShiftIstiwakDay,
                    onResetToday = onResetIstiwakToday,
                    onUseGps = {
                        val fine = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (fine || coarse) {
                            onRefreshGpsDirect()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                )
            }
        }

        if (istiwak != null) {
            item {
                IstiwakClockCard(istiwak = istiwak)
            }

            if (state.hijriMonthCalendar != null) {
                item {
                    HijriCalendarWidgetCard(
                        calendar = state.hijriMonthCalendar,
                        markaz = state.markaz,
                        onShiftMonth = onShiftCalendarMonth,
                        onResetCurrentMonth = onResetCalendarMonth
                    )
                }
            }

            item {
                PrayerScheduleCard(istiwak = istiwak)
            }
        }

        item {
            FalakConverterCalculatorCard()
        }

        item {
            FilledTonalButton(
                onClick = onExportReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("istiwak_export_report_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ekspor Laporan Waktu Istiwak & Hilal (PDF / Teks)")
            }
        }
    }
}

@Composable
private fun TrueNoonCalculatorCard(
    trueNoon: TrueNoonIstiwakResult,
    dayOffset: Int,
    isFetchingGps: Boolean,
    onShiftDay: (Int) -> Unit,
    onResetToday: () -> Unit,
    onUseGps: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Kalkulator Waktu Istiwak",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tengah Hari Hakiki (Zawal = 12:00:00 WIS)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onUseGps,
                    enabled = !isFetchingGps,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("istiwak_gps_button")
                ) {
                    if (isFetchingGps) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GPS")
                }
            }

            // Date Stepper for True Noon
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onShiftDay(-1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("istiwak_prev_day_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Hari sebelumnya"
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = trueNoon.dateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = trueNoon.hijriDateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onShiftDay(1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("istiwak_next_day_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Hari berikutnya"
                        )
                    }
                }
            }

            if (dayOffset != 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onResetToday,
                        modifier = Modifier.testTag("istiwak_reset_today_button")
                    ) {
                        Text("Kembali ke Hari Ini")
                    }
                }
            }

            // Main True Noon Time Readout
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "WAKTU ISTIWAK / TRANSIT MATAHARI (LOKAL)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = trueNoon.trueNoonLocalLabel,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("true_noon_local_text")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${trueNoon.trueNoonUtcLabel} • Dzuhur (+2m): ${trueNoon.dzuhurIhtiyatLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Mathematical breakdown for GPS location
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IstiwakFormulaRow(
                        "Bujur GPS vs Meridian ${trueNoon.markaz.timezoneLabel}",
                        String.format(Locale.US, "%.4f° vs %.0f°", trueNoon.markaz.longitude, trueNoon.standardMeridianDeg)
                    )
                    IstiwakFormulaRow(
                        "Koreksi Bujur (Δλ × 4 mnt)",
                        WaktuIstiwakCalculator.formatOffsetMinSec(trueNoon.longitudeCorrectionMinutes)
                    )
                    IstiwakFormulaRow(
                        "Equation of Time (Ta'dil Waqt)",
                        WaktuIstiwakCalculator.formatOffsetMinSec(trueNoon.equationOfTimeMinutes)
                    )
                    IstiwakFormulaRow(
                        "Total Selisih (WIS - Jam Lokal)",
                        WaktuIstiwakCalculator.formatOffsetMinSec(trueNoon.totalWisOffsetMinutes)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    IstiwakFormulaRow(
                        "Tinggi Matahari saat Istiwak",
                        FalakEphemerisEngine.formatDms(trueNoon.solarNoonAltitudeDeg)
                    )
                    IstiwakFormulaRow(
                        "Deklinasi Matahari (δ☉)",
                        FalakEphemerisEngine.formatDms(trueNoon.solarDeclinationDeg, signed = true)
                    )
                    IstiwakFormulaRow(
                        "Verifikasi WIS saat Zawal",
                        trueNoon.wisVerificationLabel
                    )
                    Text(
                        text = trueNoon.shadowDirectionAtNoon,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun IstiwakFormulaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun IstiwakClockCard(istiwak: IstiwakAndPrayerResult) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Jam Istiwak Berjalan (WIS)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = istiwak.pasaranJawa,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = istiwak.waktuIstiwakLabel,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("wis_clock_text")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Waktu Lokal (${istiwak.markaz.timezoneLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            istiwak.localStandardTimeLabel.substringBefore(" "),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Waktu Ghurubiyah",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            istiwak.waktuGhurubiyahLabel.substringBefore(" "),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "${istiwak.hijriDateLabel} • ${istiwak.pasaranJawa}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = istiwak.gregorianDateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerScheduleCard(istiwak: IstiwakAndPrayerResult) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Jadwal Sholat & Imsakiyah",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = istiwak.nextPrayerCountdown,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            istiwak.prayerTimes.forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (entry.isNext) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                    },
                    border = if (entry.isNext) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (entry.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = entry.arabicSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${entry.timeLabel} ${istiwak.markaz.timezoneLabel}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (entry.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FalakConverterCalculatorCard() {
    var decimalInput by remember { mutableStateOf("3.5417") }
    val parsedDecimal = decimalInput.replace(',', '.').toDoubleOrNull()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Kalkulator Falak: Konversi Desimal ↔ DMS",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = decimalInput,
                onValueChange = { decimalInput = it },
                label = { Text("Sudut Derajat Desimal (contoh: -6.9875 atau 3.5417)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("falak_decimal_input")
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Derajat Menit Detik (DMS)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (parsedDecimal != null) {
                            FalakEphemerisEngine.formatDms(parsedDecimal, signed = true)
                        } else {
                            "Format tidak valid"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
