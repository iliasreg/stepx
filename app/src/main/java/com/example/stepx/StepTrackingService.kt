package com.example.stepx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*

class StepTrackingService : Service() {
	private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private lateinit var counter: StepCounterManager

	override fun onCreate() {
		super.onCreate()
		createNotificationChannel()
		startForegroundServiceNotification()
		counter = StepCounterManager.getInstance(applicationContext)

		serviceScope.launch {
			counter.totalSteps.collect { steps ->
				updateNotification("Steps: $steps")
			}
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		counter // initialization triggers sensor registration
		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		serviceScope.cancel()
	}

	override fun onBind(intent: Intent?): IBinder? = null

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				"Step Tracking",
				NotificationManager.IMPORTANCE_LOW
			)
			NotificationManagerCompat.from(this).createNotificationChannel(channel)
		}
	}

	private fun startForegroundServiceNotification() {
		try {
			// Ensure the channel exists before building notification
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
				if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
					val ch = NotificationChannel(
						CHANNEL_ID,
						"Step Tracking",
						NotificationManager.IMPORTANCE_LOW
					)
					mgr.createNotificationChannel(ch)
				}
			}

			// Ask for notification permission on Android 13+
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
						PackageManager.PERMISSION_GRANTED
				if (!granted) {
					// Create a temporary minimal notification (safe fallback)
					val temp = NotificationCompat.Builder(this, CHANNEL_ID)
						.setContentTitle("StepX starting…")
						.setSmallIcon(android.R.drawable.ic_dialog_info)
						.build()
					startForeground(NOTIFICATION_ID, temp)
					return
				}
			}

			val intent = Intent(this, MainActivity::class.java)
			val pi = PendingIntent.getActivity(
				this, 0, intent,
				PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
			)

			val notification = NotificationCompat.Builder(this, CHANNEL_ID)
				.setContentTitle("StepX Active")
				.setContentText("Tracking your steps in the background")
				.setSmallIcon(android.R.drawable.ic_dialog_info)
				.setOngoing(true)
				.setContentIntent(pi)
				.build()

			startForeground(NOTIFICATION_ID, notification)

		} catch (e: Exception) {
			// Fallback — don’t crash, just log and stop service gracefully
			android.util.Log.e("StepX", "startForegroundServiceNotification failed", e)
			stopSelf()
		}
	}


	private fun updateNotification(text: String) {
		val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		val updated = NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("StepX Active")
			.setContentText(text)
			.setSmallIcon(android.R.drawable.ic_dialog_info)
			.setOngoing(true)
			.build()
		manager.notify(NOTIFICATION_ID, updated)
	}

	companion object {
		private const val CHANNEL_ID = "stepx_tracking"
		private const val NOTIFICATION_ID = 1001
	}
}
