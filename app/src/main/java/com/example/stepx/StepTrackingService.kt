package com.example.stepx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.sqrt

class StepTrackingService : Service(), SensorEventListener {
	private lateinit var sensorManager: SensorManager
	private var proximitySensor: Sensor? = null
    private var accelSensor: Sensor? = null
	private var stepCounterSensor: Sensor? = null
	private var isCovered: Boolean = false

    private var gravityX: Float = 0f
    private var gravityY: Float = 0f
    private var gravityZ: Float = 0f
    private var lastStepAtMs: Long = 0L

	private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
	private lateinit var prefs: PreferencesManager

	override fun onCreate() {
		super.onCreate()
		prefs = PreferencesManager(this)
		sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
		stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
		accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
		createNotificationChannel()
		startInForeground()
		registerSensors()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		sensorManager.unregisterListener(this)
	}

	override fun onBind(intent: Intent?): IBinder? = null

	private fun registerSensors() {
		proximitySensor?.let {
			sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
		}
		stepCounterSensor?.let {
			sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
		}
        accelSensor?.let {
			sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
		}
	}

	override fun onSensorChanged(event: android.hardware.SensorEvent) {
		when (event.sensor.type) {
			Sensor.TYPE_PROXIMITY -> {
				val max = proximitySensor?.maximumRange ?: 0f
				isCovered = (event.values.firstOrNull() ?: max) < max
            }
            Sensor.TYPE_ACCELEROMETER -> {
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
                if (!isCovered && magnitude > threshold && (now - lastStepAtMs) > debounceMs) {
                    lastStepAtMs = now
                    serviceScope.launch { prefs.incrementTotalSteps(1) }
                }
            }
		}
	}

	override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    private fun startInForeground() {
		val intent = Intent(this, MainActivity::class.java)
		val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("StepX Tracking")
			.setContentText("Counting your steps in the background")
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
			.setContentIntent(pi)
			.setOngoing(true)
			.build()
		startForeground(NOTIFICATION_ID, notification)
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(CHANNEL_ID, "Step Tracking", NotificationManager.IMPORTANCE_LOW)
			NotificationManagerCompat.from(this).createNotificationChannel(channel)
		}
	}

	companion object {
		private const val CHANNEL_ID = "stepx_tracking"
		private const val NOTIFICATION_ID = 1001
	}
}
