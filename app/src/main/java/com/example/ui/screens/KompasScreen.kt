package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.falak.FalakEphemerisEngine
import com.example.ui.CompassTargetMode
import com.example.ui.HilalUiState
import com.example.ui.components.MarkazLocationBar
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun KompasScreen(
    state: HilalUiState,
    onSelectMode: (CompassTargetMode) -> Unit,
    onToggleManualOverride: (Boolean) -> Unit,
    onManualAzimuthChange: (Float) -> Unit,
    onOpenMarkazSheet: () -> Unit,
    onRefreshGpsDirect: () -> Unit = {},
    onPermissionDenied: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hilal = state.hilalResult
    val istiwak = state.istiwakResult

    val hilalAz = hilal?.moonAzimuthDeg ?: 274.0
    val sunAz = hilal?.sunAzimuthDeg ?: 270.0
    val qiblaAz = istiwak?.qiblaAzimuthDeg ?: 295.15
    val qiblaDist = istiwak?.qiblaDistanceKm ?: 7920.0

    val targetAzimuth = when (state.compassTargetMode) {
        CompassTargetMode.KIBLAT -> qiblaAz
        CompassTargetMode.HILAL -> hilalAz
        CompassTargetMode.SUNSET -> sunAz
    }

    val sensorAvailable = state.compassReading.hasHardwareSensor
    val trueMagneticHeading = ((state.compassReading.magneticAzimuthDeg + state.magneticDeclinationDeg) % 360f + 360f) % 360f
    val activeHeading = if (!sensorAvailable || state.useManualCompassOverride) {
        state.manualCompassAzimuthDeg.toDouble()
    } else {
        trueMagneticHeading.toDouble()
    }

    val deltaDeg = ((targetAzimuth - activeHeading + 540.0) % 360.0) - 180.0
    val isAligned = abs(deltaDeg) <= 2.5

    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(isAligned) {
        if (isAligned && !wasAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasAligned = isAligned
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onRefreshGpsDirect()
        } else {
            onPermissionDenied("Izin lokasi ditolak. Pilih kota atau isi koordinat manual.")
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

        // Qibla Bearing & Direct GPS Refresh Card
        item {
            QiblaSummaryCard(
                qiblaAzimuthDeg = qiblaAz,
                qiblaDistanceKm = qiblaDist,
                declinationDeg = state.magneticDeclinationDeg,
                isFetchingGps = state.isFetchingGps,
                onRefreshGps = {
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompassTargetMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.compassTargetMode == mode,
                        onClick = { onSelectMode(mode) },
                        label = { Text(mode.label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (mode == CompassTargetMode.KIBLAT) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = if (mode == CompassTargetMode.KIBLAT) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compass_mode_${mode.name}")
                    )
                }
            }
        }

        item {
            CompassDialCard(
                activeHeadingDeg = activeHeading,
                targetAzimuthDeg = targetAzimuth,
                hilalAzimuthDeg = hilalAz,
                qiblaAzimuthDeg = qiblaAz,
                sunAzimuthDeg = sunAz,
                pitchDeg = state.compassReading.pitchDeg,
                rollDeg = state.compassReading.rollDeg,
                isSensorActive = sensorAvailable && !state.useManualCompassOverride,
                mode = state.compassTargetMode,
                deltaDeg = deltaDeg,
                isAligned = isAligned
            )
        }

        // Sensor Fusion Telemetry & Calibration Card
        item {
            SensorFusionTelemetryCard(
                state = state,
                onToggleManualOverride = onToggleManualOverride,
                onManualAzimuthChange = onManualAzimuthChange
            )
        }

        if (istiwak != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Referensi Azimuth & Roshdul Qiblah",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Koordinat Ka'bah: 21° 25' 21\" LU, 39° 49' 34\" BT",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = istiwak.roshdulQiblahSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QiblaSummaryCard(
    qiblaAzimuthDeg: Double,
    qiblaDistanceKm: Double,
    declinationDeg: Float,
    isFetchingGps: Boolean,
    onRefreshGps: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ARAH KIBLAT (KA'BAH)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = FalakEphemerisEngine.formatDms(qiblaAzimuthDeg),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("qibla_dms_text")
                    )
                    Text(
                        text = "${cardinalDirectionName(qiblaAzimuthDeg)} • ${String.format(Locale.US, "%,.0f", qiblaDistanceKm)} km ke Makkah",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onRefreshGps,
                    enabled = !isFetchingGps,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("qibla_gps_refresh_button")
                ) {
                    if (isFetchingGps) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Perbarui GPS",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GPS")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Azimuth: ${String.format(Locale.US, "%.2f°", qiblaAzimuthDeg)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format(Locale.US, "Deklinasi Mag: %+.2f°", declinationDeg),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CompassDialCard(
    activeHeadingDeg: Double,
    targetAzimuthDeg: Double,
    hilalAzimuthDeg: Double,
    qiblaAzimuthDeg: Double,
    sunAzimuthDeg: Double,
    pitchDeg: Float,
    rollDeg: Float,
    isSensorActive: Boolean,
    mode: CompassTargetMode,
    deltaDeg: Double,
    isAligned: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val cdCompass = stringResource(id = R.string.cd_compass_dial)

    val isLevel = !isSensorActive || (abs(pitchDeg) <= 6f && abs(rollDeg) <= 6f)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            width = if (isAligned) 2.2.dp else 1.dp,
            color = if (isAligned) secondaryColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isAligned) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.testTag("compass_alignment_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isAligned) Icons.Rounded.CheckCircle else Icons.Outlined.Explore,
                        contentDescription = null,
                        tint = if (isAligned) secondaryColor else primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    val guideText = when {
                        isAligned && mode == CompassTargetMode.KIBLAT -> "MENGHADAP KIBLAT (±2.5°)"
                        isAligned -> "TERARAH KE ${mode.label.uppercase()} (±2.5°)"
                        deltaDeg > 0 -> "Putar ke kanan ${String.format(Locale.US, "%.1f°", deltaDeg)}"
                        else -> "Putar ke kiri ${String.format(Locale.US, "%.1f°", abs(deltaDeg))}"
                    }
                    Text(
                        text = guideText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isAligned) secondaryColor else onSurface
                    )
                }
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = cdCompass }
                        .testTag("compass_dial_canvas")
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f - 12f

                    // Alignment ambient glow when locked onto Kaaba/target
                    if (isAligned) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(secondaryColor.copy(alpha = 0.25f), Color.Transparent),
                                center = center,
                                radius = radius + 14f
                            ),
                            radius = radius + 14f,
                            center = center
                        )
                    }

                    // Outer bezel
                    drawCircle(
                        color = surfaceHigh,
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = if (isAligned) secondaryColor else primaryColor.copy(alpha = 0.4f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = if (isAligned) 5f else 3.5f)
                    )

                    // Inner ring
                    drawCircle(
                        color = surfaceColor.copy(alpha = 0.65f),
                        radius = radius * 0.56f,
                        center = center
                    )

                    // Top device heading lubber line
                    drawLine(
                        color = if (isAligned) secondaryColor else onSurface,
                        start = Offset(center.x, center.y - radius - 8f),
                        end = Offset(center.x, center.y - radius + 18f),
                        strokeWidth = 6.5f,
                        cap = StrokeCap.Round
                    )

                    // Rotate compass card opposite to heading
                    rotate(degrees = (-activeHeadingDeg).toFloat(), pivot = center) {
                        for (deg in 0 until 360 step 5) {
                            val isMajor = deg % 30 == 0
                            val isCardinal = deg % 90 == 0
                            val rad = Math.toRadians(deg - 90.0)
                            val outerR = radius - 10f
                            val innerR = when {
                                isCardinal -> outerR - 18f
                                isMajor -> outerR - 12f
                                else -> outerR - 6f
                            }
                            drawLine(
                                color = when {
                                    deg == 0 -> Color(0xFFF27070)
                                    isMajor -> onSurface.copy(alpha = 0.8f)
                                    else -> onSurfaceVariant.copy(alpha = 0.35f)
                                },
                                start = Offset(
                                    center.x + (innerR * cos(rad)).toFloat(),
                                    center.y + (innerR * sin(rad)).toFloat()
                                ),
                                end = Offset(
                                    center.x + (outerR * cos(rad)).toFloat(),
                                    center.y + (outerR * sin(rad)).toFloat()
                                ),
                                strokeWidth = if (isMajor) 2.5f else 1.2f
                            )
                        }

                        val cardinalLabels = listOf(0 to "U", 90 to "T", 180 to "S", 270 to "B")
                        cardinalLabels.forEach { (deg, label) ->
                            val rad = Math.toRadians(deg - 90.0)
                            val labelR = radius - 40f
                            val measured = textMeasurer.measure(
                                label,
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (deg == 0) Color(0xFFF27070) else onSurface
                                )
                            )
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(
                                    center.x + (labelR * cos(rad)).toFloat() - measured.size.width / 2f,
                                    center.y + (labelR * sin(rad)).toFloat() - measured.size.height / 2f
                                )
                            )
                        }

                        // Markers for Sun, Hilal, and Qibla (with Kaaba icon)
                        fun drawStandardPointer(azDeg: Double, color: Color, radiusFactor: Float, isActive: Boolean) {
                            rotate(degrees = azDeg.toFloat(), pivot = center) {
                                val tipY = center.y - radius * radiusFactor
                                if (isActive) {
                                    drawLine(
                                        color = color,
                                        start = center,
                                        end = Offset(center.x, tipY + 14f),
                                        strokeWidth = 6f,
                                        cap = StrokeCap.Round
                                    )
                                    val arrow = Path().apply {
                                        moveTo(center.x, tipY)
                                        lineTo(center.x - 12f, tipY + 22f)
                                        lineTo(center.x + 12f, tipY + 22f)
                                        close()
                                    }
                                    drawPath(arrow, color = color)
                                } else {
                                    drawCircle(
                                        color = color.copy(alpha = 0.85f),
                                        radius = 6.5f,
                                        center = Offset(center.x, tipY + 10f)
                                    )
                                }
                            }
                        }

                        drawStandardPointer(sunAzimuthDeg, tertiaryColor, 0.78f, mode == CompassTargetMode.SUNSET)
                        drawStandardPointer(hilalAzimuthDeg, primaryColor, 0.78f, mode == CompassTargetMode.HILAL)

                        // Dedicated Kaaba Qibla pointer
                        rotate(degrees = qiblaAzimuthDeg.toFloat(), pivot = center) {
                            val tipY = center.y - radius * 0.84f
                            val isQiblaActive = mode == CompassTargetMode.KIBLAT
                            if (isQiblaActive) {
                                drawLine(
                                    color = secondaryColor,
                                    start = center,
                                    end = Offset(center.x, tipY + 28f),
                                    strokeWidth = 6.5f,
                                    cap = StrokeCap.Round
                                )
                            }
                            // Kaaba Cube Icon at Qibla Azimuth
                            val boxSize = if (isQiblaActive) 26f else 18f
                            val topLeft = Offset(center.x - boxSize / 2f, tipY)
                            drawRoundRect(
                                color = Color(0xFF14171F),
                                topLeft = topLeft,
                                size = Size(boxSize, boxSize),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            drawRoundRect(
                                color = Color(0xFFF4C464),
                                topLeft = topLeft,
                                size = Size(boxSize, boxSize),
                                cornerRadius = CornerRadius(4f, 4f),
                                style = Stroke(width = 2.2f)
                            )
                            // Golden Kiswah band
                            drawLine(
                                color = Color(0xFFF4C464),
                                start = Offset(topLeft.x + 2f, topLeft.y + boxSize * 0.32f),
                                end = Offset(topLeft.x + boxSize - 2f, topLeft.y + boxSize * 0.32f),
                                strokeWidth = 2.8f
                            )
                        }
                    }

                    // Center Spirit-Level Reticle (driven by Gyroscope/Rotation Vector pitch & roll)
                    val levelRadius = 26f
                    drawCircle(
                        color = (if (isLevel) secondaryColor else tertiaryColor).copy(alpha = 0.25f),
                        radius = levelRadius,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                    val bubbleDx = (rollDeg / 25f).coerceIn(-1f, 1f) * (levelRadius - 8f)
                    val bubbleDy = (pitchDeg / 25f).coerceIn(-1f, 1f) * (levelRadius - 8f)
                    drawCircle(
                        color = if (isLevel) secondaryColor else tertiaryColor,
                        radius = 7.5f,
                        center = Offset(center.x + bubbleDx, center.y + bubbleDy)
                    )
                }
            }

            if (isSensorActive && !isLevel) {
                Text(
                    text = "Posisikan ponsel datar agar sensor magnetometer & giroskop akurat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AzimuthLegendItem("Hadap (True N)", activeHeadingDeg, onSurface)
                AzimuthLegendItem("Target ${mode.label}", targetAzimuthDeg, if (mode == CompassTargetMode.KIBLAT) secondaryColor else primaryColor)
            }
        }
    }
}

