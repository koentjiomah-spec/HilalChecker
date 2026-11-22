package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.falak.FalakCatalog
import com.example.domain.falak.HisabMethodCategory
import com.example.domain.falak.MarkazLocation
import com.example.domain.falak.RukyatCriteria
import com.example.domain.falak.TimezonePreferenceMode
import com.example.domain.falak.formatUtcOffsetLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.falakDataStore: DataStore<Preferences> by preferencesDataStore(name = "hilal_checker_prefs")

data class UserFalakPreferences(
    val markaz: MarkazLocation,
    val baseMarkaz: MarkazLocation,
    val selectedCriteria: RukyatCriteria,
    val lastRukyatCriteria: RukyatCriteria,
    val timezoneMode: TimezonePreferenceMode,
    val customTimezoneOffsetHours: Double,
    val isDarkTheme: Boolean
)

class FalakPreferencesRepository(private val context: Context) {

    private object Keys {
        val MARKAZ_ID = stringPreferencesKey("markaz_id")
        val CUSTOM_NAME = stringPreferencesKey("custom_name")
        val CUSTOM_LAT = doublePreferencesKey("custom_lat")
        val CUSTOM_LON = doublePreferencesKey("custom_lon")
        val CUSTOM_ELEV = doublePreferencesKey("custom_elev")
        val CRITERIA = stringPreferencesKey("selected_criteria")
        val LAST_RUKYAT_CRITERIA = stringPreferencesKey("last_rukyat_criteria")
        val TIMEZONE_MODE = stringPreferencesKey("timezone_mode")
        val CUSTOM_TIMEZONE_OFFSET = doublePreferencesKey("custom_timezone_offset")
        val DARK_THEME = booleanPreferencesKey("is_dark_theme")
    }

    val preferencesFlow: Flow<UserFalakPreferences> = context.falakDataStore.data.map { prefs ->
        val markazId = prefs[Keys.MARKAZ_ID] ?: FalakCatalog.markazStations.first().id
        val baseMarkaz = if (markazId == "custom_gps" || markazId == "custom_manual") {
            val lat = prefs[Keys.CUSTOM_LAT] ?: -6.9875
            val lon = prefs[Keys.CUSTOM_LON] ?: 106.5442
            val elev = prefs[Keys.CUSTOM_ELEV] ?: 15.0
            val tzHours = inferIndonesianOrApproxTimezone(lon)
            MarkazLocation(
                id = markazId,
                name = prefs[Keys.CUSTOM_NAME] ?: "Lokasi Kustom",
                region = if (markazId == "custom_gps") "Koordinat GPS Perangkat" else "Koordinat Manual",
                latitude = lat,
                longitude = lon,
                elevationMeters = elev,
                timezoneOffsetHours = tzHours.first,
                timezoneLabel = tzHours.second,
                isCustomGps = markazId == "custom_gps"
            )
        } else {
            FalakCatalog.markazStations.firstOrNull { it.id == markazId }
                ?: FalakCatalog.markazStations.first()
        }

        val criteriaName = prefs[Keys.CRITERIA] ?: RukyatCriteria.MABIMS_BARU.name
        val criteria = RukyatCriteria.entries.firstOrNull { it.name == criteriaName }
            ?: RukyatCriteria.MABIMS_BARU

        val lastRukyatName = prefs[Keys.LAST_RUKYAT_CRITERIA] ?: RukyatCriteria.MABIMS_BARU.name
        val lastRukyat = RukyatCriteria.entries
            .firstOrNull { it.name == lastRukyatName && it != RukyatCriteria.WUJUDUL_HILAL }
            ?: RukyatCriteria.MABIMS_BARU

        val tzModeName = prefs[Keys.TIMEZONE_MODE] ?: TimezonePreferenceMode.AUTO_MARKAZ.name
        val tzMode = TimezonePreferenceMode.entries.firstOrNull { it.name == tzModeName }
            ?: TimezonePreferenceMode.AUTO_MARKAZ

        val customTzOffset = sanitizeHalfHourOffset(
            prefs[Keys.CUSTOM_TIMEZONE_OFFSET] ?: baseMarkaz.timezoneOffsetHours
        )

        val effectiveMarkaz = when (tzMode) {
            TimezonePreferenceMode.AUTO_MARKAZ -> baseMarkaz
            TimezonePreferenceMode.CUSTOM -> baseMarkaz.copy(
                timezoneOffsetHours = customTzOffset,
                timezoneLabel = formatUtcOffsetLabel(customTzOffset)
            )
            else -> baseMarkaz.copy(
                timezoneOffsetHours = tzMode.fixedOffsetHours ?: baseMarkaz.timezoneOffsetHours,
                timezoneLabel = tzMode.fixedLabel ?: baseMarkaz.timezoneLabel
            )
        }

        val darkTheme = prefs[Keys.DARK_THEME] ?: true
        UserFalakPreferences(
            markaz = effectiveMarkaz,
            baseMarkaz = baseMarkaz,
            selectedCriteria = criteria,
            lastRukyatCriteria = lastRukyat,
            timezoneMode = tzMode,
            customTimezoneOffsetHours = customTzOffset,
            isDarkTheme = darkTheme
        )
    }

