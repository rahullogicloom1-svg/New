package com.calmremind.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Rings like a real alarm clock: loops the alarm sound on the ALARM audio stream, vibrates,
 * wakes the screen and opens [AlarmActivity] over the lock screen via a full-screen intent.
 * Stops itself after [RING_MILLIS] if nobody reacts, leaving a normal notification behind.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentId = -1
    private var currentSlot = 0L
    private val timeout = Runnable { onTimeout() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(Alarms.EXTRA_ID, -1) ?: -1
        val slot = intent?.getLongExtra(Alarms.EXTRA_SLOT, 0L) ?: 0L
        val reminder = if (id >= 0) resolve(this, id) else null

        if (reminder == null) {
            // Reminder vanished (deleted just now). We must still satisfy startForeground().
            goForeground(0, buildNotification(null, 0L))
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        // A different alarm was already ringing: keep it visible as a normal notification.
        if (currentId >= 0 && currentId != id && currentId != TEST_ID) {
            resolve(this, currentId)?.let { Notifier.show(this, it, currentSlot) }
        }
        currentId = id
        currentSlot = slot

        goForeground(id, buildNotification(reminder, slot))
        startRinging()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RING_MILLIS)
        return START_NOT_STICKY
    }

    private fun goForeground(id: Int, n: Notification) {
        val nid = NOTIF_BASE + id
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(nid, n)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(r: Reminder?, slot: Long): Notification {
        Notifier.ensureChannel(this)
        val title = if (r == null) "Reminder" else "${r.type.emoji} ${r.name}"
        val text = when {
            r == null -> "Reminder"
            r.note.isNotBlank() -> r.note
            r.type == ReminderType.WATER -> "Time for a glass of water 💧"
            r.type == ReminderType.MEDICINE -> "Time to take your medicine"
            else -> "Your reminder is due"
        }
        val b = NotificationCompat.Builder(this, Notifier.RING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
        if (r != null) {
            val open = PendingIntent.getActivity(
                this, r.id,
                alarmScreenIntent(this, r.id, slot),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.setContentIntent(open)
            b.setFullScreenIntent(open, true)
            b.addAction(0, "✓ Done", Notifier.actionIntent(this, Notifier.ACTION_DONE, r.id, slot, 1))
            b.addAction(0, "Snooze ${Notifier.SNOOZE_MINUTES} min", Notifier.actionIntent(this, Notifier.ACTION_SNOOZE, r.id, slot, 2))
            b.addAction(0, "Stop", Notifier.actionIntent(this, Notifier.ACTION_STOP, r.id, slot, 3))
        }
        return b.build()
    }

    // ---------- Sound, vibration, wake lock ----------

    private fun startRinging() {
        stopRinging()

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "calmreminders:alarm").apply {
                acquire(RING_MILLIS + 5_000L)
            }
        } catch (e: Exception) {
            // Not critical.
        }

        val settings = getSharedPreferences("calm_reminder_settings", Context.MODE_PRIVATE)
        val volume = settings.getInt("ringtone_volume", 80).coerceIn(0, 100) / 100f
        val vibrationEnabled = settings.getBoolean("vibration_enabled", true)

        val selectedRingtone = settings.getString("ringtone_uri", null)?.let(Uri::parse)
        val defaultAlarm = Notifier.alarmUri(this)
        val fallbackNotification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        player = (selectedRingtone?.let { tryPlay(it, volume) })
            ?: tryPlay(defaultAlarm, volume)
            ?: tryPlay(fallbackNotification, volume)

        if (vibrationEnabled) {
            try {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 400, 700, 1200), 0)
                @Suppress("DEPRECATION")
                vibrator().vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } catch (e: Exception) {
                // Phone without vibration motor.
            }
        }
    }

    private fun tryPlay(uri: Uri, volume: Float): MediaPlayer? = try {
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        mp.setDataSource(applicationContext, uri)
        mp.isLooping = true
        mp.prepare()
        mp.setVolume(volume, volume)
        mp.start()
        mp
    } catch (e: Exception) {
        null
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun stopRinging() {
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
        try {
            vibrator().cancel()
        } catch (e: Exception) {
        }
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
        wakeLock = null
    }

    private fun onTimeout() {
        // Nobody reacted: stop the noise but leave an ordinary notification in the shade.
        val id = currentId
        stopRinging()
        stopForegroundCompat()
        if (id >= 0 && id != TEST_ID) {
            resolve(this, id)?.let { Notifier.show(this, it, currentSlot) }
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        stopRinging()
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        super.onDestroy()
    }

    companion object {
        const val TEST_ID = 999_999
        const val ACTION_STOPPED = "com.calmremind.app.action.ALARM_STOPPED"
        private const val NOTIF_BASE = 100_000
        private const val RING_MILLIS = 90_000L

        fun alarmScreenIntent(ctx: Context, id: Int, slot: Long): Intent =
            Intent(ctx, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Alarms.EXTRA_ID, id)
                .putExtra(Alarms.EXTRA_SLOT, slot)

        fun start(ctx: Context, id: Int, slot: Long) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, AlarmService::class.java)
                    .putExtra(Alarms.EXTRA_ID, id)
                    .putExtra(Alarms.EXTRA_SLOT, slot)
            )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AlarmService::class.java))
        }

        fun resolve(ctx: Context, id: Int): Reminder? =
            if (id == TEST_ID) testReminder() else Store.get(ctx).get(id)

        private fun testReminder() = Reminder(
            id = TEST_ID, name = "Test alarm", type = ReminderType.CUSTOM,
            mode = ScheduleMode.TIMES, intervalMinutes = 120, windowStart = 0, windowEnd = 0,
            times = emptyList(), repeat = RepeatMode.DAILY, weekdays = emptySet(),
            anchorEpochDay = 0L, note = "This is how your reminders will ring.",
            enabled = true, createdAt = 0L
        )

        private fun clearNotifications(ctx: Context, id: Int) {
            val nm = NotificationManagerCompat.from(ctx)
            nm.cancel(id)
            nm.cancel(NOTIF_BASE + id)
        }

        /** "Done": log it (not for the test alarm), silence everything. */
        fun done(ctx: Context, id: Int, slot: Long) {
            if (id != TEST_ID) {
                Store.get(ctx).get(id)?.let { Store.get(ctx).markDone(it, slot) }
            }
            clearNotifications(ctx, id)
            stop(ctx)
        }

        fun snooze(ctx: Context, id: Int, slot: Long) {
            Alarms.scheduleSnooze(ctx, id, slot, Notifier.SNOOZE_MINUTES)
            clearNotifications(ctx, id)
            stop(ctx)
        }

        /** "Stop": silence it without logging. */
        fun dismiss(ctx: Context, id: Int) {
            clearNotifications(ctx, id)
            stop(ctx)
        }
    }
}
