package com.example.data

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CompassReading(
    val magneticAzimuthDeg: Float = 270f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val magneticFieldUt: Float = 0f,
    val gyroRateRadPerSec: Float = 0f,
    val hasHardwareSensor: Boolean = false,
    val hasMagnetometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val hasRotationVector: Boolean = false,
    val accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
)

class CompassSensorManager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun observeAzimuth(): Flow<CompassReading> = callbackFlow {
        val sm = sensorManager
        if (sm == null) {
            trySend(CompassReading())
            awaitClose { }
            return@callbackFlow
        }

        val rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val hasMag = magSensor != null
        val hasGyro = gyroSensor != null
        val hasRot = rotationSensor != null

        if (!hasRot && (accelSensor == null || !hasMag)) {
            trySend(
                CompassReading(
                    hasHardwareSensor = false,
                    hasMagnetometer = hasMag,
                    hasGyroscope = hasGyro,
                    hasRotationVector = false
                )
            )
            awaitClose { }
            return@callbackFlow
        }

        var smoothedSin = 0.0
        var smoothedCos = -1.0
        var initialized = false
        val lowPassAlpha = 0.18

        var currentAzimuthDeg = 270f
        var currentPitchDeg = 0f
        var currentRollDeg = 0f
        var currentMagUt = 0f
        var currentGyroRate = 0f
        var lastAccuracy = if (hasRot || hasMag) {
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        } else {
            SensorManager.SENSOR_STATUS_UNRELIABLE
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false

        // Complementary filter state when TYPE_ROTATION_VECTOR is absent
        var fusedAzimuthRad = 0.0
        var hasFusedInit = false
        var lastGyroTimestampNs = 0L

        fun emitState() {
            trySend(
                CompassReading(
                    magneticAzimuthDeg = currentAzimuthDeg,
                    pitchDeg = currentPitchDeg,
                    rollDeg = currentRollDeg,
                    magneticFieldUt = currentMagUt,
                    gyroRateRadPerSec = currentGyroRate,
                    hasHardwareSensor = true,
                    hasMagnetometer = hasMag,
                    hasGyroscope = hasGyro,
                    hasRotationVector = hasRot,
                    accuracy = lastAccuracy
                )
            )
        }

        fun updateSmoothedHeading(rawDeg: Float, pitch: Float, roll: Float) {
            val rad = rawDeg * PI / 180.0
            if (!initialized) {
                smoothedSin = sin(rad)
                smoothedCos = cos(rad)
                initialized = true
            } else {
                smoothedSin += lowPassAlpha * (sin(rad) - smoothedSin)
                smoothedCos += lowPassAlpha * (cos(rad) - smoothedCos)
            }
            var deg = (atan2(smoothedSin, smoothedCos) * 180.0 / PI).toFloat()
            if (deg < 0f) deg += 360f
            currentAzimuthDeg = deg
            currentPitchDeg = pitch
            currentRollDeg = roll
            emitState()
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val rotMatrix = FloatArray(9)
                        val orientation = FloatArray(3)
                        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                        SensorManager.getOrientation(rotMatrix, orientation)
                        var azDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (azDeg < 0f) azDeg += 360f
                        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
                        updateSmoothedHeading(azDeg, pitchDeg, rollDeg)
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasGeomagnetic = true
                        currentMagUt = sqrt(
                            event.values[0] * event.values[0] +
                                event.values[1] * event.values[1] +
                                event.values[2] * event.values[2]
                        )
                        if (!hasRot && hasGravity) {
                            val r = FloatArray(9)
                            val i = FloatArray(9)
                            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                                val orientation = FloatArray(3)
                                SensorManager.getOrientation(r, orientation)
                                val magAzRad = orientation[0].toDouble()
                                if (!hasFusedInit) {
                                    fusedAzimuthRad = magAzRad
                                    hasFusedInit = true
                                } else {
                                    // Pull complementary filter gently toward magnetic heading
                                    val diff = atan2(sin(magAzRad - fusedAzimuthRad), cos(magAzRad - fusedAzimuthRad))
                                    fusedAzimuthRad += 0.08 * diff
                                }
                                var azDeg = Math.toDegrees(fusedAzimuthRad).toFloat()
                                if (azDeg < 0f) azDeg += 360f
                                val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                                val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
                                updateSmoothedHeading(azDeg, pitchDeg, rollDeg)
                            }
                        } else {
                            emitState()
                        }
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                    }

                    Sensor.TYPE_GYROSCOPE -> {
                        val gx = event.values[0]
                        val gy = event.values[1]
                        val gz = event.values[2]
                        currentGyroRate = sqrt(gx * gx + gy * gy + gz * gz)
                        if (!hasRot && hasFusedInit && lastGyroTimestampNs != 0L) {
                            val dt = (event.timestamp - lastGyroTimestampNs) * 1e-9
                            if (dt in 0.0..0.2) {
                                // Integrate yaw rate (-gz in Android device frame) for responsive turning
                                fusedAzimuthRad -= gz * dt
                            }
                        }
                        lastGyroTimestampNs = event.timestamp
                        emitState()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    lastAccuracy = accuracy
                    emitState()
                }
            }
        }

        if (rotationSensor != null) {
            sm.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        if (magSensor != null) {
            sm.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
        }
        if (gyroSensor != null) {
            sm.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_UI)
        }
        if (rotationSensor == null && accelSensor != null) {
            sm.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sm.unregisterListener(listener)
        }
    }

    companion object {
        /**
         * Calculates magnetic declination (degrees East of True North) using WMM via GeomagneticField.
         * True North Heading = (Magnetic Heading + Declination) mod 360.
         */
        fun calculateMagneticDeclinationDeg(
            latitudeDeg: Double,
            longitudeDeg: Double,
            elevationMeters: Double,
            timeMillis: Long = System.currentTimeMillis()
        ): Float {
            return runCatching {
                val field = GeomagneticField(
                    latitudeDeg.toFloat(),
                    longitudeDeg.toFloat(),
                    elevationMeters.toFloat(),
                    timeMillis
                )
                field.declination
            }.getOrDefault(0f)
        }
    }
}
