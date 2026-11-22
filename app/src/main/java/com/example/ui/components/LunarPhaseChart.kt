package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.falak.LunarPhaseData
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun LunarPhaseVisualizationCard(
    lunarPhase: LunarPhaseData,
    dayOffset: Int,
    onDayOffsetChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedCycleFraction by animateFloatAsState(
        targetValue = lunarPhase.cycleFraction.toFloat(),
        animationSpec = spring(stiffness = 300f),
        label = "cycleFraction"
    )
    val animatedIllum by animateFloatAsState(
        targetValue = (lunarPhase.illuminationPercent / 100.0).toFloat(),
        animationSpec = spring(stiffness = 300f),
        label = "illumination"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                        imageVector = Icons.Outlined.NightsStay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Visualisasi Fase Bulan",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${lunarPhase.dateLabel} • ${lunarPhase.hijriDateLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (dayOffset == 0) "Hari Ini" else if (dayOffset > 0) "+$dayOffset h" else "$dayOffset h",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Main Moon Phase Disc + Synodic Orbit Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 165.dp)
                        .weight(1f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LunarDiscWithOrbitCanvas(
                        cycleFraction = animatedCycleFraction,
                        illuminationFraction = animatedIllum,
                        isWaxing = lunarPhase.isWaxing,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                contentDescription = "Diagram fase bulan ${lunarPhase.phaseNameIndonesian}"
                            }
                            .testTag("lunar_phase_disc_canvas")
                    )
                }

                Column(
                    modifier = Modifier.weight(1.15f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = lunarPhase.phaseNameIndonesian,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("lunar_phase_name")
                    )
                    Text(
                        text = lunarPhase.phaseNameArabic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    LunarCompactStat(
                        label = "Iluminasi",
                        value = String.format(Locale.US, "%.1f %%", lunarPhase.illuminationPercent)
                    )
                    LunarCompactStat(
                        label = "Umur Bulan",
                        value = String.format(Locale.US, "%.1f / %.1f hr", lunarPhase.lunarAgeDays, lunarPhase.synodicLengthDays)
                    )
                    LunarCompactStat(
                        label = "Jarak Bumi–Bulan",
                        value = String.format(Locale.US, "%,.0f km", lunarPhase.earthMoonDistanceKm)
                    )
                }
            }

            // Synodic Illumination Curve Chart
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Kurva Siklus Sinodis (29.5 Hari)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(Locale.US, "Ø %.1f'", lunarPhase.angularDiameterArcmin),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SynodicIlluminationChart(
                    lunarPhase = lunarPhase,
                    cycleFraction = animatedCycleFraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("lunar_synodic_curve_canvas")
                )
            }

            // Day Scrubber (-15 to +15 days)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Slider(
                        value = dayOffset.toFloat(),
                        onValueChange = { onDayOffsetChange(it.roundToInt()) },
                        valueRange = -15f..15f,
                        steps = 29,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lunar_day_offset_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    if (dayOffset != 0) {
                        FilledTonalButton(
                            onClick = { onDayOffsetChange(0) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lunar_reset_today_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Kembali ke hari ini",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hari Ini")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "-15 hari",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Geser tanggal siklus bulan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "+15 hari",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Upcoming New Moon & Full Moon countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Ijtima' Berikutnya",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f hari lagi", lunarPhase.daysToNextNewMoon),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = lunarPhase.nextNewMoonLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Purnama Berikutnya",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f hari lagi", lunarPhase.daysToNextFullMoon),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = lunarPhase.nextFullMoonLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LunarCompactStat(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LunarDiscWithOrbitCanvas(
    cycleFraction: Float,
    illuminationFraction: Float,
    isWaxing: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbitR = size.minDimension / 2f - 10f
        val moonR = orbitR * 0.72f

        // Synodic Orbit Track
        drawCircle(
            color = outlineColor.copy(alpha = 0.35f),
            radius = orbitR,
            center = center,
            style = Stroke(width = 3f)
        )

        // Progress arc along synodic orbit from New Moon (top) clockwise
        drawArc(
            color = primaryColor.copy(alpha = 0.7f),
            startAngle = -90f,
            sweepAngle = cycleFraction * 360f,
            useCenter = false,
            topLeft = Offset(center.x - orbitR, center.y - orbitR),
            size = androidx.compose.ui.geometry.Size(orbitR * 2f, orbitR * 2f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        // 4 Quarter milestone ticks on orbit ring
        listOf(0f, 0.25f, 0.5f, 0.75f).forEach { q ->
            val rad = (q * 2f * PI - PI / 2.0)
            val tickPos = Offset(
                center.x + (orbitR * cos(rad)).toFloat(),
                center.y + (orbitR * sin(rad)).toFloat()
            )
            drawCircle(
                color = if (q == 0.5f) secondaryColor else outlineColor,
                radius = 3.5f,
                center = tickPos
            )
        }

        // Orbiting marker dot
        val markerRad = (cycleFraction * 2.0 * PI - PI / 2.0)
        val markerPos = Offset(
            center.x + (orbitR * cos(markerRad)).toFloat(),
            center.y + (orbitR * sin(markerRad)).toFloat()
        )
        drawCircle(color = primaryColor, radius = 6.5f, center = markerPos)

        // Ambient lunar glow proportional to illumination
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFDF0C2).copy(alpha = 0.25f * illuminationFraction),
                    Color.Transparent
                ),
                center = center,
                radius = moonR * 1.35f
            ),
            radius = moonR * 1.35f,
            center = center
        )

        // Dark unlit Moon sphere
        drawCircle(
            color = Color(0xFF141E38),
            radius = moonR,
            center = center
        )
        drawCircle(
            color = Color(0xFF283A63),
            radius = moonR,
            center = center,
            style = Stroke(width = 1.5f)
        )

        // Subtle lunar mare texture spots on dark sphere
        drawCircle(
            color = Color(0x18FFFFFF),
            radius = moonR * 0.22f,
            center = Offset(center.x - moonR * 0.26f, center.y - moonR * 0.18f)
        )
        drawCircle(
            color = Color(0x14FFFFFF),
            radius = moonR * 0.16f,
            center = Offset(center.x + moonR * 0.22f, center.y + moonR * 0.14f)
        )

        // Exact geometric lit lunar phase using circular limb + elliptical terminator
        val illum = illuminationFraction.coerceIn(0f, 1f)
        if (illum > 0.004f) {
            val litPath = Path()
            val outerRect = Rect(
                center.x - moonR,
                center.y - moonR,
                center.x + moonR,
                center.y + moonR
            )

            if (illum >= 0.996f) {
                litPath.addOval(outerRect)
            } else {
                val k = 2f * illum - 1f // -1 (New) -> 0 (Quarter) -> +1 (Full)
                val rx = (abs(k) * moonR).coerceAtLeast(0.5f)
                val innerRect = Rect(
                    center.x - rx,
                    center.y - moonR,
                    center.x + rx,
                    center.y + moonR
                )

                if (isWaxing) {
                    litPath.arcTo(outerRect, -90f, 180f, forceMoveTo = true)
                    val sweep = if (k < 0f) -180f else 180f
                    litPath.arcTo(innerRect, 90f, sweep, forceMoveTo = false)
                } else {
                    litPath.arcTo(outerRect, 90f, 180f, forceMoveTo = true)
                    val sweep = if (k >= 0f) 180f else -180f
                    litPath.arcTo(innerRect, -90f, sweep, forceMoveTo = false)
                }
                litPath.close()
            }

            drawPath(
                path = litPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF8DC), Color(0xFFF3C86A)),
                    center = Offset(center.x - moonR * 0.15f, center.y - moonR * 0.15f),
                    radius = moonR * 1.4f
                )
            )
        }
    }
}

