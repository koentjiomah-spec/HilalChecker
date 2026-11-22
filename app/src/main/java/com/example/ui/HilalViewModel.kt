package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CompassReading
import com.example.data.CompassSensorManager
import com.example.data.FalakPreferencesRepository
import com.example.data.LocationHelper
import com.example.domain.falak.FalakCatalog
import com.example.domain.falak.FalakEphemerisEngine
import com.example.domain.falak.HilalResult
import com.example.domain.falak.IstiwakAndPrayerResult
import com.example.domain.falak.LunarPhaseData
import com.example.domain.falak.MarkazLocation
import com.example.domain.falak.RukyatCriteria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CompassTargetMode(val label: String) {
    KIBLAT("Arah Kiblat"),
    HILAL("Arah Hilal"),
    SUNSET("Ghurub Matahari")
}

data class HilalUiState(
    val selectedHijriYear: Int = 1448,
    val selectedHijriMonth: Int = 9,
    val markaz: MarkazLocation = FalakCatalog.markazStations.first(),
    val selectedCriteria: RukyatCriteria = RukyatCriteria.MABIMS_BARU,
    val isDarkTheme: Boolean = true,
    val hilalResult: HilalResult? = null,
    val istiwakResult: IstiwakAndPrayerResult? = null,
    val lunarPhaseData: LunarPhaseData? = null,
    val lunarDayOffset: Int = 0,
    val compassReading: CompassReading = CompassReading(),
    val magneticDeclinationDeg: Float = 0f,
    val useManualCompassOverride: Boolean = false,
    val manualCompassAzimuthDeg: Float = 295f,
    val compassTargetMode: CompassTargetMode = CompassTargetMode.KIBLAT,
    val horizonOffsetMinutes: Float = 0f,
    val isPlayingHorizonSimulation: Boolean = false,
    val isFetchingGps: Boolean = false,
    val gpsStatusMessage: String? = null,
    val isMarkazSheetOpen: Boolean = false
)

class HilalViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = FalakPreferencesRepository(application.applicationContext)
    private val compassManager = CompassSensorManager(application.applicationContext)

    private val _uiState = MutableStateFlow(
        run {
            val (hYear, hMonth) = FalakEphemerisEngine.currentEstimatedHijriYearMonth(System.currentTimeMillis())
            HilalUiState(
                selectedHijriYear = hYear,
                selectedHijriMonth = hMonth
            )
        }
    )
    val uiState: StateFlow<HilalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                val declination = CompassSensorManager.calculateMagneticDeclinationDeg(
                    latitudeDeg = prefs.markaz.latitude,
                    longitudeDeg = prefs.markaz.longitude,
                    elevationMeters = prefs.markaz.elevationMeters
                )
                _uiState.update {
                    it.copy(
                        markaz = prefs.markaz,
                        selectedCriteria = prefs.selectedCriteria,
                        isDarkTheme = prefs.isDarkTheme,
                        magneticDeclinationDeg = declination
                    )
                }
                recomputeHilal()
                recomputeIstiwak(System.currentTimeMillis())
                recomputeLunarPhase()
            }
        }

        viewModelScope.launch {
            compassManager.observeAzimuth().collect { reading ->
                _uiState.update { it.copy(compassReading = reading) }
            }
        }

        // Live 1-second ticker for Waktu Istiwak (WIS), Ghurubiyah, and Prayer countdown
        viewModelScope.launch {
            while (true) {
                recomputeIstiwak(System.currentTimeMillis())
                delay(1_000L)
            }
        }

        // Horizon simulation auto-play loop
        viewModelScope.launch {
            while (true) {
                delay(60L)
                val cur = _uiState.value
                if (cur.isPlayingHorizonSimulation) {
                    val next = cur.horizonOffsetMinutes + 0.5f
                    if (next > 60f) {
                        _uiState.update { it.copy(horizonOffsetMinutes = -30f) }
                    } else {
                        _uiState.update { it.copy(horizonOffsetMinutes = next) }
                    }
                }
            }
        }
    }

    private fun recomputeHilal() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                FalakEphemerisEngine.calculateHilalForHijriMonth(
                    hijriYear = state.selectedHijriYear,
                    hijriMonth = state.selectedHijriMonth,
                    markaz = state.markaz
                )
            }
            _uiState.update { it.copy(hilalResult = result) }
        }
    }

    private fun recomputeIstiwak(nowMillis: Long) {
        val markaz = _uiState.value.markaz
        val result = FalakEphemerisEngine.calculateIstiwakAndPrayerTimes(nowMillis, markaz)
        _uiState.update { it.copy(istiwakResult = result) }
    }

    private fun recomputeLunarPhase() {
        val state = _uiState.value
        val targetMillis = System.currentTimeMillis() + state.lunarDayOffset * 86_400_000L
        viewModelScope.launch {
            val phase = withContext(Dispatchers.Default) {
                FalakEphemerisEngine.calculateLunarPhase(targetMillis, state.markaz)
            }
            _uiState.update { it.copy(lunarPhaseData = phase) }
        }
    }

    fun setLunarDayOffset(days: Int) {
        _uiState.update { it.copy(lunarDayOffset = days.coerceIn(-15, 15)) }
        recomputeLunarPhase()
    }

    fun selectHijriMonth(month: Int) {
        _uiState.update { it.copy(selectedHijriMonth = month.coerceIn(1, 12)) }
        recomputeHilal()
    }

    fun shiftHijriYear(delta: Int) {
        _uiState.update {
            it.copy(selectedHijriYear = (it.selectedHijriYear + delta).coerceIn(1440, 1460))
        }
        recomputeHilal()
    }

    fun selectPresetPeriod(hijriYear: Int, hijriMonth: Int) {
        _uiState.update {
            it.copy(selectedHijriYear = hijriYear, selectedHijriMonth = hijriMonth)
        }
        recomputeHilal()
    }

    fun selectCriteria(criteria: RukyatCriteria) {
        viewModelScope.launch { prefsRepo.setCriteria(criteria) }
    }

    fun toggleTheme() {
        val target = !_uiState.value.isDarkTheme
        viewModelScope.launch { prefsRepo.setDarkTheme(target) }
    }

    fun setMarkazSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isMarkazSheetOpen = open, gpsStatusMessage = null) }
    }

    fun selectPresetMarkaz(markaz: MarkazLocation) {
        viewModelScope.launch {
            prefsRepo.selectPresetMarkaz(markaz.id)
            _uiState.update { it.copy(isMarkazSheetOpen = false) }
        }
    }

    fun saveManualCoordinates(name: String, lat: Double, lon: Double, elev: Double) {
        viewModelScope.launch {
            prefsRepo.saveCustomMarkaz(
                id = "custom_manual",
                name = name.ifBlank { "Markaz Kustom" },
                latitude = lat.coerceIn(-89.9, 89.9),
                longitude = lon.coerceIn(-180.0, 180.0),
                elevationMeters = elev.coerceIn(0.0, 6000.0)
            )
            _uiState.update { it.copy(isMarkazSheetOpen = false) }
        }
    }

    fun fetchGpsLocation(closeSheetOnSuccess: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingGps = true, gpsStatusMessage = "Mengambil koordinat GPS...") }
            val gpsMarkaz = withContext(Dispatchers.IO) {
                LocationHelper.fetchCurrentMarkaz(getApplication())
            }
            if (gpsMarkaz != null) {
                prefsRepo.saveCustomMarkaz(
                    id = "custom_gps",
                    name = gpsMarkaz.name,
                    latitude = gpsMarkaz.latitude,
                    longitude = gpsMarkaz.longitude,
                    elevationMeters = gpsMarkaz.elevationMeters
                )
                _uiState.update {
                    it.copy(
                        isFetchingGps = false,
                        gpsStatusMessage = "Koordinat GPS diperbarui",
                        isMarkazSheetOpen = if (closeSheetOnSuccess) false else it.isMarkazSheetOpen
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isFetchingGps = false,
                        gpsStatusMessage = "GPS tidak tersedia. Pilih markaz atau isi koordinat manual."
                    )
                }
            }
        }
    }

    fun setGpsStatusMessage(msg: String?) {
        _uiState.update { it.copy(gpsStatusMessage = msg) }
    }

    fun setHorizonOffset(minutes: Float) {
        _uiState.update {
            it.copy(
                horizonOffsetMinutes = minutes.coerceIn(-30f, 60f),
                isPlayingHorizonSimulation = false
            )
        }
    }

    fun toggleHorizonPlayback() {
        _uiState.update {
            val nextPlay = !it.isPlayingHorizonSimulation
            val resetOffset = if (nextPlay && it.horizonOffsetMinutes >= 59f) -30f else it.horizonOffsetMinutes
            it.copy(isPlayingHorizonSimulation = nextPlay, horizonOffsetMinutes = resetOffset)
        }
    }

    fun setCompassTargetMode(mode: CompassTargetMode) {
        _uiState.update { it.copy(compassTargetMode = mode) }
    }

    fun setManualCompassOverride(enabled: Boolean) {
        _uiState.update { it.copy(useManualCompassOverride = enabled) }
    }

    fun setManualCompassAzimuth(deg: Float) {
        _uiState.update { it.copy(manualCompassAzimuthDeg = (deg % 360f + 360f) % 360f) }
    }
}
