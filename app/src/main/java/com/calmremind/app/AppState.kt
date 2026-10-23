package com.calmremind.app

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

/** Observable state for the UI. Refreshes itself whenever the stored data changes. */
class AppState(context: Context) {
    private val ctx = context.applicationContext
    private val store = Store.get(ctx)

    var reminders by mutableStateOf(store.loadReminders())
        private set
    var logs by mutableStateOf(store.loadLogs())
        private set

    private val settingsPrefs = ctx.getSharedPreferences("calm_reminder_settings", Context.MODE_PRIVATE)
    var ringtoneUri by mutableStateOf(settingsPrefs.getString(KEY_RINGTONE_URI, null))
        private set
    var ringtoneVolume by mutableStateOf(settingsPrefs.getInt(KEY_RINGTONE_VOLUME, 80))
        private set
    var vibrationEnabled by mutableStateOf(settingsPrefs.getBoolean(KEY_VIBRATION_ENABLED, true))
        private set

    var notificationsOk by mutableStateOf(true)
        private set
    var exactOk by mutableStateOf(true)
        private set
    var batteryOk by mutableStateOf(true)
        private set
    var fullScreenOk by mutableStateOf(true)
        private set

    val needsAttention: Boolean get() = !notificationsOk || !exactOk || !batteryOk || !fullScreenOk

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    init {
        store.registerListener(listener)
        refreshHealth()
    }

    fun close() {
        store.unregisterListener(listener)
    }

    fun refresh() {
        reminders = store.loadReminders()
        logs = store.loadLogs()
        ringtoneUri = settingsPrefs.getString(KEY_RINGTONE_URI, null)
        ringtoneVolume = settingsPrefs.getInt(KEY_RINGTONE_VOLUME, 80)
        vibrationEnabled = settingsPrefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    }

    fun updateRingtoneUri(uri: String?) {
        settingsPrefs.edit().putString(KEY_RINGTONE_URI, uri).apply()
        ringtoneUri = uri
    }

    fun updateRingtoneVolume(volume: Int) {
        val safeVolume = volume.coerceIn(0, 100)
        settingsPrefs.edit().putInt(KEY_RINGTONE_VOLUME, safeVolume).apply()
        ringtoneVolume = safeVolume
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        vibrationEnabled = enabled
    }

    companion object {
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_RINGTONE_VOLUME = "ringtone_volume"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    }

    fun refreshHealth() {
        notificationsOk = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryOk = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        fullScreenOk = if (Build.VERSION.SDK_INT >= 34) {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .canUseFullScreenIntent()
        } else {
            true
        }
    }

    // ---------- Actions ----------

    fun template(type: ReminderType): Reminder {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val allDays = (1..7).toSet()
        return when (type) {
            ReminderType.WATER -> Reminder(
                id = 0, name = defaultName(type), type = type, mode = ScheduleMode.INTERVAL,
                intervalMinutes = 120, windowStart = 8 * 60, windowEnd = 22 * 60, times = emptyList(),
                repeat = RepeatMode.DAILY, weekdays = allDays, anchorEpochDay = today,
                note = "", enabled = true, createdAt = now
            )
            ReminderType.MEDICINE -> Reminder(
                id = 0, name = defaultName(type), type = type, mode = ScheduleMode.INTERVAL,
                intervalMinutes = 180, windowStart = 8 * 60, windowEnd = 22 * 60, times = emptyList(),
                repeat = RepeatMode.DAILY, weekdays = allDays, anchorEpochDay = today,
                note = "", enabled = true, createdAt = now
            )
            ReminderType.CUSTOM -> Reminder(
                id = 0, name = defaultName(type), type = type, mode = ScheduleMode.TIMES,
                intervalMinutes = 120, windowStart = 8 * 60, windowEnd = 22 * 60, times = listOf(9 * 60),
                repeat = RepeatMode.DAILY, weekdays = allDays, anchorEpochDay = today,
                note = "", enabled = true, createdAt = now
            )
        }
    }

    /** Saves a reminder (id 0 = new) and re-arms its alarm. */
    fun save(r: Reminder) {
        val saved = if (r.id == 0) r.copy(id = store.nextId()) else r
        store.upsert(saved)
        Alarms.cancel(ctx, saved.id)
        if (saved.enabled) {
            Alarms.schedule(ctx, saved)
        } else {
            NotificationManagerCompat.from(ctx).cancel(saved.id)
        }
    }

    fun setEnabled(r: Reminder, enabled: Boolean) = save(r.copy(enabled = enabled))

    fun delete(id: Int) {
        Alarms.cancel(ctx, id)
        NotificationManagerCompat.from(ctx).cancel(id)
        store.delete(id)
    }

    fun markDone(r: Reminder, slot: Long) {
        store.markDone(r, slot)
        NotificationManagerCompat.from(ctx).cancel(r.id)
    }

    fun undoDone(reminderId: Int, slot: Long) = store.undoDone(reminderId, slot)

    fun clearHistory() = store.clearLogs()
}
