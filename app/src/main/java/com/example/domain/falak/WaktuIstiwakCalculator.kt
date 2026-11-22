package com.example.domain.falak

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

data class TrueNoonIstiwakResult(
    val markaz: MarkazLocation,
    val dateLabel: String,
    val hijriDateLabel: String,
    val trueNoonUtcMillis: Long,
    val trueNoonLocalLabel: String,
    val trueNoonUtcLabel: String,
    val dzuhurIhtiyatLabel: String,
    val standardMeridianDeg: Double,
    val longitudeCorrectionMinutes: Double,
    val equationOfTimeMinutes: Double,
    val totalWisOffsetMinutes: Double,
    val solarDeclinationDeg: Double,
    val solarNoonAltitudeDeg: Double,
    val zenithDistanceDeg: Double,
    val shadowDirectionAtNoon: String,
    val wisVerificationLabel: String
)

/**
 * Traditional Islamic Timekeeping (Ilmu Falak) calculator for True Solar Noon (Istiwak / Zawal)
 * and Waktu Istiwak (WIS) based on the user's GPS coordinates.
 */
object WaktuIstiwakCalculator {

    /**
     * Computes exact True Solar Noon (Istiwak = 12:00:00 WIS) in Local Standard Time and UTC
     * for the given calendar date and observer's [markaz] coordinates.
     */
    fun calculateTrueNoon(
        localYear: Int,
        localMonth: Int,
        localDay: Int,
        markaz: MarkazLocation
    ): TrueNoonIstiwakResult {
        val dayStartUtcMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(localYear, localMonth - 1, localDay, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val stdMeridianDeg = markaz.timezoneOffsetHours * 15.0
        val lonCorrMinutes = (markaz.longitude - stdMeridianDeg) * 4.0

        // Iterative solar transit solver (3 passes converge to < 0.01 seconds)
        var transitUtcHours = 12.0 - markaz.timezoneOffsetHours - lonCorrMinutes / 60.0
        var sunAtNoon = FalakEphemerisEngine.computeSunPosition(
            FalakEphemerisEngine.unixMillisToJd(
                dayStartUtcMillis + (transitUtcHours * 3_600_000.0).toLong()
            )
        )
        repeat(3) {
            transitUtcHours = 12.0 - markaz.timezoneOffsetHours -
                (lonCorrMinutes + sunAtNoon.equationOfTimeMinutes) / 60.0
            val candidateMillis = dayStartUtcMillis + (transitUtcHours * 3_600_000.0).roundToLong()
            sunAtNoon = FalakEphemerisEngine.computeSunPosition(
                FalakEphemerisEngine.unixMillisToJd(candidateMillis)
            )
        }

        val trueNoonUtcMillis = dayStartUtcMillis + (transitUtcHours * 3_600_000.0).roundToLong()
        val dzuhurIhtiyatMillis = trueNoonUtcMillis + 120_000L // +2 min ihtiyat

        val eotMinutes = sunAtNoon.equationOfTimeMinutes
        val totalWisOffsetMinutes = lonCorrMinutes + eotMinutes

        val zenithDistanceDeg = abs(markaz.latitude - sunAtNoon.decDeg)
        val solarNoonAltitudeDeg = (90.0 - zenithDistanceDeg).coerceIn(0.0, 90.0)

        val shadowDirection = when {
            zenithDistanceDeg < 0.25 -> "Bayangan tepat di bawah benda (Yaum al-Istiwa')"
            markaz.latitude < sunAtNoon.decDeg -> "Bayangan mengarah ke Selatan"
            else -> "Bayangan mengarah ke Utara"
        }

        // Verify that converting trueNoonUtcMillis to WIS yields 12:00:00 WIS
        val wisVerifyMillis = trueNoonUtcMillis + (totalWisOffsetMinutes * 60_000.0).roundToLong()

        val localTz = tzFromOffset(markaz.timezoneOffsetHours)
        val utcTz = TimeZone.getTimeZone("UTC")
        val timeFmtLocal = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = localTz }
        val timeFmtUtc = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = utcTz }
        val dateFmt = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.forLanguageTag("id-ID")).apply {
            timeZone = localTz
        }

        return TrueNoonIstiwakResult(
            markaz = markaz,
            dateLabel = dateFmt.format(Date(trueNoonUtcMillis)),
            hijriDateLabel = FalakEphemerisEngine.convertGregorianToTabularHijri(localYear, localMonth, localDay),
            trueNoonUtcMillis = trueNoonUtcMillis,
            trueNoonLocalLabel = "${timeFmtLocal.format(Date(trueNoonUtcMillis))} ${markaz.timezoneLabel}",
            trueNoonUtcLabel = "${timeFmtUtc.format(Date(trueNoonUtcMillis))} UTC",
            dzuhurIhtiyatLabel = "${timeFmtLocal.format(Date(dzuhurIhtiyatMillis))} ${markaz.timezoneLabel}",
            standardMeridianDeg = stdMeridianDeg,
            longitudeCorrectionMinutes = lonCorrMinutes,
            equationOfTimeMinutes = eotMinutes,
            totalWisOffsetMinutes = totalWisOffsetMinutes,
            solarDeclinationDeg = sunAtNoon.decDeg,
            solarNoonAltitudeDeg = solarNoonAltitudeDeg,
            zenithDistanceDeg = zenithDistanceDeg,
            shadowDirectionAtNoon = shadowDirection,
            wisVerificationLabel = "${timeFmtLocal.format(Date(wisVerifyMillis))} WIS"
        )
    }

    fun calculateTrueNoonForDayOffset(
        baseUtcMillis: Long,
        dayOffset: Int,
        markaz: MarkazLocation
    ): TrueNoonIstiwakResult {
        val localOffsetMillis = (markaz.timezoneOffsetHours * 3_600_000.0).toLong()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = baseUtcMillis + localOffsetMillis + dayOffset * 86_400_000L
        }
        return calculateTrueNoon(
            localYear = cal.get(Calendar.YEAR),
            localMonth = cal.get(Calendar.MONTH) + 1,
            localDay = cal.get(Calendar.DAY_OF_MONTH),
            markaz = markaz
        )
    }

    fun formatOffsetMinSec(offsetMinutes: Double): String {
        val sign = if (offsetMinutes >= 0) "+" else "-"
        val totalSeconds = (abs(offsetMinutes) * 60.0).roundToInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%s%02dm %02ds", sign, m, s)
    }

    private fun tzFromOffset(offsetHours: Double): TimeZone {
        val totalMinutes = (offsetHours * 60.0).roundToInt()
        val sign = if (totalMinutes >= 0) "+" else "-"
        val absM = abs(totalMinutes)
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", sign, absM / 60, absM % 60))
    }

    private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
}
