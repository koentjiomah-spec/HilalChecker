package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.falak.FalakEphemerisEngine
import com.example.domain.falak.HilalResult
import com.example.domain.falak.HorizonTrackPoint
import com.example.ui.HilalUiState
import com.example.ui.components.MarkazLocationBar
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SimulasiUfukScreen(
    state: HilalUiState,
    onOffsetChange: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onOpenMarkazSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = state.hilalResult

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

        if (result != null && result.horizonTrack.isNotEmpty()) {
            val interpolated = interpolateTrackPoint(result.horizonTrack, state.horizonOffsetMinutes)

            item {
                HorizonCanvasCard(
                    result = result,
                    currentPoint = interpolated,
                    offsetMinutes = state.horizonOffsetMinutes,
                    isPlaying = state.isPlayingHorizonSimulation,
                    onOffsetChange = onOffsetChange,
                    onTogglePlay = onTogglePlay
                )
            }

            item {
                SimulationLiveStatsCard(
                    result = result,
                    point = interpolated,
                    offsetMinutes = state.horizonOffsetMinutes
                )
            }
        }
    }
}

@Composable
private fun HorizonCanvasCard(
    result: HilalResult,
    currentPoint: HorizonTrackPoint,
    offsetMinutes: Float,
    isPlaying: Boolean,
    onOffsetChange: (Float) -> Unit,
    onTogglePlay: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Simulasi Ufuk Barat",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${result.hijriMonthName} ${result.hijriYear} H • ${result.observationDateLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    val sign = if (offsetMinutes >= 0) "+" else ""
                    Text(
                        text = "Ghurub $sign${offsetMinutes.roundToInt()}m",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Sky Dome Horizon Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            contentDescription = "Visualisasi posisi matahari dan hilal di ufuk barat"
                        }
                        .testTag("horizon_sky_canvas")
                ) {
                    drawHorizonSky(
                        result = result,
                        currentPoint = currentPoint,
                        offsetMinutes = offsetMinutes,
                        textMeasurer = textMeasurer
                    )
                }
            }

            // Timeline scrubber + Play/Pause
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("play_horizon_sim_button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda simulasi" else "Putar simulasi"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPlaying) "Jeda" else "Putar")
                    }

                    Slider(
                        value = offsetMinutes,
                        onValueChange = onOffsetChange,
                        valueRange = -30f..60f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("horizon_time_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    FilledTonalIconButton(
                        onClick = { onOffsetChange(0f) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reset_horizon_sim_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset ke waktu Ghurub"
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "-30m (Pra-Ghurub)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "0m (Ghurub)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "+60m (Isya)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawHorizonSky(
    result: HilalResult,
    currentPoint: HorizonTrackPoint,
    offsetMinutes: Float,
    textMeasurer: TextMeasurer
) {
    // Dynamic twilight factor based on sun altitude
    val sunAlt = currentPoint.sunAltitudeDeg
    val skyTopColor = when {
        sunAlt > 2.0 -> Color(0xFF1A438A)
        sunAlt > -3.0 -> Color(0xFF10234E)
        sunAlt > -8.0 -> Color(0xFF0A1430)
        else -> Color(0xFF050915)
    }
    val skyHorizonGlow = when {
        sunAlt > 2.0 -> Color(0xFFDF8C4D)
        sunAlt > -2.0 -> Color(0xFFE0713B)
        sunAlt > -7.0 -> Color(0xFF7D3439)
        else -> Color(0xFF161E3B)
    }

    val horizonY = size.height * 0.78f
    val minAlt = -5.0
    val maxAlt = 18.0
    val centerAz = result.sunAzimuthDeg
    val halfAzSpan = 18.0

    fun altToY(altDeg: Double): Float {
        val ratio = ((altDeg - 0.0) / (maxAlt - 0.0)).toFloat()
        return horizonY - ratio * (horizonY - 24f)
    }

    fun azToX(azDeg: Double): Float {
        var diff = azDeg - centerAz
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        val norm = ((diff + halfAzSpan) / (2.0 * halfAzSpan)).toFloat()
        return norm * size.width
    }

    // Sky gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(skyTopColor, skyHorizonGlow),
            startY = 0f,
            endY = horizonY
        ),
        size = Size(size.width, horizonY)
    )

    // Ground / Sea below horizon
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0A1224), Color(0xFF040812)),
            startY = horizonY,
            endY = size.height
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(size.width, size.height - horizonY)
    )

    // Altitude gridlines (0, 3, 6, 10, 15 deg)
    val labelStyle = TextStyle(
        color = Color(0xAAC7D6F5),
        fontSize = 10.sp,
        fontFamily = JetBrainsMonoFamily
    )
    listOf(3.0, 6.0, 10.0, 15.0).forEach { alt ->
        val y = altToY(alt)
        val isMabims = alt == 3.0
        drawLine(
            color = if (isMabims) Color(0xAA3CD29D) else Color(0x33FFFFFF),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (isMabims) 2.5f else 1f,
            pathEffect = if (isMabims) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null
        )
        drawText(
            textMeasurer = textMeasurer,
            text = if (isMabims) "3° MABIMS" else "${alt.toInt()}°",
            topLeft = Offset(10f, y - 16f),
            style = labelStyle.copy(
                color = if (isMabims) Color(0xFF3CD29D) else Color(0x88FFFFFF)
            )
        )
    }

    // Vertical azimuth center line (Sun Sunset Azimuth)
    val centerAzX = azToX(centerAz)
    drawLine(
        color = Color(0x33FFFFFF),
        start = Offset(centerAzX, 16f),
        end = Offset(centerAzX, horizonY),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    )

    // Trajectory paths for Sun and Moon
    val sunPath = Path()
    val moonPath = Path()
    result.horizonTrack.forEachIndexed { idx, pt ->
        val sx = azToX(pt.sunAzimuthDeg)
        val sy = altToY(pt.sunAltitudeDeg)
        val mx = azToX(pt.moonAzimuthDeg)
        val my = altToY(pt.moonAltitudeMariDeg)
        if (idx == 0) {
            sunPath.moveTo(sx, sy)
            moonPath.moveTo(mx, my)
        } else {
            sunPath.lineTo(sx, sy)
            moonPath.lineTo(mx, my)
        }
    }
    drawPath(
        path = sunPath,
        color = Color(0x66FFA74A),
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    )
    drawPath(
        path = moonPath,
        color = Color(0x88F4C464),
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    )

    // Draw Sun disc
    val sunX = azToX(currentPoint.sunAzimuthDeg)
    val sunY = altToY(currentPoint.sunAltitudeDeg)
    if (sunY < size.height + 24f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xAAFFBE55), Color(0x00FF7A2F)),
                center = Offset(sunX, sunY),
                radius = 44f
            ),
            radius = 44f,
            center = Offset(sunX, sunY)
        )
        drawCircle(
            color = Color(0xFFFFCA62),
            radius = 15f,
            center = Offset(sunX, sunY)
        )
    }

    // Draw Crescent Moon (Hilal) with tilt toward Sun
    val moonX = azToX(currentPoint.moonAzimuthDeg)
    val moonY = altToY(currentPoint.moonAltitudeMariDeg)
    val tiltAngle = Math.toDegrees(
        kotlin.math.atan2((sunY - moonY).toDouble(), (sunX - moonX).toDouble())
    ).toFloat()

    // Subtle halo around Hilal
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x55F4C464), Color.Transparent),
            center = Offset(moonX, moonY),
            radius = 36f
        ),
        radius = 36f,
        center = Offset(moonX, moonY)
    )

    rotate(degrees = tiltAngle, pivot = Offset(moonX, moonY)) {
        val r = 13f
        // Bright outer limb pointing toward the sun (+X direction after rotation)
        drawArc(
            color = Color(0xFFFDF2C4),
            startAngle = -85f,
            sweepAngle = 170f,
            useCenter = false,
            topLeft = Offset(moonX - r, moonY - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 4.2f)
        )
    }

    // Horizon line foreground mask + label
    drawLine(
        color = Color(0xFF4C659A),
        start = Offset(0f, horizonY),
        end = Offset(size.width, horizonY),
        strokeWidth = 2.5f
    )
    drawText(
        textMeasurer = textMeasurer,
        text = "UFUK BARAT (0°) • Az ${String.format(Locale.US, "%.1f°", centerAz)}",
        topLeft = Offset(12f, horizonY + 10f),
        style = labelStyle.copy(color = Color(0xCCA2B3D6))
    )

    // Labels near bodies
    drawText(
        textMeasurer = textMeasurer,
        text = "Hilal (${String.format(Locale.US, "%.1f°", currentPoint.moonAltitudeMariDeg)})",
        topLeft = Offset((moonX + 18f).coerceIn(8f, size.width - 110f), (moonY - 14f).coerceIn(8f, horizonY - 22f)),
        style = labelStyle.copy(color = Color(0xFFF4C464), fontWeight = FontWeight.Bold)
    )
}

