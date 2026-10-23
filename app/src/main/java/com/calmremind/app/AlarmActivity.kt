package com.calmremind.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId

/** The full-screen "alarm is ringing" page, shown over the lock screen. */
class AlarmActivity : ComponentActivity() {

    private var reminder by mutableStateOf<Reminder?>(null)
    private var slot by mutableStateOf(0L)

    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        if (!load(intent)) {
            finish()
            return
        }
        ContextCompat.registerReceiver(
            this, stoppedReceiver, IntentFilter(AlarmService.ACTION_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setContent {
            CalmRemindTheme {
                val r = reminder
                if (r != null) {
                    AlarmScreen(
                        reminder = r,
                        slot = slot,
                        onDone = {
                            AlarmService.done(this, r.id, slot)
                            finish()
                        },
                        onSnooze = {
                            AlarmService.snooze(this, r.id, slot)
                            finish()
                        },
                        onStop = {
                            AlarmService.dismiss(this, r.id)
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!load(intent)) finish()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stoppedReceiver)
        } catch (e: Exception) {
            // Was never registered.
        }
        super.onDestroy()
    }

    private fun load(i: Intent): Boolean {
        val id = i.getIntExtra(Alarms.EXTRA_ID, -1)
        if (id < 0) return false
        val r = AlarmService.resolve(this, id) ?: return false
        reminder = r
        slot = i.getLongExtra(Alarms.EXTRA_SLOT, 0L)
        return true
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@androidx.compose.runtime.Composable
private fun AlarmScreen(
    reminder: Reminder,
    slot: Long,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit
) {
    val minute = if (slot != 0L) {
        Instant.ofEpochMilli(slot).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
    } else {
        val now = java.time.LocalTime.now()
        now.hour * 60 + now.minute
    }
    val text = when {
        reminder.note.isNotBlank() -> reminder.note
        reminder.type == ReminderType.WATER -> "Time for a glass of water"
        reminder.type == ReminderType.MEDICINE -> "Time to take your medicine"
        else -> "Your reminder is due"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(reminder.type.emoji, fontSize = 96.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            fmtTime(minute),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(8.dp))
        Text(
            reminder.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DoneGreen, contentColor = androidx.compose.ui.graphics.Color.White)
        ) { Text("✓  Done", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("Snooze ${Notifier.SNOOZE_MINUTES} min", fontSize = 18.sp) }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("Stop", fontSize = 16.sp) }
    }
}
