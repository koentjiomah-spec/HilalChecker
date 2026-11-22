package com.example.domain.falak

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor

data class HijriDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val monthName: String
) {
    fun formatFull(): String = "$day $monthName $year H"
}

enum class HijriEventType(val label: String) {
    MAJOR_HOLIDAY("Hari Besar Islam"),
    SUNNAH_FASTING("Puasa Sunnah"),
    RUKYAT_OBSERVATION("Observasi Hilal")
}

data class HijriKeyEvent(
    val hijriDay: Int,
    val hijriMonth: Int,
    val hijriYear: Int,
    val title: String,
    val description: String,
    val type: HijriEventType,
    val gregorianDateLabel: String,
    val dayOfWeekLabel: String,
    val isTodayOrUpcoming: Boolean,
    val daysFromToday: Int
)

data class HijriCalendarDayCell(
    val hijriDay: Int,
    val hijriMonth: Int,
    val hijriYear: Int,
    val gregorianYear: Int,
    val gregorianMonth: Int,
    val gregorianDay: Int,
    val gregorianDateLabel: String,
    val dayOfWeek: Int, // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
    val dayOfWeekLabel: String,
    val pasaranJawa: String,
    val isToday: Boolean,
    val keyEvent: HijriKeyEvent?,
    val isSunnahMonThu: Boolean
)

data class HijriMonthCalendar(
    val hijriYear: Int,
    val hijriMonth: Int,
    val hijriMonthName: String,
    val todayHijri: HijriDate,
    val daysInMonth: Int,
    val startDayOfWeekOffset: Int, // 0 = Sunday .. 6 = Saturday
    val cells: List<HijriCalendarDayCell>,
    val keyEvents: List<HijriKeyEvent>,
    val upcomingKeyEvents: List<HijriKeyEvent>,
    val upcomingPrayerEvents: List<PrayerTimeEntry>,
    val nextPrayerCountdown: String,
    val gregorianSpanLabel: String
)

object HijriCalendarEngine {

    private val indonesianLocale = Locale.forLanguageTag("id-ID")
    private val pasaranList = listOf("Legi", "Pahing", "Pon", "Wage", "Kliwon")
    private val dayNamesId = mapOf(
        Calendar.SUNDAY to "Ahad",
        Calendar.MONDAY to "Senin",
        Calendar.TUESDAY to "Selasa",
        Calendar.WEDNESDAY to "Rabu",
        Calendar.THURSDAY to "Kamis",
        Calendar.FRIDAY to "Jumat",
        Calendar.SATURDAY to "Sabtu"
    )
    private val monthShortId = listOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    fun buildMonthCalendar(
        hijriYear: Int,
        hijriMonth: Int,
        markaz: MarkazLocation,
        nowMillis: Long = System.currentTimeMillis()
    ): HijriMonthCalendar {
        val localOffsetMillis = (markaz.timezoneOffsetHours * 3_600_000.0).toLong()
        val nowLocalCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = nowMillis + localOffsetMillis
        }
        val todayGYear = nowLocalCal.get(Calendar.YEAR)
        val todayGMonth = nowLocalCal.get(Calendar.MONTH) + 1
        val todayGDay = nowLocalCal.get(Calendar.DAY_OF_MONTH)
        val todayJdn = gregorianToJdn(todayGYear, todayGMonth, todayGDay)
        val todayHijri = jdnToHijri(todayJdn)

        val monthStartJdn = hijriToJdn(hijriYear, hijriMonth, 1)
        val nextMonthYear = if (hijriMonth == 12) hijriYear + 1 else hijriYear
        val nextMonth = if (hijriMonth == 12) 1 else hijriMonth + 1
        val nextMonthStartJdn = hijriToJdn(nextMonthYear, nextMonth, 1)
        val daysInMonth = (nextMonthStartJdn - monthStartJdn).toInt().coerceIn(29, 30)

        val monthName = FalakCatalog.hijriMonths.firstOrNull { it.first == hijriMonth }?.second ?: "Hijriah"

        val cells = mutableListOf<HijriCalendarDayCell>()
        val keyEvents = mutableListOf<HijriKeyEvent>()