@Composable
private fun SensorFusionTelemetryCard(
    state: HilalUiState,
    onToggleManualOverride: (Boolean) -> Unit,
    onManualAzimuthChange: (Float) -> Unit
) {
    val reading = state.compassReading
    val sensorAvailable = reading.hasHardwareSensor

    val accuracyLabel = when (reading.accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Akurasi Tinggi"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Akurasi Sedang"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Akurasi Rendah"
        else -> "Perlu Kalibrasi"
    }
    val accuracyColor = when (reading.accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> MaterialTheme.colorScheme.secondary
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val showCalibrationTip = sensorAvailable &&
        (reading.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
            reading.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)

    Card(
        shape = RoundedCornerShape(18.dp),
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Fusi Magnetometer & Giroskop",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (sensorAvailable) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accuracyColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = accuracyLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = accuracyColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (sensorAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SensorChipBox(
                        label = "Magnetometer",
                        value = if (reading.hasMagnetometer) {
                            String.format(Locale.US, "%.1f μT", reading.magneticFieldUt)
                        } else "N/A",
                        modifier = Modifier.weight(1f)
                    )
                    SensorChipBox(
                        label = "Giroskop",
                        value = if (reading.hasGyroscope) {
                            String.format(Locale.US, "%.2f rad/s", reading.gyroRateRadPerSec)
                        } else "Fused",
                        modifier = Modifier.weight(1f)
                    )
                    SensorChipBox(
                        label = "Kemiringan",
                        value = String.format(Locale.US, "%.0f° / %.0f°", reading.pitchDeg, reading.rollDeg),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (showCalibrationTip) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Gerakkan ponsel membentuk angka 8 untuk mengkalibrasi sensor kompas dan jauhkan dari benda logam.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (sensorAvailable) "Simulasi Manual Kompas" else "Sensor Kompas Tidak Tersedia",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (sensorAvailable) {
                            "Aktifkan untuk menggeser arah hadap secara manual."
                        } else {
                            "Mode simulasi manual aktif otomatis pada emulator/perangkat tanpa magnetometer."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sensorAvailable) {
                    Switch(
                        checked = state.useManualCompassOverride,
                        onCheckedChange = onToggleManualOverride,
                        modifier = Modifier.testTag("compass_manual_switch")
                    )
                }
            }

            if (!sensorAvailable || state.useManualCompassOverride) {
                Slider(
                    value = state.manualCompassAzimuthDeg,
                    onValueChange = onManualAzimuthChange,
                    valueRange = 0f..359f,
                    modifier = Modifier.testTag("compass_manual_slider")
                )
            }
        }
    }
}

@Composable
private fun SensorChipBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AzimuthLegendItem(label: String, deg: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format(Locale.US, "%.1f°", deg),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = color
        )
    }
}

private fun cardinalDirectionName(deg: Double): String {
    val normalized = ((deg % 360.0) + 360.0) % 360.0
    return when {
        normalized < 22.5 || normalized >= 337.5 -> "Utara"
        normalized < 67.5 -> "Timur Laut"
        normalized < 112.5 -> "Timur"
        normalized < 157.5 -> "Tenggara"
        normalized < 202.5 -> "Selatan"
        normalized < 247.5 -> "Barat Daya"
        normalized < 292.5 -> "Barat"
        else -> "Barat Laut"
    }
}
