package com.example.domain.falak

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ReportScope(val label: String) {
    COMPLETE("Lengkap (Hilal & Istiwak)"),
    HILAL_ONLY("Prediksi Hilal & MABIMS"),
    ISTIWAK_ONLY("Waktu Istiwak & Sholat")
}

data class ReportRow(
    val label: String,
    val value: String
)

data class ReportSection(
    val title: String,
    val subtitle: String? = null,
    val highlightText: String? = null,
    val highlightStatus: VerdictStatus? = null,
    val rows: List<ReportRow>,
    val note: String? = null
)

data class FalakReportDocument(
    val title: String,
    val subtitle: String,
    val generatedAtLabel: String,
    val markazSummary: String,
    val scope: ReportScope,
    val sections: List<ReportSection>
)

data class FalakReportInput(
    val markaz: MarkazLocation,
    val selectedCriteria: RukyatCriteria,
    val hilalResult: HilalResult?,
    val mabimsPrediction: MabimsPrediction?,
    val lunarPhaseData: LunarPhaseData?,
    val trueNoonResult: TrueNoonIstiwakResult?,
    val istiwakResult: IstiwakAndPrayerResult?,
    val scope: ReportScope = ReportScope.COMPLETE,
    val generatedAtMillis: Long = System.currentTimeMillis()
)

object FalakReportGenerator {

    fun suggestedFileName(input: FalakReportInput): String {
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).apply {
            timeZone = tzFromOffset(input.markaz.timezoneOffsetHours)
        }.format(Date(input.generatedAtMillis))

