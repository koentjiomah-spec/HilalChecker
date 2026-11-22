package com.example.domain.falak

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure Kotlin Astronomical Ephemeris & Ilmu Falak Engine.
 * Implements Jean Meeus Solar/Lunar Ephemeris, New Moon (Ijtima') solver,
 * Topocentric Horizon Coordinates, Refraction, Sunset/Moonset solvers,
 * MABIMS/Wujudul Hilal/Odeh evaluation, Waktu Istiwak (WIS), and Kemenag Prayer Times.
 */
object FalakEphemerisEngine {

    private const val DEG2RAD = PI / 180.0
    private const val RAD2DEG = 180.0 / PI
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LON = 39.826206
    private const val EARTH_RADIUS_KM = 6371.0
    private const val SYNODIC_MONTH_DAYS = 29.530588861

    // Epoch New Moon J2000 reference: 2000 Jan 6 18:14 UTC -> JD 2451550.25972
    private const val NEW_MOON_EPOCH_JD = 2451550.09766

    // Hijri epoch reference: 1 Muharram 1446 H ~ Ijtima 5 Jul 2024 22:57 UTC (JD 2460497.456)
    // Lunation k from J2000 epoch: (2460497.456 - 2451550.09766) / 29.530588861 = 303.000
    private const val BASE_HIJRI_YEAR = 1446
    private const val BASE_HIJRI_MONTH = 1
    private const val BASE_LUNATION_K = 303

    internal data class SunPosition(
        val raDeg: Double,
        val decDeg: Double,
        val distanceAu: Double,
        val apparentLonDeg: Double,
        val equationOfTimeMinutes: Double
    )

    internal data class MoonPosition(
        val raDeg: Double,
        val decDeg: Double,
        val distanceKm: Double,
        val parallaxDeg: Double,
        val apparentLonDeg: Double,
        val apparentLatDeg: Double
    )

    data class HorizontalCoord(
        val altitudeHaqiqiDeg: Double,
        val altitudeMariDeg: Double,
        val azimuthDeg: Double
    )

    fun unixMillisToJd(millis: Long): Double = millis / 86_400_000.0 + 2440587.5

    fun jdToUnixMillis(jd: Double): Long = ((jd - 2440587.5) * 86_400_000.0).toLong()

    private fun normalizeDeg(deg: Double): Double {
        var r = deg % 360.0
        if (r < 0.0) r += 360.0
        return r
    }

    private fun normalizeHourAngleDeg(deg: Double): Double {
        var r = normalizeDeg(deg)
        if (r > 180.0) r -= 360.0
        return r
    }

    internal fun computeSunPosition(jd: Double): SunPosition {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = normalizeDeg(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val m = normalizeDeg(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val e = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t

        val mRad = m * DEG2RAD
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
            (0.019993 - 0.000101 * t) * sin(2.0 * mRad) +
            0.000289 * sin(3.0 * mRad)

        val trueLon = l0 + c
        val nu = m + c
        val rAu = (1.000001018 * (1.0 - e * e)) / (1.0 + e * cos(nu * DEG2RAD))

        val omega = 125.04 - 1934.136 * t
        val lambda = trueLon - 0.00569 - 0.00478 * sin(omega * DEG2RAD)

        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(omega * DEG2RAD)

        val lambdaRad = lambda * DEG2RAD
        val epsRad = eps * DEG2RAD

        val raRad = atan2(cos(epsRad) * sin(lambdaRad), cos(lambdaRad))
        val decRad = asin(sin(epsRad) * sin(lambdaRad))

        val raDeg = normalizeDeg(raRad * RAD2DEG)
        val decDeg = decRad * RAD2DEG

        // Equation of time in minutes
        val y = tan(epsRad / 2.0).let { it * it }
        val l0Rad = l0 * DEG2RAD
        val eotRad = y * sin(2.0 * l0Rad) -
            2.0 * e * sin(mRad) +
            4.0 * e * y * sin(mRad) * cos(2.0 * l0Rad) -
            0.5 * y * y * sin(4.0 * l0Rad) -
            1.25 * e * e * sin(2.0 * mRad)
        val eotMinutes = eotRad * RAD2DEG * 4.0

        return SunPosition(
            raDeg = raDeg,
            decDeg = decDeg,
            distanceAu = rAu,
            apparentLonDeg = normalizeDeg(lambda),
            equationOfTimeMinutes = eotMinutes
        )
    }

    internal fun computeMoonPosition(jd: Double): MoonPosition {
        val t = (jd - 2451545.0) / 36525.0
        val lPrime = normalizeDeg(
            218.3164477 + 481267.88123421 * t - 0.0015786 * t * t +
                t * t * t / 538841.0 - t * t * t * t / 65194000.0
        )
        val d = normalizeDeg(
            297.8501921 + 445267.1114034 * t - 0.0018819 * t * t +
                t * t * t / 545868.0 - t * t * t * t / 113065000.0
        )
        val m = normalizeDeg(
            357.5291092 + 35999.0502909 * t - 0.0001536 * t * t + t * t * t / 24490000.0
        )
        val mPrime = normalizeDeg(
            134.9633964 + 477198.8675055 * t + 0.0087414 * t * t +
                t * t * t / 69699.0 - t * t * t * t / 14712000.0
        )
        val f = normalizeDeg(
            93.2720950 + 483202.0175233 * t - 0.0036539 * t * t -
                t * t * t / 3526000.0 + t * t * t * t / 863310000.0
        )

        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t
        val dR = d * DEG2RAD
        val mR = m * DEG2RAD
        val mpR = mPrime * DEG2RAD
        val fR = f * DEG2RAD

        // Main periodic terms (Meeus Ch. 47 truncated to ~0.005 deg accuracy)
        var sumL = 6.288774 * sin(mpR) +
            1.274027 * sin(2.0 * dR - mpR) +
            0.658314 * sin(2.0 * dR) +
            0.213618 * sin(2.0 * mpR) -
            0.185116 * e * sin(mR) -
            0.114332 * sin(2.0 * fR) +
            0.058793 * sin(2.0 * dR - 2.0 * mpR) +
            0.057066 * e * sin(2.0 * dR - mR - mpR) +
            0.053322 * sin(2.0 * dR + mpR) +
            0.045758 * e * sin(2.0 * dR - mR) -
            0.040923 * e * sin(mR - mpR) -
            0.034720 * sin(dR) -
            0.030383 * e * sin(mR + mpR) +
            0.015327 * sin(2.0 * dR - 2.0 * fR) -
            0.012528 * sin(mpR + 2.0 * fR) +
            0.010980 * sin(mpR - 2.0 * fR) +
            0.010675 * sin(4.0 * dR - mpR) +
            0.010034 * sin(3.0 * mpR) +
            0.008548 * sin(4.0 * dR - 2.0 * mpR) -
            0.007888 * e * sin(2.0 * dR + mR - mpR) -
            0.006766 * e * sin(2.0 * dR + mR) -
            0.005163 * sin(dR - mpR) +
            0.004987 * e * sin(dR + mR) +
            0.004036 * e * sin(2.0 * dR - mR + mpR)

        val a1 = (119.75 + 131.849 * t) * DEG2RAD
        val a2 = (53.09 + 479264.290 * t) * DEG2RAD
        val a3 = (313.45 + 481266.484 * t) * DEG2RAD
        sumL += 0.003958 * sin(a1) + 0.001962 * sin(lPrime * DEG2RAD - fR) + 0.000318 * sin(a2)

        val sumB = 5.128122 * sin(fR) +
            0.280602 * sin(mpR + fR) +
            0.277693 * sin(mpR - fR) +
            0.173237 * sin(2.0 * dR - fR) +
            0.055413 * sin(2.0 * dR - mpR + fR) +
            0.046271 * sin(2.0 * dR - mpR - fR) +
            0.032573 * sin(2.0 * dR + fR) +
            0.017198 * sin(2.0 * mpR + fR) +
            0.009266 * sin(2.0 * dR + mpR - fR) +
            0.008822 * sin(2.0 * mpR - fR) +
            0.008216 * e * sin(2.0 * dR - mR - fR) +
            0.004324 * sin(2.0 * dR - 2.0 * mpR - fR) +
            0.004200 * sin(2.0 * dR + mpR + fR) -
            0.003359 * e * sin(2.0 * dR + mR - fR) +
            0.002236 * sin(a1 - fR) +
            0.002236 * sin(a1 + fR) -
            0.002062 * sin(a3)

        val sumR = -20905.355 * cos(mpR) -
            3699.111 * cos(2.0 * dR - mpR) -
            2955.968 * cos(2.0 * dR) -
            569.925 * cos(2.0 * mpR) +
            246.158 * cos(2.0 * dR - 2.0 * mpR) -
            204.586 * e * cos(2.0 * dR - mR) -
            170.733 * cos(2.0 * dR + mpR) -
            152.138 * e * cos(2.0 * dR - mR - mpR) -
            129.620 * e * cos(mR - mpR) +
            108.743 * cos(dR) +
            104.755 * e * cos(mR + mpR) +
            79.661 * cos(mpR - 2.0 * fR)

        val lambda = normalizeDeg(lPrime + sumL)
        val beta = sumB
        val distanceKm = 385000.56 + sumR
        val parallaxDeg = asin(6378.14 / distanceKm) * RAD2DEG

        val omega = 125.04 - 1934.136 * t
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(omega * DEG2RAD)

        val lambdaRad = lambda * DEG2RAD
        val betaRad = beta * DEG2RAD
        val epsRad = eps * DEG2RAD

        val raRad = atan2(
            sin(lambdaRad) * cos(epsRad) - tan(betaRad) * sin(epsRad),
            cos(lambdaRad)
        )
        val decRad = asin(
            sin(betaRad) * cos(epsRad) + cos(betaRad) * sin(epsRad) * sin(lambdaRad)
        )

        return MoonPosition(
            raDeg = normalizeDeg(raRad * RAD2DEG),
            decDeg = decRad * RAD2DEG,
            distanceKm = distanceKm,
            parallaxDeg = parallaxDeg,
            apparentLonDeg = lambda,
            apparentLatDeg = beta
        )
    }

    private fun greenwichMeanSiderealTimeDeg(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        return normalizeDeg(
            280.46061837 + 360.98564736629 * (jd - 2451545.0) +
                0.000387933 * t * t - t * t * t / 38710000.0
        )
    }

    internal fun atmosphericRefractionDeg(apparentAltDeg: Double): Double {
        if (apparentAltDeg < -3.0) return 0.0
        val h = apparentAltDeg.coerceAtLeast(-1.0)
        // Bennett's formula (arcminutes -> degrees)
        val rArcMin = 1.02 / tan((h + 10.3 / (h + 5.11)) * DEG2RAD)
        return (rArcMin / 60.0).coerceAtLeast(0.0)
    }

    internal fun toHorizontalTopocentric(
        jd: Double,
        raDeg: Double,
        decDeg: Double,
        parallaxDeg: Double,
        latDeg: Double,
        lonDeg: Double,
        elevationMeters: Double
    ): HorizontalCoord {
        val gmst = greenwichMeanSiderealTimeDeg(jd)
        val lst = normalizeDeg(gmst + lonDeg)
        val haDeg = normalizeHourAngleDeg(lst - raDeg)

        val haRad = haDeg * DEG2RAD
        val decRad = decDeg * DEG2RAD
        val latRad = latDeg * DEG2RAD

        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val altHaqiqi = asin(sinAlt.coerceIn(-1.0, 1.0)) * RAD2DEG

        // Azimuth measured clockwise from North (0 = N, 90 = E, 180 = S, 270 = W)
        val yAz = -cos(decRad) * sin(haRad)
        val xAz = sin(decRad) * cos(latRad) - cos(decRad) * sin(latRad) * cos(haRad)
        val azDeg = normalizeDeg(atan2(yAz, xAz) * RAD2DEG)

        // Topocentric parallax correction
        val topoAlt = altHaqiqi - parallaxDeg * cos(altHaqiqi * DEG2RAD)
        val dipDeg = 1.76 * sqrt(elevationMeters.coerceAtLeast(0.0)) / 60.0
        val refraction = atmosphericRefractionDeg(topoAlt)
        val altMari = topoAlt + refraction + dipDeg

        return HorizontalCoord(
            altitudeHaqiqiDeg = altHaqiqi,
            altitudeMariDeg = altMari,
            azimuthDeg = azDeg
        )
    }

    /**
     * Exact New Moon (Ijtima') JD for lunation k using bisection on Moon-Sun ecliptic longitude difference.
     */
    internal fun findIjtimaJdForLunation(k: Int): Double {
        val tApprox = k / 1236.85
        var jdApprox = NEW_MOON_EPOCH_JD + SYNODIC_MONTH_DAYS * k +
            0.0001337 * tApprox * tApprox
        // Narrow search bracket +/- 1.2 days
        var low = jdApprox - 1.2
        var high = jdApprox + 1.2

        fun lonDiff(jd: Double): Double {
            val sun = computeSunPosition(jd)
            val moon = computeMoonPosition(jd)
            return normalizeHourAngleDeg(moon.apparentLonDeg - sun.apparentLonDeg)
        }

        // Refine using 32 iterations of bisection (< 1 second precision)
        var fLow = lonDiff(low)
        repeat(34) {
            val mid = (low + high) / 2.0
            val fMid = lonDiff(mid)
            if ((fLow <= 0.0 && fMid <= 0.0) || (fLow >= 0.0 && fMid >= 0.0)) {
                low = mid
                fLow = fMid
            } else {
                high = mid
            }
        }
        return (low + high) / 2.0
    }

    /**
     * Find Sunset (Ghurub) JD on the local calendar date containing [approxLocalNoonJd].
     * Standard Sunset criteria: Sun center apparent altitude = -0.8333 deg - dip.
     */
    internal fun findSunsetJd(
        localYear: Int,
        localMonth: Int,
        localDay: Int,
        markaz: MarkazLocation
    ): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(localYear, localMonth - 1, localDay, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Noon UTC shifted by timezone to local ~18:00 (sunset estimate)
        val localSunsetEstimateUtcMillis =
            cal.timeInMillis + ((18.0 - markaz.timezoneOffsetHours) * 3_600_000.0).toLong()
        var jd = unixMillisToJd(localSunsetEstimateUtcMillis)
        val dipDeg = 1.76 * sqrt(markaz.elevationMeters.coerceAtLeast(0.0)) / 60.0
        val targetAltHaqiqi = -0.8333 - dipDeg

        // 4 Newton/secant-like iterations using hour-angle adjustment
        repeat(5) {
            val sun = computeSunPosition(jd)
            val gmst = greenwichMeanSiderealTimeDeg(jd)
            val lst = normalizeDeg(gmst + markaz.longitude)
            val haDeg = normalizeHourAngleDeg(lst - sun.raDeg)
            val latR = markaz.latitude * DEG2RAD
            val decR = sun.decDeg * DEG2RAD
            val cosHaTarget = ((sin(targetAltHaqiqi * DEG2RAD) - sin(latR) * sin(decR)) /
                (cos(latR) * cos(decR))).coerceIn(-0.999, 0.999)
            val haTarget = acos(cosHaTarget) * RAD2DEG // positive for sunset (West)
            val deltaDeg = haTarget - haDeg
            jd += deltaDeg / 360.9856
        }
        return jd
    }

    private fun findMoonsetAfterSunset(sunsetJd: Double, markaz: MarkazLocation): Double? {
        val startMoon = computeMoonPosition(sunsetJd)
        val startHorizon = toHorizontalTopocentric(
            jd = sunsetJd,
            raDeg = startMoon.raDeg,
            decDeg = startMoon.decDeg,
            parallaxDeg = startMoon.parallaxDeg,
            latDeg = markaz.latitude,
            lonDeg = markaz.longitude,
            elevationMeters = markaz.elevationMeters
        )
        if (startHorizon.altitudeMariDeg <= 0.0) return null

        // Step forward in 2-minute increments up to 4 hours
        var prevJd = sunsetJd
        var prevAlt = startHorizon.altitudeMariDeg
        for (step in 1..120) {
            val curJd = sunsetJd + (step * 2.0) / 1440.0
            val m = computeMoonPosition(curJd)
            val h = toHorizontalTopocentric(
                curJd, m.raDeg, m.decDeg, m.parallaxDeg,
                markaz.latitude, markaz.longitude, markaz.elevationMeters
            )
            if (h.altitudeMariDeg <= 0.0) {
                // Linear interpolation for exact crossing
                val frac = prevAlt / (prevAlt - h.altitudeMariDeg).coerceAtLeast(1e-6)
                return prevJd + frac * (curJd - prevJd)
            }
            prevJd = curJd
            prevAlt = h.altitudeMariDeg
        }
        return null
    }

    private fun lunationIndexForHijriMonth(hijriYear: Int, hijriMonth: Int): Int {
        val monthDiff = (hijriYear - BASE_HIJRI_YEAR) * 12 + (hijriMonth - BASE_HIJRI_MONTH)
        // We want the Ijtima' at the END of month (m-1) which marks the start of (hijriYear, hijriMonth)
        return BASE_LUNATION_K + monthDiff
    }

    fun calculateHilalForHijriMonth(
        hijriYear: Int,
        hijriMonth: Int,
        markaz: MarkazLocation
    ): HilalResult {
        val k = lunationIndexForHijriMonth(hijriYear, hijriMonth)
        val ijtimaJd = findIjtimaJdForLunation(k)
        val ijtimaMillis = jdToUnixMillis(ijtimaJd)

        // Determine local date of Ijtima'
        val localOffsetMillis = (markaz.timezoneOffsetHours * 3_600_000.0).toLong()
        val localCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = ijtimaMillis + localOffsetMillis
        }
        var obsYear = localCal.get(Calendar.YEAR)
        var obsMonth = localCal.get(Calendar.MONTH) + 1
        var obsDay = localCal.get(Calendar.DAY_OF_MONTH)

        var sunsetJd = findSunsetJd(obsYear, obsMonth, obsDay, markaz)
        // If Ijtima' occurs AFTER sunset on that local day, standard Rukyat day is the NEXT evening?
        // Wait: in Digital Falak & Kemenag, we show the Ijtima' day's sunset OR if user checks evening of 29th,
        // if ijtima happened after sunset, the moon is below horizon on that evening (Wujudul Hilal = false, Istikmal).
        // Let's keep the exact evening of the 29th (the day containing Ijtima', unless Ijtima' is before sunrise? Actually
        // if Ijtima' is at 02:00 local, sunset is at 18:00 same day -> moon age ~16h; if Ijtima' is at 21:00 local,
        // sunset was at 18:00 same day -> ijtimaBeforeSunset = false, moon age negative, Istikmal! Which is super educational!)

        val sunsetMillis = jdToUnixMillis(sunsetJd)
        val ijtimaBeforeSunset = ijtimaJd < sunsetJd

        val sunAtSunset = computeSunPosition(sunsetJd)
        val moonAtSunset = computeMoonPosition(sunsetJd)

        val sunHoriz = toHorizontalTopocentric(
            sunsetJd, sunAtSunset.raDeg, sunAtSunset.decDeg, 0.0024,
            markaz.latitude, markaz.longitude, markaz.elevationMeters
        )
        val moonHoriz = toHorizontalTopocentric(
            sunsetJd, moonAtSunset.raDeg, moonAtSunset.decDeg, moonAtSunset.parallaxDeg,
            markaz.latitude, markaz.longitude, markaz.elevationMeters
        )

        // Geocentric Elongation (angular separation in RA/Dec)
        val sRa = sunAtSunset.raDeg * DEG2RAD
        val sDec = sunAtSunset.decDeg * DEG2RAD
        val mRa = moonAtSunset.raDeg * DEG2RAD
        val mDec = moonAtSunset.decDeg * DEG2RAD
        val cosElongGeo = (sin(sDec) * sin(mDec) + cos(sDec) * cos(mDec) * cos(sRa - mRa))
            .coerceIn(-1.0, 1.0)
        val elongGeoDeg = acos(cosElongGeo) * RAD2DEG

        // Topocentric Elongation from horizontal Az/Alt
        val sAltR = sunHoriz.altitudeHaqiqiDeg * DEG2RAD
        val mAltR = (moonHoriz.altitudeHaqiqiDeg - moonAtSunset.parallaxDeg * cos(moonHoriz.altitudeHaqiqiDeg * DEG2RAD)) * DEG2RAD
        val dAzR = (moonHoriz.azimuthDeg - sunHoriz.azimuthDeg) * DEG2RAD
        val cosElongTopo = (sin(sAltR) * sin(mAltR) + cos(sAltR) * cos(mAltR) * cos(dAzR))
            .coerceIn(-1.0, 1.0)
        val elongTopoDeg = acos(cosElongTopo) * RAD2DEG

        val moonAgeHours = (sunsetJd - ijtimaJd) * 24.0
        val moonsetJd = findMoonsetAfterSunset(sunsetJd, markaz)
        val mukutsMinutes = if (moonsetJd != null) {
            ((moonsetJd - sunsetJd) * 1440.0).coerceAtLeast(0.0)
        } else 0.0

        // Crescent illumination & width in arcminutes (moon semi-diameter ~ 15.5' * (384400 / dist))
        val semiDiamArcmin = 15.54 * (384400.0 / moonAtSunset.distanceKm)
        val phaseAngleRad = PI - (elongTopoDeg * DEG2RAD)
        val illumination = ((1.0 + cos(phaseAngleRad)) / 2.0) * 100.0
        val crescentWidthArcmin = semiDiamArcmin * (1.0 - cos(elongTopoDeg * DEG2RAD))

        // Crescent orientation / tilt angle relative to horizon
        val dy = moonHoriz.altitudeMariDeg - sunHoriz.altitudeMariDeg
        val dx = moonHoriz.azimuthDeg - sunHoriz.azimuthDeg
        val tiltDeg = atan2(dy, dx) * RAD2DEG

        // Track points from -30m to +60m around sunset
        val track = (-30..60 step 5).map { offsetMin ->
            val ptJd = sunsetJd + offsetMin / 1440.0
            val sPt = computeSunPosition(ptJd)
            val mPt = computeMoonPosition(ptJd)
            val sH = toHorizontalTopocentric(
                ptJd, sPt.raDeg, sPt.decDeg, 0.0024,
                markaz.latitude, markaz.longitude, markaz.elevationMeters
            )
            val mH = toHorizontalTopocentric(
                ptJd, mPt.raDeg, mPt.decDeg, mPt.parallaxDeg,
                markaz.latitude, markaz.longitude, markaz.elevationMeters
            )
            val dAzPt = (mH.azimuthDeg - sH.azimuthDeg) * DEG2RAD
            val sAR = sH.altitudeHaqiqiDeg * DEG2RAD
            val mAR = mH.altitudeHaqiqiDeg * DEG2RAD
            val elPt = acos((sin(sAR) * sin(mAR) + cos(sAR) * cos(mAR) * cos(dAzPt)).coerceIn(-1.0, 1.0)) * RAD2DEG

            HorizonTrackPoint(
                offsetMinutesFromSunset = offsetMin,
                timeLabel = formatTimeOnly(jdToUnixMillis(ptJd), markaz.timezoneOffsetHours),
                sunAltitudeDeg = sH.altitudeMariDeg,
                sunAzimuthDeg = sH.azimuthDeg,
                moonAltitudeMariDeg = mH.altitudeMariDeg,
                moonAzimuthDeg = mH.azimuthDeg,
                elongationDeg = elPt
            )
        }

        val verdicts = evaluateAllCriteria(
            ijtimaBeforeSunset = ijtimaBeforeSunset,
            altMari = moonHoriz.altitudeMariDeg,
            altHaqiqi = moonHoriz.altitudeHaqiqiDeg,
            elongTopo = elongTopoDeg,
            moonAgeHours = moonAgeHours
        )

        val monthName = FalakCatalog.hijriMonths.firstOrNull { it.first == hijriMonth }?.second ?: "Ramadhan"

        return HilalResult(
            hijriMonth = hijriMonth,
            hijriMonthName = monthName,
            hijriYear = hijriYear,
            markaz = markaz,
            observationDateLabel = formatDateIndonesian(sunsetMillis, markaz.timezoneOffsetHours),
            observationDayLabel = formatDayIndonesian(sunsetMillis, markaz.timezoneOffsetHours),
            ijtimaUtcMillis = ijtimaMillis,
            ijtimaLocalLabel = "${formatDateTimeShort(ijtimaMillis, markaz.timezoneOffsetHours)} ${markaz.timezoneLabel}",
            ijtimaBeforeSunset = ijtimaBeforeSunset,
            sunsetUtcMillis = sunsetMillis,
            sunsetLocalLabel = "${formatTimeSeconds(sunsetMillis, markaz.timezoneOffsetHours)} ${markaz.timezoneLabel}",
            moonsetUtcMillis = moonsetJd?.let { jdToUnixMillis(it) },
            moonsetLocalLabel = if (moonsetJd != null) {
                "${formatTimeSeconds(jdToUnixMillis(moonsetJd), markaz.timezoneOffsetHours)} ${markaz.timezoneLabel}"
            } else {
                "Terbenam lebih dulu"
            },
            mukutsMinutes = mukutsMinutes,
            moonAltitudeHaqiqiDeg = moonHoriz.altitudeHaqiqiDeg,
            moonAltitudeMariDeg = moonHoriz.altitudeMariDeg,
            sunAltitudeAtSunsetDeg = sunHoriz.altitudeMariDeg,
            sunAzimuthDeg = sunHoriz.azimuthDeg,
            moonAzimuthDeg = moonHoriz.azimuthDeg,
            azimuthDifferenceDeg = moonHoriz.azimuthDeg - sunHoriz.azimuthDeg,
            elongationGeoDeg = elongGeoDeg,
            elongationTopoDeg = elongTopoDeg,
            moonAgeHours = moonAgeHours,
            crescentWidthArcmin = crescentWidthArcmin,
            illuminationPercent = illumination,
            crescentTiltDeg = tiltDeg,
            nurulHilalPercent = (crescentWidthArcmin / 1.0 * 100.0).coerceIn(0.0, 100.0),
            verdicts = verdicts,
            horizonTrack = track
        )
    }

    private fun evaluateAllCriteria(
        ijtimaBeforeSunset: Boolean,
        altMari: Double,
        altHaqiqi: Double,
        elongTopo: Double,
        moonAgeHours: Double
    ): Map<RukyatCriteria, CriteriaVerdict> {
        val mabimsBaru = when {
            !ijtimaBeforeSunset || altMari <= 0.0 -> CriteriaVerdict(
                criteria = RukyatCriteria.MABIMS_BARU,
                status = VerdictStatus.TIDAK_MEMENUHI,
                summary = "Istikmal (Belum Memenuhi)",
                detail = "Hilal belum memenuhi tinggi ≥ 3° dan elongasi ≥ 6.4°."
            )
            altMari >= 3.0 && elongTopo >= 6.4 -> CriteriaVerdict(
                criteria = RukyatCriteria.MABIMS_BARU,
                status = VerdictStatus.MEMENUHI,
                summary = "Imkanur Rukyat (Memenuhi)",
                detail = "Tinggi (${formatDegSimple(altMari)}) ≥ 3° dan Elongasi (${formatDegSimple(elongTopo)}) ≥ 6.4°."
            )
            altMari >= 2.5 && elongTopo >= 5.5 -> CriteriaVerdict(
                criteria = RukyatCriteria.MABIMS_BARU,
                status = VerdictStatus.MARGINAL,
                summary = "Batas Tipis (Istikmal Hisab)",
                detail = "Mendekati ambang 3°/6.4°, peluang rukyat sangat rendah."
            )
            else -> CriteriaVerdict(
                criteria = RukyatCriteria.MABIMS_BARU,
                status = VerdictStatus.TIDAK_MEMENUHI,
                summary = "Istikmal (Di Bawah Ambang)",
                detail = "Masih di bawah kriteria Neo-MABIMS (min 3° & elongasi 6.4°)."
            )
        }

        val wujudul = if (ijtimaBeforeSunset && altHaqiqi > 0.0) {
            CriteriaVerdict(
                criteria = RukyatCriteria.WUJUDUL_HILAL,
                status = VerdictStatus.MEMENUHI,
                summary = "Wujud (Masuk Bulan Baru)",
                detail = "Ijtima' terjadi sebelum Ghurub & tinggi haqiqi (${formatDegSimple(altHaqiqi)}) > 0°."
            )
        } else {
            CriteriaVerdict(
                criteria = RukyatCriteria.WUJUDUL_HILAL,
                status = VerdictStatus.TIDAK_MEMENUHI,
                summary = "Belum Wujud",
                detail = if (!ijtimaBeforeSunset) "Ijtima' belum terjadi saat matahari terbenam."
                else "Posisi bulan masih di bawah ufuk (tinggi ≤ 0°)."
            )
        }

        val mabimsLama = if (ijtimaBeforeSunset && ((altMari >= 2.0 && elongTopo >= 3.0) || moonAgeHours >= 8.0)) {
            CriteriaVerdict(
                criteria = RukyatCriteria.MABIMS_LAMA,
                status = VerdictStatus.MEMENUHI,
                summary = "Memenuhi MABIMS 2-3-8",
                detail = "Memenuhi syarat tinggi ≥ 2° & elongasi ≥ 3° atau umur bulan ≥ 8 jam."
            )
        } else {
            CriteriaVerdict(
                criteria = RukyatCriteria.MABIMS_LAMA,
                status = VerdictStatus.TIDAK_MEMENUHI,
                summary = "Tidak Memenuhi 2-3-8",
                detail = "Belum mencapai ambang MABIMS klasik."
            )
        }

        val odeh = if (ijtimaBeforeSunset && altMari >= 5.0 && elongTopo >= 8.0) {
            CriteriaVerdict(
                criteria = RukyatCriteria.ODEH_GLOBAL,
                status = VerdictStatus.MEMENUHI,
                summary = "Terlihat Mata / Optik",
                detail = "Tinggi ≥ 5° dan elongasi ≥ 8° (visibilitas global optimal)."
            )
        } else if (ijtimaBeforeSunset && altMari >= 3.5 && elongTopo >= 6.4) {
            CriteriaVerdict(
                criteria = RukyatCriteria.ODEH_GLOBAL,
                status = VerdictStatus.MARGINAL,
                summary = "Perlu Teleskop / CCD",
                detail = "Kemungkinan hanya terdeteksi alat optik di langit sangat cerah."
            )
        } else {
            CriteriaVerdict(
                criteria = RukyatCriteria.ODEH_GLOBAL,
                status = VerdictStatus.TIDAK_MEMENUHI,
                summary = "Tidak Terlihat Global",
                detail = "Di bawah batas visibilitas Odeh / Istanbul."
            )
        }

        return mapOf(
            RukyatCriteria.MABIMS_BARU to mabimsBaru,
            RukyatCriteria.WUJUDUL_HILAL to wujudul,
            RukyatCriteria.MABIMS_LAMA to mabimsLama,
            RukyatCriteria.ODEH_GLOBAL to odeh
        )
    }

    fun calculateQiblaAzimuthAndDistance(latDeg: Double, lonDeg: Double): Pair<Double, Double> {
        val lat1 = latDeg * DEG2RAD
        val lon1 = lonDeg * DEG2RAD
        val lat2 = KAABA_LAT * DEG2RAD
        val lon2 = KAABA_LON * DEG2RAD
        val dLon = lon2 - lon1

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = normalizeDeg(atan2(y, x) * RAD2DEG)

        val dLat = lat2 - lat1
        val a = sin(dLat / 2.0).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0).let { it * it }
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        val distanceKm = EARTH_RADIUS_KM * c

        return bearing to distanceKm
    }

    fun calculateIstiwakAndPrayerTimes(
        nowMillis: Long,
        markaz: MarkazLocation
    ): IstiwakAndPrayerResult {
        val localOffsetMillis = (markaz.timezoneOffsetHours * 3_600_000.0).toLong()
        val localCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = nowMillis + localOffsetMillis
        }
        val year = localCal.get(Calendar.YEAR)
        val month = localCal.get(Calendar.MONTH) + 1
        val day = localCal.get(Calendar.DAY_OF_MONTH)

        val jdNow = unixMillisToJd(nowMillis)
        val sunNow = computeSunPosition(jdNow)

        // Meridian timezone (e.g. 105° for WIB, 120° for WITA, 135° for WIT)
        val stdMeridianDeg = markaz.timezoneOffsetHours * 15.0
        val lonCorrectionMinutes = (markaz.longitude - stdMeridianDeg) * 4.0
        val eotMinutes = sunNow.equationOfTimeMinutes

        // Waktu Istiwak (WIS) = Local Standard Time + Longitude Correction + Equation of Time
        val istiwakOffsetMillis = ((lonCorrectionMinutes + eotMinutes) * 60_000.0).toLong()
        val istiwakMillis = nowMillis + istiwakOffsetMillis

        // Sunset today for Ghurubiyah clock & Maghrib
        val sunsetJd = findSunsetJd(year, month, day, markaz)
        val sunsetMillis = jdToUnixMillis(sunsetJd)
        val ghurubiyahDiffMillis = nowMillis - sunsetMillis
        val ghurubiyahNormalized = ((ghurubiyahDiffMillis % 86_400_000L) + 86_400_000L) % 86_400_000L
        val ghurubHour = (ghurubiyahNormalized / 3_600_000L).toInt()
        val ghurubMin = ((ghurubiyahNormalized % 3_600_000L) / 60_000L).toInt()
        val ghurubSec = ((ghurubiyahNormalized % 60_000L) / 1_000L).toInt()
        val ghurubiyahLabel = String.format(Locale.US, "%02d:%02d:%02d", ghurubHour, ghurubMin, ghurubSec)

        // Prayer times for today (Kemenag RI standards + 2 min ihtiyat)
        val noonCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val noonJd = unixMillisToJd(noonCal.timeInMillis) - markaz.timezoneOffsetHours / 24.0
        val sunNoon = computeSunPosition(noonJd)
        val transitUtcHours = 12.0 - markaz.timezoneOffsetHours -
            (markaz.longitude - markaz.timezoneOffsetHours * 15.0) / 15.0 -
            sunNoon.equationOfTimeMinutes / 60.0

        val latR = markaz.latitude * DEG2RAD
        val decR = sunNoon.decDeg * DEG2RAD

        fun hourAngleForSunAltDeg(altDeg: Double): Double {
            val cosHa = ((sin(altDeg * DEG2RAD) - sin(latR) * sin(decR)) /
                (cos(latR) * cos(decR))).coerceIn(-0.999, 0.999)
            return acos(cosHa) * RAD2DEG / 15.0
        }

        val dipDeg = 1.76 * sqrt(markaz.elevationMeters.coerceAtLeast(0.0)) / 60.0
        val subuhHa = hourAngleForSunAltDeg(-20.0)
        val sunriseHa = hourAngleForSunAltDeg(-0.8333 - dipDeg)
        val dhuhaHa = hourAngleForSunAltDeg(4.5)
        val asharAltDeg = atan(1.0 / (1.0 + tan(abs(latR - decR)))) * RAD2DEG
        val asharHa = hourAngleForSunAltDeg(asharAltDeg)
        val maghribHa = sunriseHa
        val isyaHa = hourAngleForSunAltDeg(-18.0)

        val dayStartUtcMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun hoursToMillis(utcHours: Double, ihtiyatMin: Int = 2): Long {
            return dayStartUtcMillis + (utcHours * 3_600_000.0).toLong() + ihtiyatMin * 60_000L
        }

        val subuhMillis = hoursToMillis(transitUtcHours - subuhHa, 2)
        val imsakMillis = subuhMillis - 10 * 60_000L
        val terbitMillis = hoursToMillis(transitUtcHours - sunriseHa, -1)
        val dhuhaMillis = hoursToMillis(transitUtcHours - dhuhaHa, 2)
        val dzuhurMillis = hoursToMillis(transitUtcHours, 2)
        val asharMillis = hoursToMillis(transitUtcHours + asharHa, 2)
        val maghribMillis = hoursToMillis(transitUtcHours + maghribHa, 2)
        val isyaMillis = hoursToMillis(transitUtcHours + isyaHa, 2)

        val rawEntries = listOf(
            Triple("imsak", "Imsak" to "10m sebelum Subuh", imsakMillis),
            Triple("subuh", "Subuh" to "Fajar Shadiq (-20°)", subuhMillis),
            Triple("terbit", "Terbit" to "Syuruq", terbitMillis),
            Triple("dhuha", "Dhuha" to "Matahari +4.5°", dhuhaMillis),
            Triple("dzuhur", "Dzuhur" to "Zawal + Ihtiyat", dzuhurMillis),
            Triple("ashar", "Ashar" to "Bayangan 1x (Syafi'i)", asharMillis),
            Triple("maghrib", "Maghrib" to "Ghurub Syams", maghribMillis),
            Triple("isya", "Isya" to "Hilangnya Syafaq (-18°)", isyaMillis)
        )

        val nextEntry = rawEntries.firstOrNull { it.third > nowMillis }
        val prayerEntries = rawEntries.map { (id, names, millis) ->
            PrayerTimeEntry(
                id = id,
                name = names.first,
                arabicSubtitle = names.second,
                timeMillis = millis,
                timeLabel = formatTimeOnly(millis, markaz.timezoneOffsetHours),
                isNext = nextEntry?.first == id
            )
        }

        val countdownLabel = if (nextEntry != null) {
            val diffSec = ((nextEntry.third - nowMillis) / 1000L).coerceAtLeast(0L)
            val h = diffSec / 3600L
            val m = (diffSec % 3600L) / 60L
            val s = diffSec % 60L
            "Menuju ${nextEntry.second.first}: ${String.format(Locale.US, "%02d:%02d:%02d", h, m, s)}"
        } else {
            "Seluruh jadwal hari ini telah lewat"
        }

        val (qiblaAz, qiblaDist) = calculateQiblaAzimuthAndDistance(markaz.latitude, markaz.longitude)
        val roshdulNote = "Roshdul Qiblah Global: 27–28 Mei (16:18 WIB) & 15–16 Juli (16:27 WIB) — bayangan benda tegak lurus menuju Ka'bah."

        return IstiwakAndPrayerResult(
            markaz = markaz,
            gregorianDateLabel = "${formatDayIndonesian(nowMillis, markaz.timezoneOffsetHours)}, ${formatDateIndonesian(nowMillis, markaz.timezoneOffsetHours)}",
            hijriDateLabel = convertGregorianToTabularHijri(year, month, day),
            pasaranJawa = computePasaranJawa(year, month, day),
            localStandardTimeLabel = "${formatTimeSeconds(nowMillis, markaz.timezoneOffsetHours)} ${markaz.timezoneLabel}",
            waktuIstiwakLabel = "${formatTimeSeconds(istiwakMillis, markaz.timezoneOffsetHours)} WIS",
            waktuGhurubiyahLabel = "$ghurubiyahLabel Ghurubiyah",
            equationOfTimeMinutes = eotMinutes,
            longitudeCorrectionMinutes = lonCorrectionMinutes,
            qiblaAzimuthDeg = qiblaAz,
            qiblaDistanceKm = qiblaDist,
            roshdulQiblahSummary = roshdulNote,
            prayerTimes = prayerEntries,
            nextPrayerCountdown = countdownLabel
        )
    }

    private fun computePasaranJawa(year: Int, month: Int, day: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 12, 0, 0)
        }
        val jdn = floor(unixMillisToJd(cal.timeInMillis) + 0.5).toLong()
        val pasaran = listOf("Legi", "Pahing", "Pon", "Wage", "Kliwon")
        // Reference: 2024-01-01 (JDN 2460311) was Senin Pahing (index 1)
        val idx = (((jdn - 2460311L + 1L) % 5L) + 5L).toInt() % 5
        return pasaran[idx]
    }

    fun convertGregorianToTabularHijri(year: Int, month: Int, day: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 12, 0, 0)
        }
        val jd = floor(unixMillisToJd(cal.timeInMillis) + 0.5).toLong()
        var l = jd - 1948440L + 10632L
        val n = (l - 1L) / 10631L
        l = l - 10631L * n + 354L
        val j = ((10985L - l) / 5316L) * ((50L * l) / 17719L) + (l / 5670L) * ((43L * l) / 15238L)
        l = l - ((30L - j) / 15L) * ((17719L * j) / 50L) - (j / 16L) * ((15238L * j) / 43L) + 29L
        val hMonth = ((24L * l) / 709L).toInt().coerceIn(1, 12)
        val hDay = (l - (709L * hMonth) / 24L).toInt()
        val hYear = (30L * n + j - 30L).toInt()
        val monthName = FalakCatalog.hijriMonths.firstOrNull { it.first == hMonth }?.second ?: "Hijriah"
        return "$hDay $monthName $hYear H"
    }

    fun currentEstimatedHijriYearMonth(nowMillis: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = nowMillis + 7 * 3_600_000L
        }
        val jd = floor(unixMillisToJd(cal.timeInMillis) + 0.5).toLong()
        var l = jd - 1948440L + 10632L
        val n = (l - 1L) / 10631L
        l = l - 10631L * n + 354L
        val j = ((10985L - l) / 5316L) * ((50L * l) / 17719L) + (l / 5670L) * ((43L * l) / 15238L)
        l = l - ((30L - j) / 15L) * ((17719L * j) / 50L) - (j / 16L) * ((15238L * j) / 43L) + 29L
        val hMonth = ((24L * l) / 709L).toInt().coerceIn(1, 12)
        val hYear = (30L * n + j - 30L).toInt()
        return hYear to hMonth
    }

    private fun findFullMoonJdForLunation(k: Int): Double {
        val tApprox = (k + 0.5) / 1236.85
        val jdApprox = NEW_MOON_EPOCH_JD + SYNODIC_MONTH_DAYS * (k + 0.5) +
            0.0001337 * tApprox * tApprox
        var low = jdApprox - 1.2
        var high = jdApprox + 1.2

        fun diffFrom180(jd: Double): Double {
            val sun = computeSunPosition(jd)
            val moon = computeMoonPosition(jd)
            return normalizeHourAngleDeg(moon.apparentLonDeg - sun.apparentLonDeg - 180.0)
        }

        var fLow = diffFrom180(low)
        repeat(32) {
            val mid = (low + high) / 2.0
            val fMid = diffFrom180(mid)
            if ((fLow <= 0.0 && fMid <= 0.0) || (fLow >= 0.0 && fMid >= 0.0)) {
                low = mid
                fLow = fMid
            } else {
                high = mid
            }
        }
        return (low + high) / 2.0
    }

    fun calculateLunarPhase(
        timeMillis: Long,
        markaz: MarkazLocation
    ): LunarPhaseData {
        val jd = unixMillisToJd(timeMillis)
        var k = floor((jd - NEW_MOON_EPOCH_JD) / SYNODIC_MONTH_DAYS).toInt()
        var prevNewMoonJd = findIjtimaJdForLunation(k)
        if (prevNewMoonJd > jd) {
            k -= 1
            prevNewMoonJd = findIjtimaJdForLunation(k)
        }
        var nextNewMoonJd = findIjtimaJdForLunation(k + 1)
        if (nextNewMoonJd <= jd) {
            k += 1
            prevNewMoonJd = nextNewMoonJd
            nextNewMoonJd = findIjtimaJdForLunation(k + 1)
        }

        var nextFullMoonJd = findFullMoonJdForLunation(k)
        if (nextFullMoonJd <= jd) {
            nextFullMoonJd = findFullMoonJdForLunation(k + 1)
        }

        val synodicLength = (nextNewMoonJd - prevNewMoonJd).coerceAtLeast(28.0)
        val lunarAgeDays = (jd - prevNewMoonJd).coerceIn(0.0, synodicLength)
        val cycleFraction = (lunarAgeDays / synodicLength).coerceIn(0.0, 1.0)

        val sun = computeSunPosition(jd)
        val moon = computeMoonPosition(jd)
        val lonDiff = normalizeDeg(moon.apparentLonDeg - sun.apparentLonDeg)
        val isWaxing = lonDiff <= 180.0

        // Geocentric elongation angle (psi) from ecliptic coordinates
        val cosPsi = (cos(moon.apparentLatDeg * DEG2RAD) * cos(lonDiff * DEG2RAD)).coerceIn(-1.0, 1.0)
        val illuminationFraction = (1.0 - cosPsi) / 2.0
        val illuminationPercent = (illuminationFraction * 100.0).coerceIn(0.0, 100.0)
        val angularDiamArcmin = (31.08 * (384400.0 / moon.distanceKm))

        val (phaseIndo, phaseArabic) = when {
            cycleFraction < 0.025 || cycleFraction > 0.975 -> "Bulan Baru (Ijtima')" to "Al-Mahaq / Ijtima'"
            cycleFraction < 0.22 -> "Sabit Muda (Hilal)" to "Al-Hilal"
            cycleFraction < 0.28 -> "Perbani Awal" to "At-Tarbi' al-Awwal"
            cycleFraction < 0.475 -> "Cembung Muda" to "Al-Ahdab al-Mutaqaddim"
            cycleFraction < 0.525 -> "Bulan Purnama" to "Al-Badr"
            cycleFraction < 0.72 -> "Cembung Tua" to "Al-Ahdab al-Mutanaqish"
            cycleFraction < 0.78 -> "Perbani Akhir" to "At-Tarbi' ats-Tsani"
            else -> "Sabit Tua" to "Al-Urjun al-Qadim"
        }

        val localOffsetMillis = (markaz.timezoneOffsetHours * 3_600_000.0).toLong()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timeMillis + localOffsetMillis
        }
        val hijriDateLabel = convertGregorianToTabularHijri(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

        val curvePoints = (0..29).map { dayIdx ->
            val f = dayIdx / 29.0
            val ptJd = prevNewMoonJd + f * synodicLength
            val sPt = computeSunPosition(ptJd)
            val mPt = computeMoonPosition(ptJd)
            val dLon = normalizeDeg(mPt.apparentLonDeg - sPt.apparentLonDeg)
            val cPsi = (cos(mPt.apparentLatDeg * DEG2RAD) * cos(dLon * DEG2RAD)).coerceIn(-1.0, 1.0)
            val illum = ((1.0 - cPsi) / 2.0 * 100.0).toFloat()
            LunarDayPoint(
                dayIndex = dayIdx,
                cycleFraction = f.toFloat(),
                illuminationPercent = illum,
                label = "H+$dayIdx"
            )
        }

        return LunarPhaseData(
            timestampMillis = timeMillis,
            dateLabel = formatDateIndonesian(timeMillis, markaz.timezoneOffsetHours),
            hijriDateLabel = hijriDateLabel,
            lunarAgeDays = lunarAgeDays,
            synodicLengthDays = synodicLength,
            cycleFraction = cycleFraction,
            illuminationPercent = illuminationPercent,
            isWaxing = isWaxing,
            phaseNameIndonesian = phaseIndo,
            phaseNameArabic = phaseArabic,
            earthMoonDistanceKm = moon.distanceKm,
            angularDiameterArcmin = angularDiamArcmin,
            daysToNextNewMoon = (nextNewMoonJd - jd).coerceAtLeast(0.0),
            daysToNextFullMoon = (nextFullMoonJd - jd).coerceAtLeast(0.0),
            nextNewMoonLabel = formatDateTimeShort(jdToUnixMillis(nextNewMoonJd), markaz.timezoneOffsetHours),
            nextFullMoonLabel = formatDateTimeShort(jdToUnixMillis(nextFullMoonJd), markaz.timezoneOffsetHours),
            cycleCurvePoints = curvePoints
        )
    }

    fun formatDms(deg: Double, signed: Boolean = false): String {
        val sign = if (deg < 0) "-" else if (signed) "+" else ""
        val absDeg = abs(deg)
        val d = floor(absDeg).toInt()
        val remMin = (absDeg - d) * 60.0
        val m = floor(remMin).toInt()
        val s = ((remMin - m) * 60.0).roundToInt()
        val (fd, fm, fs) = if (s >= 60) {
            if (m + 1 >= 60) Triple(d + 1, 0, 0) else Triple(d, m + 1, 0)
        } else Triple(d, m, s)
        return String.format(Locale.US, "%s%02d° %02d' %02d\"", sign, fd, fm, fs)
    }

    fun formatDegSimple(deg: Double): String = String.format(Locale.US, "%.2f°", deg)

    private fun tzFromOffset(offsetHours: Double): TimeZone {
        val totalMinutes = (offsetHours * 60.0).roundToInt()
        val sign = if (totalMinutes >= 0) "+" else "-"
        val absM = abs(totalMinutes)
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", sign, absM / 60, absM % 60))
    }

    private fun formatTimeOnly(utcMillis: Long, tzOffsetHours: Double): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tzFromOffset(tzOffsetHours) }
        return sdf.format(Date(utcMillis))
    }

    private fun formatTimeSeconds(utcMillis: Long, tzOffsetHours: Double): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = tzFromOffset(tzOffsetHours) }
        return sdf.format(Date(utcMillis))
    }

    private fun formatDateTimeShort(utcMillis: Long, tzOffsetHours: Double): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID")).apply {
            timeZone = tzFromOffset(tzOffsetHours)
        }
        return sdf.format(Date(utcMillis))
    }

    private fun formatDateIndonesian(utcMillis: Long, tzOffsetHours: Double): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).apply {
            timeZone = tzFromOffset(tzOffsetHours)
        }
        return sdf.format(Date(utcMillis))
    }

    private fun formatDayIndonesian(utcMillis: Long, tzOffsetHours: Double): String {
        val sdf = SimpleDateFormat("EEEE", Locale("id", "ID")).apply {
            timeZone = tzFromOffset(tzOffsetHours)
        }
        return sdf.format(Date(utcMillis))
    }
}