@Composable
private fun SynodicIlluminationChart(
    lunarPhase: LunarPhaseData,
    cycleFraction: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val points = lunarPhase.cycleCurvePoints
        if (points.size < 2) return@Canvas

        val padLeft = 28f
        val padRight = 10f
        val padTop = 10f
        val padBottom = 22f
        val chartW = (size.width - padLeft - padRight).coerceAtLeast(10f)
        val chartH = (size.height - padTop - padBottom).coerceAtLeast(10f)

        // Horizontal gridlines (0%, 50%, 100%)
        val textStyle = TextStyle(
            color = labelColor,
            fontSize = 9.sp,
            fontFamily = JetBrainsMonoFamily
        )
        listOf(0f, 50f, 100f).forEach { pct ->
            val y = padTop + chartH * (1f - pct / 100f)
            drawLine(
                color = outlineColor.copy(alpha = 0.35f),
                start = Offset(padLeft, y),
                end = Offset(padLeft + chartW, y),
                strokeWidth = 1f,
                pathEffect = if (pct == 50f) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "${pct.toInt()}%",
                topLeft = Offset(0f, y - 6f),
                style = textStyle
            )
        }

        // Build smooth curve & area fill path
        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { idx, pt ->
            val x = padLeft + pt.cycleFraction * chartW
            val y = padTop + chartH * (1f - pt.illuminationPercent / 100f)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, padTop + chartH)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        val lastX = padLeft + points.last().cycleFraction * chartW
        fillPath.lineTo(lastX, padTop + chartH)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                startY = padTop,
                endY = padTop + chartH
            )
        )
        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )

        // X-axis milestone labels
        val bottomY = padTop + chartH + 4f
        listOf(
            0f to "Ijtima'",
            0.25f to "Perbani I",
            0.5f to "Purnama",
            0.75f to "Perbani II",
            1f to "Ijtima'"
        ).forEach { (f, lbl) ->
            val measured = textMeasurer.measure(lbl, style = textStyle)
            val x = (padLeft + f * chartW - measured.size.width / 2f)
                .coerceIn(padLeft, size.width - measured.size.width)
            drawText(measured, topLeft = Offset(x, bottomY))
        }

        // Current position vertical cursor & glowing dot
        val curX = padLeft + cycleFraction.coerceIn(0f, 1f) * chartW
        val curY = padTop + chartH * (1f - (lunarPhase.illuminationPercent.toFloat() / 100f))
        drawLine(
            color = secondaryColor.copy(alpha = 0.7f),
            start = Offset(curX, padTop),
            end = Offset(curX, padTop + chartH),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.3f),
            radius = 10f,
            center = Offset(curX, curY)
        )
        drawCircle(
            color = secondaryColor,
            radius = 5f,
            center = Offset(curX, curY)
        )
    }
}
