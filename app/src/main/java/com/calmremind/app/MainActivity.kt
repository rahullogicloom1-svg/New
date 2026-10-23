package com.calmremind.app

import android.Manifest
import android.content.Intent
import android.media.RingtoneManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var state: AppState

    private val ringtonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            state.updateRingtoneUri(picked?.toString())
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            state.refreshHealth()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Notifier.ensureChannel(this)
        state = AppState(applicationContext)

        // Heal the schedule on every launch (cheap, and guards against OEM task killers).
        Alarms.scheduleAll(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val health = HealthActions(
            openNotifications = { openSafely(notificationSettingsIntent()) },
            openExactAlarm = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openSafely(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                    )
                }
            },
            openBattery = {
                val direct = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                if (!openSafely(direct)) {
                    openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            },
            openFullScreen = {
                if (Build.VERSION.SDK_INT >= 34) {
                    if (!openSafely(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                        )
                    ) {
                        openSafely(notificationSettingsIntent())
                    }
                }
            },
            pickRingtone = {
                val current = state.ringtoneUri?.let(Uri::parse)
                    ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose reminder ringtone")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                }
                ringtonePicker.launch(picker)
            },
            testAlarm = {
                Alarms.scheduleTest(this)
                Toast.makeText(
                    this,
                    "Alarm rings in 10 seconds — lock your phone now to see the full screen.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        setContent {
            CalmRemindTheme {
                App(state, health)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        state.refresh()
        state.refreshHealth()
        Alarms.scheduleAll(this)
    }

    override fun onDestroy() {
        state.close()
        super.onDestroy()
    }

    private fun notificationSettingsIntent() =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

    private fun openSafely(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
}
