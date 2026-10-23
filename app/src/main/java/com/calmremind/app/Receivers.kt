package com.calmremind.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** A reminder is due: arm the following dose, then ring the alarm. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(Alarms.EXTRA_ID, -1)
        if (id < 0) return
        val slot = intent.getLongExtra(Alarms.EXTRA_SLOT, 0L)

        val reminder = AlarmService.resolve(context, id)
        if (reminder == null) {
            Alarms.cancel(context, id)
            return
        }

        // Arm the next dose first, so the schedule survives even if ringing fails.
        // A snooze alarm must not disturb the regular schedule, which is already armed.
        if (intent.action == Alarms.ACTION_FIRE && id != AlarmService.TEST_ID) {
            Alarms.schedule(context, reminder, maxOf(System.currentTimeMillis(), slot))
        }

        if (!reminder.enabled) return
        try {
            AlarmService.start(context, id, slot)
        } catch (e: Exception) {
            // Android refused to start the ringing service: fall back to a normal notification.
            Notifier.show(context, reminder, slot)
        }
    }
}

/** Handles the Done / Snooze / Stop buttons on the notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(Alarms.EXTRA_ID, -1)
        if (id < 0) return
        val slot = intent.getLongExtra(Alarms.EXTRA_SLOT, 0L)
        when (intent.action) {
            Notifier.ACTION_DONE -> AlarmService.done(context, id, slot)
            Notifier.ACTION_SNOOZE -> AlarmService.snooze(context, id, slot)
            Notifier.ACTION_STOP -> AlarmService.dismiss(context, id)
        }
    }
}

/** Phone restarted, app updated, clock or time zone changed: rebuild every alarm. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.scheduleAll(context)
    }
}
