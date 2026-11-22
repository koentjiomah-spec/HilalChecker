package com.example

import com.example.domain.falak.FalakCatalog
import com.example.domain.falak.FalakEphemerisEngine
import com.example.domain.falak.RukyatCriteria
import org.junit.Assert.assertEquals
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
}
