package com.example.domain.falak

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class MabimsCriteriaAssessment(
    val meetsNeoMabims: Boolean,
    val meetsClassicMabims: Boolean,
    val altitudeMarginNeoDeg: Double,
    val elongationMarginNeoDeg: Double,
    val visibilityScorePercent: Double,
    val status: VerdictStatus,
    val predictionLabel: String,
    val explanation: String
)

data class MabimsPrediction(
    val markaz: MarkazLocation,
    val observationDateLabel: String,
    val sunsetJd: Double,
    val sunsetLocalLabel: String,
    val sunAzimuthDeg: Double,
    val sunAltitudeMariDeg: Double,
    val moonAzimuthDeg: Double,
    val moonAltitudeHaqiqiDeg: Double,
    val parallaxCorrectionDeg: Double,
    val refractionCorrectionDeg: Double,
    val horizonDipDeg: Double,
    val moonAltitudeMariDeg: Double,
    val azimuthSeparationDeg: Double,
    val elongationGeocentricDeg: Double,
    val elongationTopocentricDeg: Double,
    val ijtimaBeforeSunset: Boolean,
    val moonAgeHours: Double,
    val assessment: MabimsCriteriaAssessment
)

/**
 * Dedicated Mathematical Model for predicting Hilal visibility at sunset (Ghurub)
 * using Neo-MABIMS (2021: Altitude >= 3°, Elongation >= 6.4°) and Classic MABIMS criteria.
 */
object MabimsHilalModel {

    const val NEO_MABIMS_MIN_ALTITUDE_DEG = 3.0
    const val NEO_MABIMS_MIN_ELONGATION_DEG = 6.4
    const val CLASSIC_MABIMS_MIN_ALTITUDE_DEG = 2.0
    const val CLASSIC_MABIMS_MIN_ELONGATION_DEG = 3.0
    const val CLASSIC_MABIMS_MIN_AGE_HOURS = 8.0

    private const val DEG2RAD = PI / 180.0
    private const val RAD2DEG = 180.0 / PI
    private const val SYNODIC_MONTH_DAYS = 29.530588861
    private const val NEW_MOON_EPOCH_JD = 2451550.09766
    private const val BASE_HIJRI_YEAR = 1446
    private const val BASE_HIJRI_MONTH = 1
    private const val BASE_LUNATION_K = 303

