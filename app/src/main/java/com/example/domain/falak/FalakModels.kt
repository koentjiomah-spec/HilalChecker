package com.example.domain.falak

data class MarkazLocation(
    val id: String,
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val timezoneOffsetHours: Double,
    val timezoneLabel: String,
    val isCustomGps: Boolean = false
)

enum class RukyatCriteria(
    val title: String,
    val shortCode: String,
    val description: String
) {
    MABIMS_BARU(
        title = "Neo-MABIMS (2021)",
        shortCode = "MABIMS Baru",
        description = "Tinggi Hilal ≥ 3° & Elongasi ≥ 6.4° (Kemenag RI, Brunei, Malaysia, Singapura)"
    ),
    WUJUDUL_HILAL(
        title = "Wujudul Hilal",
        shortCode = "Wujudul Hilal",
        description = "Ijtima' terjadi sebelum Ghurub & Tinggi Hilal > 0° saat matahari terbenam"
    ),
    MABIMS_LAMA(
        title = "MABIMS Klasik",
        shortCode = "MABIMS Lama",
        description = "Tinggi ≥ 2° & Elongasi ≥ 3°, atau Umur Bulan ≥ 8 jam setelah Ijtima'"
    ),
    ODEH_GLOBAL(
        title = "Kriteria Odeh / Istanbul",
        shortCode = "Odeh Global",
        description = "Tinggi Toposentris ≥ 5° & Elongasi ≥ 8° untuk keterlihatan global"
    )
}

enum class VerdictStatus {
    MEMENUHI,
    MARGINAL,
    TIDAK_MEMENUHI
}

data class CriteriaVerdict(
    val criteria: RukyatCriteria,
    val status: VerdictStatus,
    val summary: String,
    val detail: String
)

data class HorizonTrackPoint(
    val offsetMinutesFromSunset: Int,
    val timeLabel: String,
    val sunAltitudeDeg: Double,
    val sunAzimuthDeg: Double,
    val moonAltitudeMariDeg: Double,
    val moonAzimuthDeg: Double,
    val elongationDeg: Double
)

data class HilalResult(
    val hijriMonth: Int,
    val hijriMonthName: String,
    val hijriYear: Int,
    val markaz: MarkazLocation,
    val observationDateLabel: String,
    val observationDayLabel: String,
    val ijtimaUtcMillis: Long,
    val ijtimaLocalLabel: String,
    val ijtimaBeforeSunset: Boolean,
    val sunsetUtcMillis: Long,
    val sunsetLocalLabel: String,
    val moonsetUtcMillis: Long?,
    val moonsetLocalLabel: String,
    val mukutsMinutes: Double,
    val moonAltitudeHaqiqiDeg: Double,
    val moonAltitudeMariDeg: Double,
    val sunAltitudeAtSunsetDeg: Double,
    val sunAzimuthDeg: Double,
    val moonAzimuthDeg: Double,
    val azimuthDifferenceDeg: Double,
    val elongationGeoDeg: Double,
    val elongationTopoDeg: Double,
    val moonAgeHours: Double,
    val crescentWidthArcmin: Double,
    val illuminationPercent: Double,
    val crescentTiltDeg: Double,
    val nurulHilalPercent: Double,
    val verdicts: Map<RukyatCriteria, CriteriaVerdict>,
    val horizonTrack: List<HorizonTrackPoint>
)

data class PrayerTimeEntry(
    val id: String,
    val name: String,
    val arabicSubtitle: String,
    val timeMillis: Long,
    val timeLabel: String,
    val isNext: Boolean = false
)

data class IstiwakAndPrayerResult(
    val markaz: MarkazLocation,
    val gregorianDateLabel: String,
    val hijriDateLabel: String,
    val pasaranJawa: String,
    val localStandardTimeLabel: String,
    val waktuIstiwakLabel: String,
    val waktuGhurubiyahLabel: String,
    val equationOfTimeMinutes: Double,
    val longitudeCorrectionMinutes: Double,
    val qiblaAzimuthDeg: Double,
    val qiblaDistanceKm: Double,
    val roshdulQiblahSummary: String,
    val prayerTimes: List<PrayerTimeEntry>,
    val nextPrayerCountdown: String
)

data class LunarDayPoint(
    val dayIndex: Int,
    val cycleFraction: Float,
    val illuminationPercent: Float,
    val label: String
)

data class LunarPhaseData(
    val timestampMillis: Long,
    val dateLabel: String,
    val hijriDateLabel: String,
    val lunarAgeDays: Double,
    val synodicLengthDays: Double,
    val cycleFraction: Double,
    val illuminationPercent: Double,
    val isWaxing: Boolean,
    val phaseNameIndonesian: String,
    val phaseNameArabic: String,
    val earthMoonDistanceKm: Double,
    val angularDiameterArcmin: Double,
    val daysToNextNewMoon: Double,
    val daysToNextFullMoon: Double,
    val nextNewMoonLabel: String,
    val nextFullMoonLabel: String,
    val cycleCurvePoints: List<LunarDayPoint>
)