        for (hDay in 1..daysInMonth) {
            val jdn = monthStartJdn + (hDay - 1)
            val (gY, gM, gD) = jdnToGregorian(jdn)
            val dow = jdnToDayOfWeek(jdn)
            val dowLabel = dayNamesId[dow] ?: ""
            val pasaran = pasaranList[(((jdn - 2460311L + 1L) % 5L) + 5L).toInt() % 5]
            val gDateLabel = "$gD ${monthShortId[(gM - 1).coerceIn(0, 11)]} $gY"
            val daysFromToday = (jdn - todayJdn).toInt()
            val isToday = daysFromToday == 0

            val rawEvent = eventForHijriDate(hijriYear, hijriMonth, hDay, monthName)
            val keyEvent = rawEvent?.let { (title, desc, type) ->
                HijriKeyEvent(
                    hijriDay = hDay,
                    hijriMonth = hijriMonth,
                    hijriYear = hijriYear,
                    title = title,
                    description = desc,
                    type = type,
                    gregorianDateLabel = gDateLabel,
                    dayOfWeekLabel = dowLabel,
                    isTodayOrUpcoming = daysFromToday >= 0,
                    daysFromToday = daysFromToday
                )
            }
            if (keyEvent != null) {
                keyEvents += keyEvent
            }

            cells += HijriCalendarDayCell(
                hijriDay = hDay,
                hijriMonth = hijriMonth,
                hijriYear = hijriYear,
                gregorianYear = gY,
                gregorianMonth = gM,
                gregorianDay = gD,
                gregorianDateLabel = gDateLabel,
                dayOfWeek = dow,
                dayOfWeekLabel = dowLabel,
                pasaranJawa = pasaran,
                isToday = isToday,
                keyEvent = keyEvent,
                isSunnahMonThu = (dow == Calendar.MONDAY || dow == Calendar.THURSDAY) &&
                    !(hijriMonth == 10 && hDay == 1) &&
                    !(hijriMonth == 12 && hDay in 10..13)
            )
        }

        val startOffset = (cells.first().dayOfWeek - Calendar.SUNDAY).coerceIn(0, 6)
        val firstCell = cells.first()
        val lastCell = cells.last()
        val gregorianSpan = "${firstCell.gregorianDateLabel} – ${lastCell.gregorianDateLabel}"

        val istiwakToday = FalakEphemerisEngine.calculateIstiwakAndPrayerTimes(nowMillis, markaz)
        val upcomingPrayers = buildUpcomingPrayerEvents(istiwakToday.prayerTimes, nowMillis, markaz)
        val upcomingKeyEvents = keyEvents.filter { it.daysFromToday >= 0 }
            .ifEmpty { keyEvents }

