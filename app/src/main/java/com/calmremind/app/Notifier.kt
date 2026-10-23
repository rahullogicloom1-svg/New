package com.calmremind.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    /** Fallback / after-timeout notification. Uses the alarm sound on the alarm volume. */
    const val CHANNEL_ID = "reminders_v2"
    /** Silent channel for the ringing notification; [AlarmService] plays the sound itself. */
    const val RING_CHANNEL_ID = "alarm_ring_v1"
    const val ACTION_DONE = "com.calmremind.app.action.DONE"
    const val ACTION_SNOOZE = "com.calmremind.app.action.SNOOZE"
    const val ACTION_STOP = "com.calmremind.app.action.STOP"
    const val SNOOZE_MINUTES = 10

    fun alarmUri(ctx: Context): Uri =
        ctx.getSharedPreferences("calm_reminder_settings", Context.MODE_PRIVATE)
            .getString("ringtone_uri", null)?.let { raw ->
                try { Uri.parse(raw) } catch (_: Exception) { null }
            } ?: RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // The first version's channel used the quiet notification sound; its settings cannot be
        // changed after creation, so it is replaced by a new one.
        nm.deleteNotificationChannel("reminders_v1")

        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Water, medicine and custom reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                setSound(alarmUri(ctx), attrs)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
            nm.createNotificationChannel(channel)
        }

        if (nm.getNotificationChannel(RING_CHANNEL_ID) == null) {
            val ring = NotificationChannel(RING_CHANNEL_ID, "Ringing alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while a reminder is ringing"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ring)
        }
    }

    /** Ordinary (non-ringing) notification. */
    fun show(ctx: Context, r: Reminder, slot: Long) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = PendingIntent.getActivity(
            ctx, r.id,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when {
            r.note.isNotBlank() -> r.note
            r.type == ReminderType.WATER -> "Time for a glass of water 💧"
            r.type == ReminderType.MEDICINE -> "Time to take your medicine"
            else -> "Your reminder is due"
        }

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("${r.type.emoji} ${r.name}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, "✓ Done", actionIntent(ctx, ACTION_DONE, r.id, slot, 1))
            .addAction(0, "Snooze $SNOOZE_MINUTES min", actionIntent(ctx, ACTION_SNOOZE, r.id, slot, 2))
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(r.id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    fun actionIntent(ctx: Context, action: String, id: Int, slot: Long, k: Int): PendingIntent {
        val intent = Intent(ctx, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(Alarms.EXTRA_ID, id)
            .putExtra(Alarms.EXTRA_SLOT, slot)
        return PendingIntent.getBroadcast(
            ctx, id * 10 + k, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
