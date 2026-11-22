package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import com.example.domain.falak.MabimsHilalModel
import com.example.domain.falak.MabimsPrediction
import com.example.domain.falak.VerdictStatus
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale

@Composable
fun MabimsMathematicalModelCard(
    prediction: MabimsPrediction,
    modifier: Modifier = Modifier
) {
    var sandboxEnabled by remember { mutableStateOf(false) }
    var testAltDeg by remember(prediction) {
        mutableFloatStateOf(prediction.moonAltitudeMariDeg.toFloat().coerceIn(-2f, 10f))
    }
    var testElongDeg by remember(prediction) {
        mutableFloatStateOf(prediction.elongationTopocentricDeg.toFloat().coerceIn(0f, 14f))
    }

    val activeAlt = if (sandboxEnabled) testAltDeg.toDouble() else prediction.moonAltitudeMariDeg
    val activeElong = if (sandboxEnabled) testElongDeg.toDouble() else prediction.elongationTopocentricDeg
    val activeAssessment = if (sandboxEnabled) {
        MabimsHilalModel.evaluateCriteriaFromMeasurements(
            moonAltitudeMariDeg = activeAlt,
            elongationTopocentricDeg = activeElong,
            ijtimaBeforeSunset = true,
            moonAgeHours = prediction.moonAgeHours.coerceAtLeast(10.0)
        )
    } else {
        prediction.assessment
    }

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
                        imageVector = Icons.Outlined.Functions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text(
                            text = "Model Matematis MABIMS",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Prediksi Visibilitas Hilal saat Ghurub (${prediction.sunsetLocalLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = { sandboxEnabled = !sandboxEnabled },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_mabims_sandbox_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (sandboxEnabled) "Ephemeris" else "Uji Model")
                }
            }

            VerdictBadge(
                status = activeAssessment.status,
                label = "${activeAssessment.predictionLabel} (${String.format(Locale.US, "%.0f", activeAssessment.visibilityScorePercent)}%)",
                modifier = Modifier.testTag("mabims_model_verdict_badge")
            )

            Text(
                text = activeAssessment.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 2D MABIMS Decision Plane Plot (Elongation X vs Altitude Y)
            MabimsDecisionPlaneCanvas(
                altitudeDeg = activeAlt,
                elongationDeg = activeElong,
                status = activeAssessment.status,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .semantics {
                        contentDescription = "Grafik bidang keputusan Neo-MABIMS tinggi hilal terhadap elongasi"
                    }
                    .testTag("mabims_decision_plane_canvas")
            )

            // Interactive Sandbox Sliders
            AnimatedVisibility(visible = sandboxEnabled) {
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
                            Text(
                                text = "Simulasi Parameter Input Model",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FilledTonalButton(
                                onClick = {
                                    testAltDeg = prediction.moonAltitudeMariDeg.toFloat().coerceIn(-2f, 10f)
                                    testElongDeg = prediction.elongationTopocentricDeg.toFloat().coerceIn(0f, 14f)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("reset_mabims_sandbox_button")
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }

                        Text(
                            text = String.format(Locale.US, "Tinggi Hilal Mar'i (h): %.2f° (Ambang ≥ 3.00°)", testAltDeg),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = testAltDeg,
                            onValueChange = { testAltDeg = it },
                            valueRange = -2f..10f,
                            modifier = Modifier.testTag("mabims_sandbox_alt_slider")
                        )

                        Text(
                            text = String.format(Locale.US, "Elongasi Toposentris (ψ): %.2f° (Ambang ≥ 6.40°)", testElongDeg),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = testElongDeg,
                            onValueChange = { testElongDeg = it },
                            valueRange = 0f..14f,
                            modifier = Modifier.testTag("mabims_sandbox_elong_slider")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Mathematical derivation breakdown
            Text(
                text = "Derivasi Matematis saat Ghurub (JD ${String.format(Locale.US, "%.4f", prediction.sunsetJd)})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

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
                    FormulaRow("1. Tinggi Haqiqi (h₀)", String.format(Locale.US, "%+.3f°", prediction.moonAltitudeHaqiqiDeg))
                    FormulaRow("2. Koreksi Paralaks (-π·cos h₀)", String.format(Locale.US, "%+.3f°", prediction.parallaxCorrectionDeg))
                    FormulaRow(
                        "3. Refraksi Bennett + Dip",
                        String.format(Locale.US, "+%.3f° + %.3f°", prediction.refractionCorrectionDeg, prediction.horizonDipDeg)
                    )
                    FormulaRow("4. Tinggi Mar'i (h = h₀+Δπ+R+Dip)", String.format(Locale.US, "%+.3f°", prediction.moonAltitudeMariDeg))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FormulaRow("5. Elongasi Geosentris (ψ_geo)", String.format(Locale.US, "%.3f°", prediction.elongationGeocentricDeg))
                    FormulaRow("6. Elongasi Toposentris (ψ_topo)", String.format(Locale.US, "%.3f°", prediction.elongationTopocentricDeg))
                    FormulaRow(
                        "7. Margin Neo-MABIMS (Δh / Δψ)",
                        String.format(
                            Locale.US,
                            "%+.2f° / %+.2f°",
                            activeAssessment.altitudeMarginNeoDeg,
                            activeAssessment.elongationMarginNeoDeg
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FormulaRow(label: String, value: String) {
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
private fun MabimsDecisionPlaneCanvas(
    altitudeDeg: Double,
    elongationDeg: Double,
    status: VerdictStatus,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val emerald = MaterialTheme.colorScheme.secondary
    val amber = MaterialTheme.colorScheme.tertiary
    val coral = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bgSurface = MaterialTheme.colorScheme.surface

    val pointColor = when (status) {
        VerdictStatus.MEMENUHI -> emerald
        VerdictStatus.MARGINAL -> amber
        VerdictStatus.TIDAK_MEMENUHI -> coral
    }

    Canvas(modifier = modifier) {
        val padLeft = 42f
        val padRight = 14f
        val padTop = 14f
        val padBottom = 28f
        val plotW = (size.width - padLeft - padRight).coerceAtLeast(20f)
        val plotH = (size.height - padTop - padBottom).coerceAtLeast(20f)

        val minElong = 0.0
        val maxElong = 14.0
        val minAlt = -2.0
        val maxAlt = 10.0

        fun xOf(elong: Double): Float {
            val f = ((elong.coerceIn(minElong, maxElong) - minElong) / (maxElong - minElong)).toFloat()
            return padLeft + f * plotW
        }

        fun yOf(alt: Double): Float {
            val f = ((alt.coerceIn(minAlt, maxAlt) - minAlt) / (maxAlt - minAlt)).toFloat()
            return padTop + plotH * (1f - f)
        }

        // Background
        drawRect(
            color = bgSurface.copy(alpha = 0.7f),
            topLeft = Offset(padLeft, padTop),
            size = Size(plotW, plotH)
        )

        // Neo-MABIMS eligible region: Elong >= 6.4, Alt >= 3.0
        val neoX = xOf(MabimsHilalModel.NEO_MABIMS_MIN_ELONGATION_DEG)
        val neoY = yOf(MabimsHilalModel.NEO_MABIMS_MIN_ALTITUDE_DEG)
        drawRect(
            color = emerald.copy(alpha = 0.18f),
            topLeft = Offset(neoX, padTop),
            size = Size((padLeft + plotW) - neoX, neoY - padTop)
        )
        drawRect(
            color = emerald.copy(alpha = 0.75f),
            topLeft = Offset(neoX, padTop),
            size = Size((padLeft + plotW) - neoX, neoY - padTop),
            style = Stroke(width = 2f)
        )

        // Classic MABIMS region outline: Elong >= 3.0, Alt >= 2.0
        val clsX = xOf(MabimsHilalModel.CLASSIC_MABIMS_MIN_ELONGATION_DEG)
        val clsY = yOf(MabimsHilalModel.CLASSIC_MABIMS_MIN_ALTITUDE_DEG)
        drawRect(
            color = amber.copy(alpha = 0.45f),
            topLeft = Offset(clsX, padTop),
            size = Size((padLeft + plotW) - clsX, clsY - padTop),
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        )

        // 0-degree horizon line
        val zeroY = yOf(0.0)
        drawLine(
            color = outline.copy(alpha = 0.75f),
            start = Offset(padLeft, zeroY),
            end = Offset(padLeft + plotW, zeroY),
            strokeWidth = 1.5f
        )

        // Axes & labels
        val labelStyle = TextStyle(
            color = labelColor,
            fontSize = 9.sp,
            fontFamily = JetBrainsMonoFamily
        )
        listOf(0.0, 3.0, 6.0, 9.0).forEach { altTick ->
            val y = yOf(altTick)
            val measured = textMeasurer.measure("${altTick.toInt()}°", style = labelStyle)
            drawText(measured, topLeft = Offset(4f, y - measured.size.height / 2f))
        }
        listOf(0.0, 3.0, 6.4, 10.0, 14.0).forEach { elTick ->
            val x = xOf(elTick)
            val txt = if (elTick == 6.4) "6.4°" else "${elTick.toInt()}°"
            val measured = textMeasurer.measure(txt, style = labelStyle)
            drawText(
                measured,
                topLeft = Offset(
                    (x - measured.size.width / 2f).coerceIn(padLeft, size.width - measured.size.width),
                    padTop + plotH + 6f
                )
            )
        }

        // Zone label inside green quadrant
        drawText(
            textMeasurer = textMeasurer,
            text = "ZONA NEO-MABIMS\n(h≥3°, ψ≥6.4°)",
            topLeft = Offset(neoX + 8f, padTop + 8f),
            style = labelStyle.copy(color = emerald, fontWeight = FontWeight.Bold)
        )

        // Plotted Hilal point + crosshairs
        val ptX = xOf(elongationDeg)
        val ptY = yOf(altitudeDeg)
        drawLine(
            color = pointColor.copy(alpha = 0.5f),
            start = Offset(ptX, padTop),
            end = Offset(ptX, padTop + plotH),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
        drawLine(
            color = pointColor.copy(alpha = 0.5f),
            start = Offset(padLeft, ptY),
            end = Offset(padLeft + plotW, ptY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
        drawCircle(
            color = pointColor.copy(alpha = 0.28f),
            radius = 13f,
            center = Offset(ptX, ptY)
        )
        drawCircle(
            color = pointColor,
            radius = 6f,
            center = Offset(ptX, ptY)
        )
    }
}