@Composable
private fun SimulationLiveStatsCard(
    result: HilalResult,
    point: HorizonTrackPoint,
    offsetMinutes: Float
) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Telemetri Posisi Saat Ini",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${point.timeLabel} ${result.markaz.timezoneLabel}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniStatBox(
                    label = "Tinggi Hilal",
                    value = FalakEphemerisEngine.formatDms(point.moonAltitudeMariDeg, signed = true),
                    modifier = Modifier.weight(1f)
                )
                MiniStatBox(
                    label = "Tinggi Matahari",
                    value = FalakEphemerisEngine.formatDms(point.sunAltitudeDeg, signed = true),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniStatBox(
                    label = "Azimuth Hilal",
                    value = FalakEphemerisEngine.formatDegSimple(point.moonAzimuthDeg),
                    modifier = Modifier.weight(1f)
                )
                MiniStatBox(
                    label = "Elongasi",
                    value = FalakEphemerisEngine.formatDegSimple(point.elongationDeg),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MiniStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun interpolateTrackPoint(
    track: List<HorizonTrackPoint>,
    offsetMinutes: Float
): HorizonTrackPoint {
    val clamped = offsetMinutes.coerceIn(
        track.first().offsetMinutesFromSunset.toFloat(),
        track.last().offsetMinutesFromSunset.toFloat()
    )
    val upperIdx = track.indexOfFirst { it.offsetMinutesFromSunset >= clamped }.coerceAtLeast(0)
    if (upperIdx == 0) return track.first()
    val a = track[upperIdx - 1]
    val b = track[upperIdx]
    val span = (b.offsetMinutesFromSunset - a.offsetMinutesFromSunset).coerceAtLeast(1)
    val t = ((clamped - a.offsetMinutesFromSunset) / span.toFloat()).toDouble()
    fun lerp(x: Double, y: Double) = x + (y - x) * t

    val nearest = if (abs(clamped - a.offsetMinutesFromSunset) < abs(b.offsetMinutesFromSunset - clamped)) a else b
    return HorizonTrackPoint(
        offsetMinutesFromSunset = clamped.roundToInt(),
        timeLabel = nearest.timeLabel,
        sunAltitudeDeg = lerp(a.sunAltitudeDeg, b.sunAltitudeDeg),
        sunAzimuthDeg = lerp(a.sunAzimuthDeg, b.sunAzimuthDeg),
        moonAltitudeMariDeg = lerp(a.moonAltitudeMariDeg, b.moonAltitudeMariDeg),
        moonAzimuthDeg = lerp(a.moonAzimuthDeg, b.moonAzimuthDeg),
        elongationDeg = lerp(a.elongationDeg, b.elongationDeg)
    )
}