        val markazSlug = input.markaz.name
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "Markaz" }

        val periodSlug = when (input.scope) {
            ReportScope.ISTIWAK_ONLY -> "Istiwak"
            ReportScope.HILAL_ONLY -> {
                val h = input.hilalResult
                if (h != null) "Hilal_${h.hijriMonthName.replace("[^A-Za-z0-9]".toRegex(), "")}_${h.hijriYear}H"
                else "Hilal"
            }
            ReportScope.COMPLETE -> {
                val h = input.hilalResult
                if (h != null) "Falak_${h.hijriMonthName.replace("[^A-Za-z0-9]".toRegex(), "")}_${h.hijriYear}H"
                else "Falak"
            }
        }
        return "HilalChecker_${periodSlug}_${markazSlug}_$dateStamp"
    }

    fun buildStructuredReport(input: FalakReportInput): FalakReportDocument {
        val tz = tzFromOffset(input.markaz.timezoneOffsetHours)
        val generatedFmt = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.forLanguageTag("id-ID")).apply {
            timeZone = tz
        }
        val generatedAt = "${generatedFmt.format(Date(input.generatedAtMillis))} ${input.markaz.timezoneLabel}"
        val markazCoords = String.format(
            Locale.US,
            "%.4f°, %.4f° | Elevasi %.0f m | %s (UTC%+.0f)",
            input.markaz.latitude,
            input.markaz.longitude,
            input.markaz.elevationMeters,
            input.markaz.timezoneLabel,
            input.markaz.timezoneOffsetHours
        )

        val sections = mutableListOf<ReportSection>()

        // 1. Markaz & Observer Info
        sections += ReportSection(
            title = "1. Data Markaz & Koordinat Pengamat",
            rows = listOf(
                ReportRow("Nama Markaz / Lokasi", input.markaz.name),
                ReportRow("Wilayah", input.markaz.region),
                ReportRow(
                    "Lintang (Latitude)",
                    "${FalakEphemerisEngine.formatDms(input.markaz.latitude, signed = true)} (${String.format(Locale.US, "%.5f°", input.markaz.latitude)})"
                ),
                ReportRow(
                    "Bujur (Longitude)",
                    "${FalakEphemerisEngine.formatDms(input.markaz.longitude, signed = true)} (${String.format(Locale.US, "%.5f°", input.markaz.longitude)})"
                ),
                ReportRow("Elevasi / Tinggi Tempat", String.format(Locale.US, "%.0f meter dpl", input.markaz.elevationMeters)),
                ReportRow("Zona Waktu", "${input.markaz.timezoneLabel} (Meridian ${String.format(Locale.US, "%.0f°", input.markaz.timezoneOffsetHours * 15.0)})")
            )
        )

        // 2. Hilal & MABIMS Prediction
        if (input.scope == ReportScope.COMPLETE || input.scope == ReportScope.HILAL_ONLY) {
            val hilal = input.hilalResult
            if (hilal != null) {
                val activeVerdict = hilal.verdicts[input.selectedCriteria] ?: hilal.verdicts.values.first()
                val moonAgeStr = run {
                    val sign = if (hilal.moonAgeHours < 0) "-" else ""
                    val absAge = kotlin.math.abs(hilal.moonAgeHours)
                    val h = absAge.toInt()
                    val m = ((absAge - h) * 60).toInt()
                    "$sign${h} jam ${m} menit"
                }

                sections += ReportSection(
                    title = "2. Prediksi Awal ${hilal.hijriMonthName} ${hilal.hijriYear} H",
                    subtitle = "Rukyat Sore: ${hilal.observationDayLabel}, ${hilal.observationDateLabel}",
                    highlightText = "${input.selectedCriteria.title}: ${activeVerdict.summary}",
                    highlightStatus = activeVerdict.status,
                    rows = listOf(
                        ReportRow("Waktu Ijtima' (Konjungsi)", hilal.ijtimaLocalLabel),
                        ReportRow("Terbenam Matahari (Ghurub)", hilal.sunsetLocalLabel),
                        ReportRow("Terbenam Bulan", hilal.moonsetLocalLabel),
                        ReportRow("Lama Hilal di Atas Ufuk (Mukuts)", String.format(Locale.US, "%.1f menit", hilal.mukutsMinutes)),
                        ReportRow("Tinggi Hilal Mar'i", FalakEphemerisEngine.formatDms(hilal.moonAltitudeMariDeg, signed = true)),
                        ReportRow("Tinggi Hilal Haqiqi", FalakEphemerisEngine.formatDms(hilal.moonAltitudeHaqiqiDeg, signed = true)),
                        ReportRow("Elongasi Toposentris", FalakEphemerisEngine.formatDms(hilal.elongationTopoDeg)),
                        ReportRow("Elongasi Geosentris", FalakEphemerisEngine.formatDms(hilal.elongationGeoDeg)),
                        ReportRow("Umur Bulan saat Ghurub", moonAgeStr),
                        ReportRow("Azimuth Matahari saat Ghurub", FalakEphemerisEngine.formatDms(hilal.sunAzimuthDeg)),
                        ReportRow("Azimuth Hilal saat Ghurub", FalakEphemerisEngine.formatDms(hilal.moonAzimuthDeg)),
                        ReportRow("Beda Azimuth (Bulan - Matahari)", FalakEphemerisEngine.formatDms(hilal.azimuthDifferenceDeg, signed = true)),
                        ReportRow("Lebar Sabit & Iluminasi", String.format(Locale.US, "%.3f arcmin | %.2f%%", hilal.crescentWidthArcmin, hilal.illuminationPercent))
                    ),
                    note = activeVerdict.detail
                )

                val verdictRows = RukyatCriteria.entries.mapNotNull { crit ->
                    hilal.verdicts[crit]?.let { v ->
                        ReportRow(crit.title, "${v.summary} — ${v.detail}")
                    }
                }
                if (verdictRows.isNotEmpty()) {
                    sections += ReportSection(
                        title = "3. Perbandingan Multi-Kriteria Rukyat",
                        rows = verdictRows
                    )
                }
            }

            val mabims = input.mabimsPrediction
            if (mabims != null) {
                val assess = mabims.assessment
                sections += ReportSection(
                    title = "4. Model Matematis MABIMS (Saat Ghurub)",
                    subtitle = "Evaluasi Neo-MABIMS (Tinggi ≥ 3.00° & Elongasi ≥ 6.40°) & MABIMS Klasik",
                    highlightText = assess.predictionLabel,
                    highlightStatus = assess.status,
                    rows = listOf(
                        ReportRow("Tinggi Haqiqi Bulan", String.format(Locale.US, "%+.4f°", mabims.moonAltitudeHaqiqiDeg)),
                        ReportRow("Koreksi Paralaks Toposentris", String.format(Locale.US, "%+.4f°", mabims.parallaxCorrectionDeg)),
                        ReportRow("Koreksi Refraksi Atmosfer", String.format(Locale.US, "%+.4f°", mabims.refractionCorrectionDeg)),
                        ReportRow("Kerendahan Ufuk (Horizon Dip)", String.format(Locale.US, "%+.4f°", mabims.horizonDipDeg)),
                        ReportRow("Tinggi Mar'i Akhir (h_mar'i)", String.format(Locale.US, "%+.4f°", mabims.moonAltitudeMariDeg)),
                        ReportRow("Elongasi Toposentris / Geosentris", String.format(Locale.US, "%.4f° / %.4f°", mabims.elongationTopocentricDeg, mabims.elongationGeocentricDeg)),
                        ReportRow("Margin Tinggi Neo-MABIMS", String.format(Locale.US, "%+.2f° (ambang +3.00°)", assess.altitudeMarginNeoDeg)),
                        ReportRow("Margin Elongasi Neo-MABIMS", String.format(Locale.US, "%+.2f° (ambang 6.40°)", assess.elongationMarginNeoDeg)),
                        ReportRow("Skor Indeks Visibilitas", String.format(Locale.US, "%.0f%%", assess.visibilityScorePercent)),
                        ReportRow("Status Neo-MABIMS / Klasik", "${if (assess.meetsNeoMabims) "Memenuhi" else "Belum"} / ${if (assess.meetsClassicMabims) "Memenuhi" else "Belum"}")
                    ),
                    note = assess.explanation
                )
            }

            val lunar = input.lunarPhaseData
            if (lunar != null) {
                sections += ReportSection(
                    title = "5. Data Fase Bulan (Siklus Sinodis)",
                    subtitle = "${lunar.dateLabel} | ${lunar.hijriDateLabel}",
                    rows = listOf(
                        ReportRow("Fase Bulan", "${lunar.phaseNameIndonesian} (${lunar.phaseNameArabic})"),
                        ReportRow("Umur Siklus Bulan", String.format(Locale.US, "%.2f / %.2f hari", lunar.lunarAgeDays, lunar.synodicLengthDays)),
                        ReportRow("Iluminasi Piringan", String.format(Locale.US, "%.1f%%", lunar.illuminationPercent)),
                        ReportRow("Jarak Bumi–Bulan", String.format(Locale.US, "%,.0f km (%.2f arcmin)", lunar.earthMoonDistanceKm, lunar.angularDiameterArcmin)),
                        ReportRow("Bulan Baru / Purnama Berikutnya", "${lunar.nextNewMoonLabel} / ${lunar.nextFullMoonLabel}")
                    )
                )
            }
        }

        // 3. Waktu Istiwak & Prayer Times
        if (input.scope == ReportScope.COMPLETE || input.scope == ReportScope.ISTIWAK_ONLY) {
            val trueNoon = input.trueNoonResult
            if (trueNoon != null) {
                sections += ReportSection(
                    title = "Kalkulator Waktu Istiwak (Tengah Hari Hakiki)",
                    subtitle = "${trueNoon.dateLabel} | ${trueNoon.hijriDateLabel}",
                    highlightText = "Istiwak / Zawal: ${trueNoon.trueNoonLocalLabel} (${trueNoon.trueNoonUtcLabel})",
                    highlightStatus = VerdictStatus.MEMENUHI,
                    rows = listOf(
                        ReportRow("Waktu Istiwak (Jam Lokal)", trueNoon.trueNoonLocalLabel),
                        ReportRow("Waktu Istiwak (UTC)", trueNoon.trueNoonUtcLabel),
                        ReportRow("Dzuhur (+2 Menit Ihtiyat)", trueNoon.dzuhurIhtiyatLabel),
                        ReportRow("Bujur Lokasi vs Meridian Standar", String.format(Locale.US, "%.4f° vs %.0f°", trueNoon.markaz.longitude, trueNoon.standardMeridianDeg)),
                        ReportRow("Koreksi Bujur (Δλ × 4 mnt)", WaktuIstiwakCalculator.formatOffsetMinSec(trueNoon.longitudeCorrectionMinutes)),
                        ReportRow("Equation of Time (Ta'dil Waqt)", WaktuIstiwakCalculator.formatOffsetMinSec(trueNoon.equationOfTimeMinutes)),
                        ReportRow("Total Selisih (WIS - Jam Lokal)", WaktuIstiwakCalculator.formatOffsetMinSec(trueNoon.totalWisOffsetMinutes)),
                        ReportRow("Tinggi Matahari saat Istiwak", FalakEphemerisEngine.formatDms(trueNoon.solarNoonAltitudeDeg)),
                        ReportRow("Deklinasi Matahari (δ☉)", FalakEphemerisEngine.formatDms(trueNoon.solarDeclinationDeg, signed = true)),
                        ReportRow("Jarak Zenit saat Kulminasi", FalakEphemerisEngine.formatDms(trueNoon.zenithDistanceDeg)),
                        ReportRow("Verifikasi WIS saat Zawal", trueNoon.wisVerificationLabel),
                        ReportRow("Arah Bayangan Tengah Hari", trueNoon.shadowDirectionAtNoon)
                    )
                )
            }

            val istiwak = input.istiwakResult
            if (istiwak != null) {
                val prayerRows = mutableListOf(
                    ReportRow("Jam Istiwak Berjalan (WIS)", istiwak.waktuIstiwakLabel),
                    ReportRow("Jam Ghurubiyah", istiwak.waktuGhurubiyahLabel),
                    ReportRow("Waktu Lokal Saat Ini", istiwak.localStandardTimeLabel),
                    ReportRow("Kalender & Pasaran", "${istiwak.hijriDateLabel} • ${istiwak.pasaranJawa}"),
                    ReportRow("Azimuth & Jarak Kiblat", String.format(Locale.US, "%.2f° (%s) | %.1f km", istiwak.qiblaAzimuthDeg, FalakEphemerisEngine.formatDms(istiwak.qiblaAzimuthDeg), istiwak.qiblaDistanceKm)),
                    ReportRow("Roshdul Qiblah", istiwak.roshdulQiblahSummary)
                )
                istiwak.prayerTimes.forEach { entry ->
                    prayerRows += ReportRow(
                        "Jadwal ${entry.name} (${entry.arabicSubtitle})",
                        "${entry.timeLabel} ${istiwak.markaz.timezoneLabel}"
                    )
                }
                sections += ReportSection(
                    title = "Jam Istiwak, Kiblat & Jadwal Sholat",
                    subtitle = istiwak.gregorianDateLabel,
                    rows = prayerRows
                )
            }
        }

        val title = when (input.scope) {
            ReportScope.COMPLETE -> "LAPORAN HISAB HILAL & WAKTU ISTIWAK"
            ReportScope.HILAL_ONLY -> "LAPORAN PREDIKSI HILAL & KRITERIA MABIMS"
            ReportScope.ISTIWAK_ONLY -> "LAPORAN WAKTU ISTIWAK & JADWAL SHOLAT"
        }

        return FalakReportDocument(
            title = title,
            subtitle = "Hilal Checker — Ephemeris Jean Meeus & Ilmu Falak Nusantara",
            generatedAtLabel = generatedAt,
            markazSummary = "${input.markaz.name} (${input.markaz.region}) • $markazCoords",
            scope = input.scope,
            sections = sections
        )
    }

    fun buildTextReport(input: FalakReportInput): String {
        val doc = buildStructuredReport(input)
        val sep = "================================================================"
        val subSep = "----------------------------------------------------------------"
        return buildString {
            appendLine(sep)
            appendLine(doc.title)
            appendLine(doc.subtitle)
            appendLine(sep)
            appendLine("Dibuat pada : ${doc.generatedAtLabel}")
            appendLine("Markaz      : ${doc.markazSummary}")
            appendLine("Cakupan     : ${doc.scope.label}")
            appendLine()

            doc.sections.forEach { section ->
                appendLine(section.title)
                if (!section.subtitle.isNullOrBlank()) {
                    appendLine("  ${section.subtitle}")
                }
                appendLine(subSep)
                if (!section.highlightText.isNullOrBlank()) {
                    appendLine("  [RINGKASAN] ${section.highlightText}")
                }
                val maxLabelLen = (section.rows.maxOfOrNull { it.label.length } ?: 24).coerceAtMost(36)
                section.rows.forEach { row ->
                    val padded = row.label.padEnd(maxLabelLen)
                    appendLine("  $padded : ${row.value}")
                }
                if (!section.note.isNullOrBlank()) {
                    appendLine("  Catatan: ${section.note}")
                }
                appendLine()
            }

            appendLine(sep)
            appendLine("Catatan: Perhitungan menggunakan algoritma astronomis Jean Meeus")
            appendLine("dan kaidah Ilmu Falak (Neo-MABIMS 2021, Wujudul Hilal, Istiwak WIS).")
            appendLine(sep)
        }
    }

    private fun tzFromOffset(offsetHours: Double): TimeZone {
        val totalMinutes = kotlin.math.round(offsetHours * 60.0).toInt()
        val sign = if (totalMinutes >= 0) "+" else "-"
        val absM = kotlin.math.abs(totalMinutes)
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", sign, absM / 60, absM % 60))
    }
}
