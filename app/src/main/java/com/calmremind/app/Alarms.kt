package com.calmremind.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules one exact, Doze-proof alarm per reminder (always the *next* dose).
 * When it fires, [AlarmReceiver] shows the notification and schedules the following dose,
 * so reminders keep working with the app closed. [BootReceiver] re-creates them after a restart.
 */
object Alarms {
    const val ACTION_FIRE = "com.calmremind.app.action.FIRE"
    const val ACTION_SNOOZE_FIRE = "com.calmremind.app.action.SNOOZE_FIRE"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_SLOT = "slot_millis"

    private fun alarmManager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(ctx: Context, id: Int, slot: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction(if (snooze) ACTION_SNOOZE_FIRE else ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_SLOT, slot)
        return PendingIntent.getBroadcast(
            ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setExact(ctx: Context, atMillis: Long, pi: PendingIntent) {
        val am = alarmManager(ctx)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    /** Schedule the next dose of [r] after [after]; cancels the alarm if there is none. */
    fun schedule(ctx: Context, r: Reminder, after: Long = System.currentTimeMillis()) {
        val next = Schedule.nextTrigger(r, after)
        val pi = pending(ctx, r.id, next ?: 0L, false)
        if (next == null) {
            alarmManager(ctx).cancel(pi)
        } else {
            setExact(ctx, next, pi)
        }
    }

    fun scheduleSnooze(ctx: Context, id: Int, slot: Long, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setExact(ctx, at, pending(ctx, id, slot, true))
    }

    /** Rings a demo alarm in 10 seconds so the user can lock the phone and check the setup. */
    fun scheduleTest(ctx: Context) {
        val at = System.currentTimeMillis() + 10_000L
        setExact(ctx, at, pending(ctx, AlarmService.TEST_ID, 0L, false))
    }

    /** Cancels both the regular and the snooze alarm of a reminder. */
    fun cancel(ctx: Context, id: Int) {
        val am = alarmManager(ctx)
        am.cancel(pending(ctx, id, 0L, false))
        am.cancel(pending(ctx, id, 0L, true))
    }

    fun scheduleAll(ctx: Context) {
        Store.get(ctx).loadReminders().forEach { r ->
            if (r.enabled) schedule(ctx, r) else cancel(ctx, r.id)
        }
    }
}
