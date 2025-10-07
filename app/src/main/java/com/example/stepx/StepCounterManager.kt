package com.example.stepx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.sqrt

class StepCounterManager private constructor(private val appContext: Context) : SensorEventListener {
    private val sensorManager: SensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val prefs = PreferencesManager(appContext)

    private val _totalSteps: MutableStateFlow<Int> = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps

    private val _ambientLux: MutableStateFlow<Float> = MutableStateFlow(0f)
    val ambientLux: StateFlow<Float> = _ambientLux

    private val _isCovered: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isCovered: StateFlow<Boolean> = _isCovered

    private var lastRawCounter: Int = 0
    private var gravityX: Float = 0f
    private var gravityY: Float = 0f
    private var gravityZ: Float = 0f
    private var lastStepAtMs: Long = 0L
    private var pocketDetectionEnabled: Boolean = true
    // No date-bound logic anymore

    init {
        // Observe settings and today's steps
        scope.launch { prefs.pocketDetectionEnabled.collect { pocketDetectionEnabled = it } }
        scope.launch { prefs.totalSteps.collect { _totalSteps.value = it } }
        // Register sensors once
        registerSensors()
        // No date-change loop
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun registerSensors() {
        val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        light?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }

        val arGranted = hasActivityRecognitionPermission()
        when {
            arGranted && stepCounter != null -> sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI)
            arGranted && stepDetector != null -> sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
            //accelerometer != null -> sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                _ambientLux.value = event.values.firstOrNull() ?: 0f
            }
            Sensor.TYPE_PROXIMITY -> {
                val max = event.sensor.maximumRange
                _isCovered.value = (event.values.firstOrNull() ?: max) < max
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val raw = event.values.firstOrNull()?.toInt() ?: return
                if (lastRawCounter == 0) {
                    lastRawCounter = raw
                } else {
                    val delta = (raw - lastRawCounter).coerceAtLeast(0)
                    lastRawCounter = raw
                    if (!(pocketDetectionEnabled && _isCovered.value) && delta > 0) incrementTotal(delta)
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                if (!(pocketDetectionEnabled && _isCovered.value)) incrementTotal(1)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-pass gravity estimation
                val alpha = 0.8f
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                gravityX = alpha * gravityX + (1 - alpha) * ax
                gravityY = alpha * gravityY + (1 - alpha) * ay
                gravityZ = alpha * gravityZ + (1 - alpha) * az
                val linearX = ax - gravityX
                val linearY = ay - gravityY
                val linearZ = az - gravityZ
                val magnitude = sqrt((linearX * linearX + linearY * linearY + linearZ * linearZ).toDouble()).toFloat()
                val now = System.currentTimeMillis()
                val debounceMs = 300L
                val threshold = 1.2f
                if (!(pocketDetectionEnabled && _isCovered.value) && magnitude > threshold && (now - lastStepAtMs) > debounceMs) {
                    lastStepAtMs = now
                    incrementTotal(1)
                }
            }
        }
    }

    private fun incrementTotal(delta: Int) {
        scope.launch { prefs.incrementTotalSteps(delta) }
        _totalSteps.value = (_totalSteps.value + delta).coerceAtLeast(0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        @Volatile private var instance: StepCounterManager? = null
        fun getInstance(context: Context): StepCounterManager {
            return instance ?: synchronized(this) {
                instance ?: StepCounterManager(context.applicationContext).also { instance = it }
            }
        }
    }
}