object FalakCatalog {
    val hijriMonths = listOf(
        1 to "Muharram",
        2 to "Safar",
        3 to "Rabi'ul Awwal",
        4 to "Rabi'ul Akhir",
        5 to "Jumadil Ula",
        6 to "Jumadil Akhirah",
        7 to "Rajab",
        8 to "Sya'ban",
        9 to "Ramadhan",
        10 to "Syawal",
        11 to "Dzulqa'dah",
        12 to "Dzulhijjah"
    )

    val markazStations = listOf(
        MarkazLocation(
            id = "pelabuhan_ratu",
            name = "POS Observasi Pelabuhan Ratu",
            region = "Sukabumi, Jawa Barat",
            latitude = -6.9875,
            longitude = 106.5442,
            elevationMeters = 52.0,
            timezoneOffsetHours = 7.0,
            timezoneLabel = "WIB"
        ),
        MarkazLocation(
            id = "condrodipo",
            name = "Bukit Condrodipo",
            region = "Gresik, Jawa Timur",
            latitude = -7.1714,
            longitude = 112.6164,
            elevationMeters = 120.0,
            timezoneOffsetHours = 7.0,
            timezoneLabel = "WIB"
        ),
        MarkazLocation(
            id = "lhoknga",
            name = "Observatorium Tgk. Chiek Kuta Karang",
            region = "Lhoknga, Aceh Besar",
            latitude = 5.4658,
            longitude = 95.2422,
            elevationMeters = 35.0,
            timezoneOffsetHours = 7.0,
            timezoneLabel = "WIB"
        ),
        MarkazLocation(
            id = "bosscha",
            name = "Observatorium Bosscha",
            region = "Lembang, Bandung Barat",
            latitude = -6.8247,
            longitude = 107.6156,
            elevationMeters = 1310.0,
            timezoneOffsetHours = 7.0,
            timezoneLabel = "WIB"
        ),
        MarkazLocation(
            id = "sidogiri",
            name = "Markaz Falak Sidogiri",
            region = "Pasuruan, Jawa Timur",
            latitude = -7.6844,
            longitude = 112.8639,
            elevationMeters = 15.0,
            timezoneOffsetHours = 7.0,
            timezoneLabel = "WIB"
        ),
        MarkazLocation(
            id = "belabelu",
            name = "Bukit Syekh Bela-Belu",
            region = "Bantul, DI Yogyakarta",
            latitude = -8.0197,
            longitude = 110.3208,
            elevationMeters = 85.0,
            timezoneOffsetHours = 7.0,
            timezoneLabel = "WIB"
        ),
        MarkazLocation(
            id = "istiqlal",
            name = "Menara Masjid Istiqlal",
            region = "DKI Jakarta",
            latitude = -6.1702,
            longitude = 106.8314,
            elevationMeters = 25.0,
            timezoneOffsetHours = 7.0,
            timezoneLabel = "WIB"
        ),
        MarkazLocation(
            id = "loang_baloq",
            name = "Pantai Loang Baloq",
            region = "Mataram, NTB",
            latitude = -8.6042,
            longitude = 116.0739,
            elevationMeters = 10.0,
            timezoneOffsetHours = 8.0,
            timezoneLabel = "WITA"
        ),
        MarkazLocation(
            id = "makassar",
            name = "Anjungan Losari / GTC",
            region = "Makassar, Sulawesi Selatan",
            latitude = -5.1442,
            longitude = 119.4067,
            elevationMeters = 12.0,
            timezoneOffsetHours = 8.0,
            timezoneLabel = "WITA"
        ),
        MarkazLocation(
            id = "manado",
            name = "Pantai Malalayang",
            region = "Manado, Sulawesi Utara",
            latitude = 1.4581,
            longitude = 124.8031,
            elevationMeters = 10.0,
            timezoneOffsetHours = 8.0,
            timezoneLabel = "WITA"
        ),
        MarkazLocation(
            id = "kupang",
            name = "Menara Rukyat Kupang",
            region = "Kupang, NTT",
            latitude = -10.1583,
            longitude = 123.5972,
            elevationMeters = 45.0,
            timezoneOffsetHours = 8.0,
            timezoneLabel = "WITA"
        ),
        MarkazLocation(
            id = "merauke",
            name = "Pantai Lampu Satu",
            region = "Merauke, Papua Selatan",
            latitude = -8.5117,
            longitude = 140.3914,
            elevationMeters = 8.0,
            timezoneOffsetHours = 9.0,
            timezoneLabel = "WIT"
        )
    )
}
