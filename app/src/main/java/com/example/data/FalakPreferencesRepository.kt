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
import com.example.domain.falak.MarkazLocation
import com.example.domain.falak.RukyatCriteria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.falakDataStore: DataStore<Preferences> by preferencesDataStore(name = "hilal_checker_prefs")

data class UserFalakPreferences(
    val markaz: MarkazLocation,
    val selectedCriteria: RukyatCriteria,
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
        val DARK_THEME = booleanPreferencesKey("is_dark_theme")
    }

    val preferencesFlow: Flow<UserFalakPreferences> = context.falakDataStore.data.map { prefs ->
        val markazId = prefs[Keys.MARKAZ_ID] ?: FalakCatalog.markazStations.first().id
        val markaz = if (markazId == "custom_gps" || markazId == "custom_manual") {
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

        val darkTheme = prefs[Keys.DARK_THEME] ?: true
        UserFalakPreferences(
            markaz = markaz,
            selectedCriteria = criteria,
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
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.falakDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = isDark
        }
    }

    companion object {
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