        return HijriMonthCalendar(
            hijriYear = hijriYear,
            hijriMonth = hijriMonth,
            hijriMonthName = monthName,
            todayHijri = todayHijri,
            daysInMonth = daysInMonth,
            startDayOfWeekOffset = startOffset,
            cells = cells,
            keyEvents = keyEvents,
            upcomingKeyEvents = upcomingKeyEvents,
            upcomingPrayerEvents = upcomingPrayers,
            nextPrayerCountdown = istiwakToday.nextPrayerCountdown,
            gregorianSpanLabel = gregorianSpan
        )
    }

    fun calculatePrayerEventsForDay(
        gregorianYear: Int,
        gregorianMonth: Int,
        gregorianDay: Int,
        markaz: MarkazLocation
    ): List<PrayerTimeEntry> {
        val localMiddayMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(gregorianYear, gregorianMonth - 1, gregorianDay, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis - (markaz.timezoneOffsetHours * 3_600_000.0).toLong()
        return FalakEphemerisEngine.calculateIstiwakAndPrayerTimes(localMiddayMillis, markaz).prayerTimes
    }

    fun gregorianToHijri(year: Int, month: Int, day: Int): HijriDate {
        return jdnToHijri(gregorianToJdn(year, month, day))
    }

    fun hijriToJdn(hijriYear: Int, hijriMonth: Int, hijriDay: Int): Long {
        val y = hijriYear.toLong()
        val m = hijriMonth.toLong()
        val d = hijriDay.toLong()
        return ((11L * y + 3L) / 30L) +
            354L * y +
            30L * m -
            ((m - 1L) / 2L) +
            d +
            1948440L - 385L
    }

    fun jdnToHijri(jdn: Long): HijriDate {
        var l = jdn - 1948440L + 10632L
        val n = (l - 1L) / 10631L
        l = l - 10631L * n + 354L
        val j = ((10985L - l) / 5316L) * ((50L * l) / 17719L) + (l / 5670L) * ((43L * l) / 15238L)
        l = l - ((30L - j) / 15L) * ((17719L * j) / 50L) - (j / 16L) * ((15238L * j) / 43L) + 29L
        val hMonth = ((24L * l) / 709L).toInt().coerceIn(1, 12)
        val hDay = (l - (709L * hMonth) / 24L).toInt()
        val hYear = (30L * n + j - 30L).toInt()
        val monthName = FalakCatalog.hijriMonths.firstOrNull { it.first == hMonth }?.second ?: "Hijriah"
        return HijriDate(hYear, hMonth, hDay, monthName)
    }

    fun gregorianToJdn(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return floor(FalakEphemerisEngine.unixMillisToJd(cal.timeInMillis) + 0.5).toLong()
    }

    fun jdnToGregorian(jdn: Long): Triple<Int, Int, Int> {
        // Fliegel-Van Flandern algorithm (exact Gregorian JDN -> Y, M, D)
        var l = jdn + 68569L
        val n = (4L * l) / 146097L
        l -= (146097L * n + 3L) / 4L
        val i = (4000L * (l + 1L)) / 1461001L
        l = l - (1461L * i) / 4L + 31L
        val j = (80L * l) / 2447L
        val day = (l - (2447L * j) / 80L).toInt()
        l = j / 11L
        val month = (j + 2L - 12L * l).toInt()
        val year = (100L * (n - 49L) + i + l).toInt()
        return Triple(year, month, day)
    }

    private fun jdnToDayOfWeek(jdn: Long): Int {
        // JDN 0 was Monday (Calendar.MONDAY = 2); (jdn + 1) % 7 -> 0=Sun..6=Sat -> +1 gives Calendar.SUNDAY..SATURDAY
        return (((jdn + 1L) % 7L + 7L) % 7L).toInt() + 1
    }

    private fun buildUpcomingPrayerEvents(
        todayPrayers: List<PrayerTimeEntry>,
        nowMillis: Long,
        markaz: MarkazLocation
    ): List<PrayerTimeEntry> {
        val remainingToday = todayPrayers.filter { it.timeMillis >= nowMillis }
        if (remainingToday.size >= 4) {
            return remainingToday
        }
        val tomorrowPrayers = FalakEphemerisEngine.calculateIstiwakAndPrayerTimes(
            nowMillis + 86_400_000L,
            markaz
        ).prayerTimes.map {
            it.copy(
                name = "${it.name} (Besok)",
                isNext = remainingToday.isEmpty() && it.id == "imsak"
            )
        }
        return (remainingToday + tomorrowPrayers).take(6)
    }

    private fun eventForHijriDate(
        hijriYear: Int,
        hijriMonth: Int,
        hijriDay: Int,
        monthName: String
    ): Triple<String, String, HijriEventType>? {
        return when (hijriMonth to hijriDay) {
            1 to 1 -> Triple(
                "Tahun Baru Islam 1 Muharram",
                "Awal tahun baru Hijriah $hijriYear H & doa awal tahun.",
                HijriEventType.MAJOR_HOLIDAY
            )
            1 to 9 -> Triple(
                "Puasa Sunnah Tasu'a",
                "Puasa sunnah 9 Muharram mengiringi hari Asyura.",
                HijriEventType.SUNNAH_FASTING
            )
            1 to 10 -> Triple(
                "Hari Asyura (10 Muharram)",
                "Puasa sunnah Asyura dengan keutamaan penghapus dosa setahun lalu.",
                HijriEventType.MAJOR_HOLIDAY
            )
            3 to 12 -> Triple(
                "Maulid Nabi Muhammad SAW",
                "Peringatan hari kelahiran Nabi Muhammad SAW (12 Rabi'ul Awwal).",
                HijriEventType.MAJOR_HOLIDAY
            )
            7 to 27 -> Triple(
                "Isra' Mi'raj Nabi Muhammad SAW",
                "Peringatan perjalanan Isra' Mi'raj & perintah sholat lima waktu.",
                HijriEventType.MAJOR_HOLIDAY
            )
            8 to 15 -> Triple(
                "Nisfu Sya'ban & Ayyamul Bidh",
                "Pertengahan bulan Sya'ban (hari ke-3 puasa sunnah Ayyamul Bidh).",
                HijriEventType.MAJOR_HOLIDAY
            )
            9 to 1 -> Triple(
                "Awal Puasa Ramadhan",
                "Permulaan bulan suci Ramadhan $hijriYear H.",
                HijriEventType.MAJOR_HOLIDAY
            )
            9 to 17 -> Triple(
                "Nuzulul Qur'an (17 Ramadhan)",
                "Peringatan turunnya wahyu Al-Qur'an pertama kali.",
                HijriEventType.MAJOR_HOLIDAY
            )
            9 to 27 -> Triple(
                "10 Malam Akhir Ramadhan",
                "Ikhtiar menggapai malam mulia Lailatul Qadar.",
                HijriEventType.MAJOR_HOLIDAY
            )
            10 to 1 -> Triple(
                "Hari Raya Idul Fitri",
                "1 Syawal $hijriYear H — Sholat Id & haram berpuasa pada hari raya.",
                HijriEventType.MAJOR_HOLIDAY
            )
            10 to 2 -> Triple(
                "Puasa Sunnah Syawal",
                "Permulaan anjuran puasa sunnah 6 hari di bulan Syawal.",
                HijriEventType.SUNNAH_FASTING
            )
            12 to 8 -> Triple(
                "Hari Tarwiyah (8 Dzulhijjah)",
                "Puasa sunnah Tarwiyah menjelang wukuf di Arafah.",
                HijriEventType.SUNNAH_FASTING
            )
            12 to 9 -> Triple(
                "Hari Arafah (9 Dzulhijjah)",
                "Puasa sunnah Arafah bagi yang tidak berhaji & takbir muqayyad.",
                HijriEventType.MAJOR_HOLIDAY
            )
            12 to 10 -> Triple(
                "Hari Raya Idul Adha",
                "10 Dzulhijjah $hijriYear H — Sholat Idul Adha & penyembelihan kurban.",
                HijriEventType.MAJOR_HOLIDAY
            )
            12 to 11, 12 to 12, 12 to 13 -> Triple(
                "Hari Tasyrik (${hijriDay} Dzulhijjah)",
                "Hari makan, minum & berzikir; dilarang berpuasa.",
                HijriEventType.MAJOR_HOLIDAY
            )
            else -> when (hijriDay) {
                1 -> Triple(
                    "Awal Bulan $monthName",
                    "Hari pertama bulan $monthName $hijriYear H.",
                    HijriEventType.RUKYAT_OBSERVATION
                )
                13, 14, 15 -> Triple(
                    "Puasa Ayyamul Bidh ($hijriDay $monthName)",
                    "Puasa sunnah tiga hari pertengahan bulan Qamariyah.",
                    HijriEventType.SUNNAH_FASTING
                )
                29 -> Triple(
                    "Rukyatul Hilal (29 $monthName)",
                    "Petang hari ke-29 dilakukan rukyat/hisab penentuan awal bulan berikutnya.",
                    HijriEventType.RUKYAT_OBSERVATION
                )
                else -> null
            }
        }
    }

    fun formatDaysDelta(daysFromToday: Int): String = when {
        daysFromToday == 0 -> "Hari Ini"
        daysFromToday == 1 -> "Besok"
        daysFromToday == -1 -> "Kemarin"
        daysFromToday > 1 -> "$daysFromToday hari lagi"
        else -> "${-daysFromToday} hari lalu"
    }

    fun formatCurrentMonthYear(hijriYear: Int, hijriMonth: Int): String {
        val monthName = FalakCatalog.hijriMonths.firstOrNull { it.first == hijriMonth }?.second ?: "Hijriah"
        return "$monthName $hijriYear H"
    }
}
