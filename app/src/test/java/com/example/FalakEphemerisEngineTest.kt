package com.example

import com.example.domain.falak.FalakCatalog
import com.example.domain.falak.FalakEphemerisEngine
import com.example.domain.falak.FalakReportGenerator
import com.example.domain.falak.FalakReportInput
import com.example.domain.falak.HijriCalendarEngine
import com.example.domain.falak.HijriEventType
import com.example.domain.falak.MabimsHilalModel
import com.example.domain.falak.ReportScope
import com.example.domain.falak.RukyatCriteria
import com.example.domain.falak.VerdictStatus
import com.example.domain.falak.WaktuIstiwakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FalakEphemerisEngineTest {

    @Test
    fun qiblaAzimuthFromJakartaIsAround295Degrees() {
        val jakarta = FalakCatalog.markazStations.first { it.id == "istiqlal" }
        val (azimuth, distanceKm) = FalakEphemerisEngine.calculateQiblaAzimuthAndDistance(
            jakarta.latitude,
            jakarta.longitude
        )
        assertEquals(295.15, azimuth, 0.5)
        assertTrue(distanceKm in 7800.0..8000.0)
    }

    @Test
    fun hilalCalculationForRamadhan1447ProducesValidEphemeris() {
        val pelabuhanRatu = FalakCatalog.markazStations.first { it.id == "pelabuhan_ratu" }
        val result = FalakEphemerisEngine.calculateHilalForHijriMonth(
            hijriYear = 1447,
            hijriMonth = 9,
            markaz = pelabuhanRatu
        )
        assertTrue(result.sunAzimuthDeg in 240.0..300.0)
        assertTrue(result.moonAzimuthDeg in 240.0..300.0)
        assertTrue(result.elongationTopoDeg >= 0.0)
        assertTrue(result.horizonTrack.isNotEmpty())
        assertTrue(result.verdicts.containsKey(RukyatCriteria.MABIMS_BARU))
        assertTrue(result.verdicts.containsKey(RukyatCriteria.WUJUDUL_HILAL))
    }

    @Test
    fun istiwakAndPrayerTimesAreChronological() {
        val gresik = FalakCatalog.markazStations.first { it.id == "condrodipo" }
        val now = 1_760_000_000_000L
        val istiwak = FalakEphemerisEngine.calculateIstiwakAndPrayerTimes(now, gresik)
        assertEquals(8, istiwak.prayerTimes.size)
        for (i in 0 until istiwak.prayerTimes.lastIndex) {
            assertTrue(istiwak.prayerTimes[i].timeMillis < istiwak.prayerTimes[i + 1].timeMillis)
        }
    }

    @Test
    fun dmsFormattingHandlesPositiveAndNegativeAngles() {
        val pos = FalakEphemerisEngine.formatDms(3.5, signed = true)
        val neg = FalakEphemerisEngine.formatDms(-6.5, signed = true)
        assertEquals("+03° 30' 00\"", pos)
        assertEquals("-06° 30' 00\"", neg)
    }

    @Test
    fun qiblaAzimuthFromGlobalCitiesIsAccurate() {
        // London (51.5074 N, -0.1278 W) -> ~118.99 deg ESE
        val (londonAz, _) = FalakEphemerisEngine.calculateQiblaAzimuthAndDistance(51.5074, -0.1278)
        assertEquals(118.99, londonAz, 0.5)

        // Tokyo (35.6762 N, 139.6503 E) -> ~293.0 deg WNW
        val (tokyoAz, _) = FalakEphemerisEngine.calculateQiblaAzimuthAndDistance(35.6762, 139.6503)
        assertEquals(293.0, tokyoAz, 0.5)
    }

    @Test
    fun lunarPhaseCalculationProducesValidSynodicCycle() {
        val jakarta = FalakCatalog.markazStations.first { it.id == "istiqlal" }
        val phase = FalakEphemerisEngine.calculateLunarPhase(1_760_000_000_000L, jakarta)
        assertTrue(phase.lunarAgeDays in 0.0..30.0)
        assertTrue(phase.cycleFraction in 0.0..1.0)
        assertTrue(phase.illuminationPercent in 0.0..100.0)
        assertTrue(phase.earthMoonDistanceKm in 350_000.0..410_000.0)
        assertEquals(30, phase.cycleCurvePoints.size)
        assertTrue(phase.phaseNameIndonesian.isNotBlank())
    }

    @Test
    fun mabimsMathematicalModelEvaluatesThresholdsAndMarginsAccurately() {
        val eligible = MabimsHilalModel.evaluateCriteriaFromMeasurements(
            moonAltitudeMariDeg = 3.5,
            elongationTopocentricDeg = 7.0,
            ijtimaBeforeSunset = true,
            moonAgeHours = 14.0
        )
        assertTrue(eligible.meetsNeoMabims)
        assertEquals(VerdictStatus.MEMENUHI, eligible.status)
        assertEquals(0.5, eligible.altitudeMarginNeoDeg, 1e-6)
        assertEquals(0.6, eligible.elongationMarginNeoDeg, 1e-6)

        val marginal = MabimsHilalModel.evaluateCriteriaFromMeasurements(
            moonAltitudeMariDeg = 2.6,
            elongationTopocentricDeg = 5.5,
            ijtimaBeforeSunset = true,
            moonAgeHours = 10.0
        )
        assertFalse(marginal.meetsNeoMabims)
        assertTrue(marginal.meetsClassicMabims)
        assertEquals(VerdictStatus.MARGINAL, marginal.status)

        val preIjtima = MabimsHilalModel.evaluateCriteriaFromMeasurements(
            moonAltitudeMariDeg = 3.2,
            elongationTopocentricDeg = 6.8,
            ijtimaBeforeSunset = false,
            moonAgeHours = -1.0
        )
        assertFalse(preIjtima.meetsNeoMabims)
        assertEquals(VerdictStatus.TIDAK_MEMENUHI, preIjtima.status)
    }

    @Test
    fun mabimsPredictionDecomposesSunsetAltitudeAndElongationConsistently() {
        val gresik = FalakCatalog.markazStations.first { it.id == "condrodipo" }
        val pred = MabimsHilalModel.predictForHijriMonth(1447, 9, gresik)
        val reconstructedAlt = pred.moonAltitudeHaqiqiDeg +
            pred.parallaxCorrectionDeg +
            pred.refractionCorrectionDeg +
            pred.horizonDipDeg
        assertEquals(pred.moonAltitudeMariDeg, reconstructedAlt, 1e-4)
        assertTrue(pred.elongationTopocentricDeg >= 0.0)
        assertTrue(pred.elongationGeocentricDeg >= 0.0)
    }

    @Test
    fun waktuIstiwakTrueNoonMatches1200WisAndEquinoxAltitude() {
        val jakarta = FalakCatalog.markazStations.first { it.id == "istiqlal" }
        // March 20, 2026 (Vernal Equinox: solar declination ~ 0 deg)
        val res = WaktuIstiwakCalculator.calculateTrueNoon(2026, 3, 20, jakarta)
        assertEquals("12:00:00 WIS", res.wisVerificationLabel)
        // Jakarta latitude is -6.17 deg -> Solar noon altitude at equinox ~ 90 - 6.17 = 83.83 deg
        assertEquals(90.0 - kotlin.math.abs(jakarta.latitude), res.solarNoonAltitudeDeg, 0.5)
        // Longitude correction for 106.8314 E vs 105 E = +1.8314 * 4 = +7.3256 minutes
        assertEquals(7.3256, res.longitudeCorrectionMinutes, 0.05)
    }

    @Test
    fun reportGeneratorBuildsCompleteHilalAndIstiwakTextReports() {
        val jakarta = FalakCatalog.markazStations.first { it.id == "istiqlal" }
        val hilal = FalakEphemerisEngine.calculateHilalForHijriMonth(1447, 9, jakarta)
        val mabims = MabimsHilalModel.predictForHijriMonth(1447, 9, jakarta)
        val lunar = FalakEphemerisEngine.calculateLunarPhase(1_760_000_000_000L, jakarta)
        val trueNoon = WaktuIstiwakCalculator.calculateTrueNoon(2026, 3, 20, jakarta)
        val istiwak = FalakEphemerisEngine.calculateIstiwakAndPrayerTimes(1_760_000_000_000L, jakarta)

        val completeInput = FalakReportInput(
            markaz = jakarta,
            selectedCriteria = RukyatCriteria.MABIMS_BARU,
            hilalResult = hilal,
            mabimsPrediction = mabims,
            lunarPhaseData = lunar,
            trueNoonResult = trueNoon,
            istiwakResult = istiwak,
            scope = ReportScope.COMPLETE,
            generatedAtMillis = 1_760_000_000_000L
        )
        val completeText = FalakReportGenerator.buildTextReport(completeInput)
        assertTrue(completeText.contains("LAPORAN HISAB HILAL & WAKTU ISTIWAK"))
        assertTrue(completeText.contains("Menara Masjid Istiqlal"))
        assertTrue(completeText.contains("Model Matematis MABIMS"))
        assertTrue(completeText.contains("12:00:00 WIS"))
        assertTrue(completeText.contains("Jadwal Dzuhur"))

        val hilalOnlyText = FalakReportGenerator.buildTextReport(completeInput.copy(scope = ReportScope.HILAL_ONLY))
        assertTrue(hilalOnlyText.contains("Prediksi Awal Ramadhan 1447 H"))
        assertFalse(hilalOnlyText.contains("Kalkulator Waktu Istiwak"))

        val istiwakOnlyText = FalakReportGenerator.buildTextReport(completeInput.copy(scope = ReportScope.ISTIWAK_ONLY))
        assertTrue(istiwakOnlyText.contains("Kalkulator Waktu Istiwak"))
        assertFalse(istiwakOnlyText.contains("Model Matematis MABIMS"))

        val fileName = FalakReportGenerator.suggestedFileName(completeInput)
        assertTrue(fileName.startsWith("HilalChecker_Falak_Ramadhan_1447H_"))
    }

    @Test
    fun hijriCalendarEngineBuildsKeyDatesAndPrayerEventsAccurately() {
        val jakarta = FalakCatalog.markazStations.first { it.id == "istiqlal" }

        // Verify exact round-trip conversion Hijri -> JDN -> Gregorian -> Hijri
        val jdn = HijriCalendarEngine.hijriToJdn(1447, 9, 1)
        val (gY, gM, gD) = HijriCalendarEngine.jdnToGregorian(jdn)
        val roundTrip = HijriCalendarEngine.gregorianToHijri(gY, gM, gD)
        assertEquals(1447, roundTrip.year)
        assertEquals(9, roundTrip.month)
        assertEquals(1, roundTrip.day)

        val ramadhanCal = HijriCalendarEngine.buildMonthCalendar(1447, 9, jakarta, 1_760_000_000_000L)
        assertTrue(ramadhanCal.daysInMonth in 29..30)
        assertEquals(ramadhanCal.daysInMonth, ramadhanCal.cells.size)
        assertTrue(ramadhanCal.keyEvents.any { it.hijriDay == 1 && it.type == HijriEventType.MAJOR_HOLIDAY })
        assertTrue(ramadhanCal.keyEvents.any { it.hijriDay == 13 && it.type == HijriEventType.SUNNAH_FASTING })
        assertTrue(ramadhanCal.keyEvents.any { it.hijriDay == 17 && it.title.contains("Nuzulul") })
        assertTrue(ramadhanCal.keyEvents.any { it.hijriDay == 29 && it.type == HijriEventType.RUKYAT_OBSERVATION })
        assertTrue(ramadhanCal.upcomingPrayerEvents.isNotEmpty())

        val dzulhijjahCal = HijriCalendarEngine.buildMonthCalendar(1447, 12, jakarta, 1_760_000_000_000L)
        assertTrue(dzulhijjahCal.keyEvents.any { it.hijriDay == 9 && it.title.contains("Arafah") })
        assertTrue(dzulhijjahCal.keyEvents.any { it.hijriDay == 10 && it.title.contains("Idul Adha") })
        assertTrue(dzulhijjahCal.keyEvents.any { it.hijriDay == 13 && it.title.contains("Tasyrik") })
    }
}