    /**
     * Predicts sunset Moon altitude, topocentric & geocentric elongation, and MABIMS visibility
     * for the 29th-day evening corresponding to the start of [hijriYear]/[hijriMonth].
     */
    fun predictForHijriMonth(
        hijriYear: Int,
        hijriMonth: Int,
        markaz: MarkazLocation
    ): MabimsPrediction {
        val k = BASE_LUNATION_K + (hijriYear - BASE_HIJRI_YEAR) * 12 + (hijriMonth - BASE_HIJRI_MONTH)
        val ijtimaJd = FalakEphemerisEngine.findIjtimaJdForLunation(k)
        val ijtimaMillis = FalakEphemerisEngine.jdToUnixMillis(ijtimaJd)
        val localOffsetMillis = (markaz.timezoneOffsetHours * 3_600_000.0).toLong()

        val localCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = ijtimaMillis + localOffsetMillis
        }
        return predictInternal(
            localYear = localCal.get(Calendar.YEAR),
            localMonth = localCal.get(Calendar.MONTH) + 1,
            localDay = localCal.get(Calendar.DAY_OF_MONTH),
            markaz = markaz,
            knownPrevIjtimaJd = ijtimaJd
        )
    }

    /**
     * Predicts sunset Moon altitude, elongation, and MABIMS visibility for any Gregorian calendar date.
     */
    fun predictForGregorianDate(
        localYear: Int,
        localMonth: Int,
        localDay: Int,
        markaz: MarkazLocation
    ): MabimsPrediction {
        return predictInternal(
            localYear = localYear,
            localMonth = localMonth,
            localDay = localDay,
            markaz = markaz,
            knownPrevIjtimaJd = null
        )
    }

    private fun predictInternal(
        localYear: Int,
        localMonth: Int,
        localDay: Int,
        markaz: MarkazLocation,
        knownPrevIjtimaJd: Double?
    ): MabimsPrediction {
        val sunsetJd = FalakEphemerisEngine.findSunsetJd(localYear, localMonth, localDay, markaz)
        val sunsetMillis = FalakEphemerisEngine.jdToUnixMillis(sunsetJd)

        val ijtimaJd = if (knownPrevIjtimaJd != null) {
            knownPrevIjtimaJd
        } else {
            var k = floor((sunsetJd - NEW_MOON_EPOCH_JD) / SYNODIC_MONTH_DAYS).toInt()
            var candidate = FalakEphemerisEngine.findIjtimaJdForLunation(k)
            if (candidate > sunsetJd) {
                candidate = FalakEphemerisEngine.findIjtimaJdForLunation(k - 1)
            }
            candidate
        }

        val ijtimaBeforeSunset = ijtimaJd < sunsetJd
        val moonAgeHours = (sunsetJd - ijtimaJd) * 24.0

        // Equatorial coordinates at sunset
        val sun = FalakEphemerisEngine.computeSunPosition(sunsetJd)
        val moon = FalakEphemerisEngine.computeMoonPosition(sunsetJd)

        // Horizontal topocentric coordinates at sunset
        val sunHoriz = FalakEphemerisEngine.toHorizontalTopocentric(
            jd = sunsetJd,
            raDeg = sun.raDeg,
            decDeg = sun.decDeg,
            parallaxDeg = 0.0024,
            latDeg = markaz.latitude,
            lonDeg = markaz.longitude,
            elevationMeters = markaz.elevationMeters
        )
        val moonHoriz = FalakEphemerisEngine.toHorizontalTopocentric(
            jd = sunsetJd,
            raDeg = moon.raDeg,
            decDeg = moon.decDeg,
            parallaxDeg = moon.parallaxDeg,
            latDeg = markaz.latitude,
            lonDeg = markaz.longitude,
            elevationMeters = markaz.elevationMeters
        )

        // Explicit decomposition of altitude corrections:
        // h_mari = h_haqiqi - parallax * cos(h_haqiqi) + refraction + dip
        val h0 = moonHoriz.altitudeHaqiqiDeg
        val parallaxCorr = -moon.parallaxDeg * cos(h0 * DEG2RAD)
        val topoUnrefracted = h0 + parallaxCorr
        val refractionCorr = FalakEphemerisEngine.atmosphericRefractionDeg(topoUnrefracted)
        val dipCorr = 1.76 * sqrt(markaz.elevationMeters.coerceAtLeast(0.0)) / 60.0

        // Geocentric Elongation: cos(psi_geo) = sin(decS)*sin(decM) + cos(decS)*cos(decM)*cos(raS - raM)
        val sRa = sun.raDeg * DEG2RAD
        val sDec = sun.decDeg * DEG2RAD
        val mRa = moon.raDeg * DEG2RAD
        val mDec = moon.decDeg * DEG2RAD
        val cosElongGeo = (sin(sDec) * sin(mDec) + cos(sDec) * cos(mDec) * cos(sRa - mRa))
            .coerceIn(-1.0, 1.0)
        val elongGeoDeg = acos(cosElongGeo) * RAD2DEG

        // Topocentric Elongation at sunset from horizontal coordinates (Alt_topo, Az)
        val sAltR = sunHoriz.altitudeHaqiqiDeg * DEG2RAD
        val mAltR = topoUnrefracted * DEG2RAD
        val dAzR = (moonHoriz.azimuthDeg - sunHoriz.azimuthDeg) * DEG2RAD
        val cosElongTopo = (sin(sAltR) * sin(mAltR) + cos(sAltR) * cos(mAltR) * cos(dAzR))
            .coerceIn(-1.0, 1.0)
        val elongTopoDeg = acos(cosElongTopo) * RAD2DEG

        val assessment = evaluateCriteriaFromMeasurements(
            moonAltitudeMariDeg = moonHoriz.altitudeMariDeg,
            elongationTopocentricDeg = elongTopoDeg,
            ijtimaBeforeSunset = ijtimaBeforeSunset,
            moonAgeHours = moonAgeHours
        )

        val tz = tzFromOffset(markaz.timezoneOffsetHours)
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).apply { timeZone = tz }
            .format(Date(sunsetMillis))
        val sunsetStr = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = tz }
            .format(Date(sunsetMillis)) + " ${markaz.timezoneLabel}"

        return MabimsPrediction(
            markaz = markaz,
            observationDateLabel = dateStr,
            sunsetJd = sunsetJd,
            sunsetLocalLabel = sunsetStr,
            sunAzimuthDeg = sunHoriz.azimuthDeg,
            sunAltitudeMariDeg = sunHoriz.altitudeMariDeg,
            moonAzimuthDeg = moonHoriz.azimuthDeg,
            moonAltitudeHaqiqiDeg = h0,
            parallaxCorrectionDeg = parallaxCorr,
            refractionCorrectionDeg = refractionCorr,
            horizonDipDeg = dipCorr,
            moonAltitudeMariDeg = moonHoriz.altitudeMariDeg,
            azimuthSeparationDeg = moonHoriz.azimuthDeg - sunHoriz.azimuthDeg,
            elongationGeocentricDeg = elongGeoDeg,
            elongationTopocentricDeg = elongTopoDeg,
            ijtimaBeforeSunset = ijtimaBeforeSunset,
            moonAgeHours = moonAgeHours,
            assessment = assessment
        )
    }

    /**
     * Pure mathematical MABIMS evaluator from Moon altitude & elongation at sunset.
     */
    fun evaluateCriteriaFromMeasurements(
        moonAltitudeMariDeg: Double,
        elongationTopocentricDeg: Double,
        ijtimaBeforeSunset: Boolean = true,
        moonAgeHours: Double = 12.0
    ): MabimsCriteriaAssessment {
        val altMargin = moonAltitudeMariDeg - NEO_MABIMS_MIN_ALTITUDE_DEG
        val elongMargin = elongationTopocentricDeg - NEO_MABIMS_MIN_ELONGATION_DEG

        val meetsNeo = ijtimaBeforeSunset &&
            moonAltitudeMariDeg >= NEO_MABIMS_MIN_ALTITUDE_DEG &&
            elongationTopocentricDeg >= NEO_MABIMS_MIN_ELONGATION_DEG

        val meetsClassic = ijtimaBeforeSunset && (
            (moonAltitudeMariDeg >= CLASSIC_MABIMS_MIN_ALTITUDE_DEG &&
                elongationTopocentricDeg >= CLASSIC_MABIMS_MIN_ELONGATION_DEG) ||
                moonAgeHours >= CLASSIC_MABIMS_MIN_AGE_HOURS
            )

        // Normalized visibility confidence score (0%..100%) centered around Neo-MABIMS thresholds
        val score = if (!ijtimaBeforeSunset || moonAltitudeMariDeg <= 0.0) {
            0.0
        } else {
            val altNorm = (moonAltitudeMariDeg / NEO_MABIMS_MIN_ALTITUDE_DEG).coerceIn(0.0, 1.5)
            val elongNorm = (elongationTopocentricDeg / NEO_MABIMS_MIN_ELONGATION_DEG).coerceIn(0.0, 1.5)
            ((0.55 * altNorm + 0.45 * elongNorm) / 1.5 * 100.0).coerceIn(0.0, 100.0)
        }

        val (status, label, explanation) = when {
            !ijtimaBeforeSunset -> Triple(
                VerdictStatus.TIDAK_MEMENUHI,
                "Istikmal (Pra-Ijtima')",
                "Konjungsi (Ijtima') belum terjadi saat matahari terbenam sehingga hilal tidak mungkin dirukyat."
            )
            moonAltitudeMariDeg <= 0.0 -> Triple(
                VerdictStatus.TIDAK_MEMENUHI,
                "Istikmal (Di Bawah Ufuk)",
                "Tinggi hilal negatif (${String.format(Locale.US, "%.2f°", moonAltitudeMariDeg)}), bulan sudah terbenam sebelum Ghurub."
            )
            meetsNeo -> Triple(
                VerdictStatus.MEMENUHI,
                "Imkanur Rukyat (Neo-MABIMS)",
                "Memenuhi syarat Neo-MABIMS: tinggi ≥ 3.00° (margin ${String.format(Locale.US, "%+.2f°", altMargin)}) dan elongasi ≥ 6.40° (margin ${String.format(Locale.US, "%+.2f°", elongMargin)})."
            )
            moonAltitudeMariDeg >= 2.3 && elongationTopocentricDeg >= 5.2 -> Triple(
                VerdictStatus.MARGINAL,
                "Batas Tipis (Dekat Ambang)",
                "Mendekati ambang Neo-MABIMS 3° / 6.4°, peluang terlihat sangat rendah (memerlukan alat optik & langit bersih)."
            )
            else -> Triple(
                VerdictStatus.TIDAK_MEMENUHI,
                "Istikmal (Belum Memenuhi MABIMS)",
                "Hilal di atas ufuk tetapi di bawah ambang Neo-MABIMS (selisih tinggi ${String.format(Locale.US, "%+.2f°", altMargin)}, elongasi ${String.format(Locale.US, "%+.2f°", elongMargin)})."
            )
        }

        return MabimsCriteriaAssessment(
            meetsNeoMabims = meetsNeo,
            meetsClassicMabims = meetsClassic,
            altitudeMarginNeoDeg = altMargin,
            elongationMarginNeoDeg = elongMargin,
            visibilityScorePercent = score,
            status = status,
            predictionLabel = label,
            explanation = explanation
        )
    }

    private fun tzFromOffset(offsetHours: Double): TimeZone {
        val totalMinutes = (offsetHours * 60.0).roundToInt()
        val sign = if (totalMinutes >= 0) "+" else "-"
        val absM = abs(totalMinutes)
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", sign, absM / 60, absM % 60))
    }
}
