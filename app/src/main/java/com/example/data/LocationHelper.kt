package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.example.domain.falak.MarkazLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentMarkaz(context: Context): MarkazLocation? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val lastKnown = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 300_000L) {
            return toMarkazLocation(lastKnown)
        }

        val activeProvider = when {
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (activeProvider == null) {
            return lastKnown?.let { toMarkazLocation(it) }
        }

        val freshLocation = withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { locationManager.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                runCatching {
                    locationManager.requestLocationUpdates(
                        activeProvider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation {
                    runCatching { locationManager.removeUpdates(listener) }
                }
            }
        }

        return (freshLocation ?: lastKnown)?.let { toMarkazLocation(it) }
    }

    private fun toMarkazLocation(location: Location): MarkazLocation {
        val tz = FalakPreferencesRepository.inferIndonesianOrApproxTimezone(location.longitude)
        val elev = if (location.hasAltitude()) location.altitude.coerceAtLeast(0.0) else 15.0
        return MarkazLocation(
            id = "custom_gps",
            name = "Lokasi GPS Saat Ini",
            region = "Koordinat GPS Perangkat",
            latitude = location.latitude,
            longitude = location.longitude,
            elevationMeters = elev,
            timezoneOffsetHours = tz.first,
            timezoneLabel = tz.second,
            isCustomGps = true
        )
    }
}