    suspend fun selectPresetMarkaz(markazId: String) {
        context.falakDataStore.edit { prefs ->
            prefs[Keys.MARKAZ_ID] = markazId
        }
    }

    suspend fun saveCustomMarkaz(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double
    ) {
        context.falakDataStore.edit { prefs ->
            prefs[Keys.MARKAZ_ID] = id
            prefs[Keys.CUSTOM_NAME] = name
            prefs[Keys.CUSTOM_LAT] = latitude
            prefs[Keys.CUSTOM_LON] = longitude
            prefs[Keys.CUSTOM_ELEV] = elevationMeters
        }
    }

    suspend fun setCriteria(criteria: RukyatCriteria) {
        context.falakDataStore.edit { prefs ->
            prefs[Keys.CRITERIA] = criteria.name
            if (criteria != RukyatCriteria.WUJUDUL_HILAL) {
                prefs[Keys.LAST_RUKYAT_CRITERIA] = criteria.name
            }
        }
    }

    suspend fun setHisabMethodCategory(category: HisabMethodCategory) {
        context.falakDataStore.edit { prefs ->
            when (category) {
                HisabMethodCategory.WUJUDUL_HILAL -> {
                    prefs[Keys.CRITERIA] = RukyatCriteria.WUJUDUL_HILAL.name
                }
                HisabMethodCategory.RUKYATUL_HILAL -> {
                    val lastName = prefs[Keys.LAST_RUKYAT_CRITERIA]
                    val restored = RukyatCriteria.entries.firstOrNull {
                        it.name == lastName && it != RukyatCriteria.WUJUDUL_HILAL
                    } ?: RukyatCriteria.MABIMS_BARU
                    prefs[Keys.CRITERIA] = restored.name
                    prefs[Keys.LAST_RUKYAT_CRITERIA] = restored.name
                }
            }
        }
    }

    suspend fun setTimezoneMode(mode: TimezonePreferenceMode) {
        context.falakDataStore.edit { prefs ->
            prefs[Keys.TIMEZONE_MODE] = mode.name
        }
    }

    suspend fun setCustomTimezoneOffset(offsetHours: Double) {
        val sanitized = sanitizeHalfHourOffset(offsetHours)
        context.falakDataStore.edit { prefs ->
            prefs[Keys.TIMEZONE_MODE] = TimezonePreferenceMode.CUSTOM.name
            prefs[Keys.CUSTOM_TIMEZONE_OFFSET] = sanitized
        }
    }

    suspend fun resetFalakSettingsToDefaults() {
        context.falakDataStore.edit { prefs ->
            prefs[Keys.CRITERIA] = RukyatCriteria.MABIMS_BARU.name
            prefs[Keys.LAST_RUKYAT_CRITERIA] = RukyatCriteria.MABIMS_BARU.name
            prefs[Keys.TIMEZONE_MODE] = TimezonePreferenceMode.AUTO_MARKAZ.name
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.falakDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = isDark
        }
    }

    companion object {
        fun sanitizeHalfHourOffset(offsetHours: Double): Double {
            if (!offsetHours.isFinite()) return 7.0
            val clamped = offsetHours.coerceIn(-12.0, 14.0)
            return (clamped * 2.0).roundToInt() / 2.0
        }

        fun inferIndonesianOrApproxTimezone(longitude: Double): Pair<Double, String> {
            return when {
                longitude in 94.0..114.5 -> 7.0 to "WIB"
                longitude in 114.5..128.0 -> 8.0 to "WITA"
                longitude in 128.0..142.0 -> 9.0 to "WIT"
                else -> {
                    val tz = (longitude / 15.0).roundToInt().toDouble()
                    val sign = if (tz >= 0) "+" else ""
                    tz to "UTC$sign${tz.toInt()}"
                }
            }
        }
    }
}
