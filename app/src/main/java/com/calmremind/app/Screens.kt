package com.calmremind.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Buttons the UI can trigger that need an Activity (opening system settings). */
class HealthActions(
    val openNotifications: () -> Unit,
    val openExactAlarm: () -> Unit,
    val openBattery: () -> Unit,
    val openFullScreen: () -> Unit,
    val pickRingtone: () -> Unit,
    val testAlarm: () -> Unit
)

@Composable
fun App(state: AppState, health: HealthActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<Reminder?>(null) }
    var showTypePicker by remember { mutableStateOf(false) }

    BackHandler(enabled = editor != null) { editor = null }

    val current = editor
    if (current != null) {
        EditorScreen(
            initial = current,
            isNew = current.id == 0,
            onSave = {
                state.save(it)
                editor = null
            },
            onDelete = {
                state.delete(it.id)
                editor = null
            },
            onClose = { editor = null }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("🗓️", fontSize = 20.sp) }, label = { Text("Today") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("⏰", fontSize = 20.sp) }, label = { Text("Reminders") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("📊", fontSize = 20.sp) }, label = { Text("History") }
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Text("⚙️", fontSize = 20.sp) }, label = { Text("Settings") }
                )
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showTypePicker = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add reminder") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(state, health, onAdd = { tab = 1; showTypePicker = true })
                1 -> RemindersScreen(state, onEdit = { editor = it })
                2 -> HistoryScreen(state)
                else -> SettingsScreen(state, health)
            }
        }
    }

    if (showTypePicker) {
        AlertDialog(
            onDismissRequest = { showTypePicker = false },
            title = { Text("What should we remind you about?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeButton("💧  Drink water", "Every few hours through the day") {
                        showTypePicker = false
                        editor = state.template(ReminderType.WATER)
                    }
                    TypeButton("💊  Take medicine", "Every few hours or at set times") {
                        showTypePicker = false
                        editor = state.template(ReminderType.MEDICINE)
                    }
                    TypeButton("🔔  Something else", "Any custom reminder") {
                        showTypePicker = false
                        editor = state.template(ReminderType.CUSTOM)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTypePicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsScreen(state: AppState, health: HealthActions) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Personalise how your reminders sound and alert you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("🔔  Reminder ringtone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        state.ringtoneUri?.let { uri ->
                            try { android.media.RingtoneManager.getRingtone(context, android.net.Uri.parse(uri))?.getTitle(context) ?: "Selected ringtone" }
                            catch (_: Exception) { "Selected ringtone" }
                        } ?: "Default alarm ringtone",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(onClick = health.pickRingtone, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose ringtone")
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Ringtone volume", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${state.ringtoneVolume}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    androidx.compose.material3.Slider(
                        value = state.ringtoneVolume.toFloat(),
                        onValueChange = { state.updateRingtoneVolume(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 19
                    )
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Vibration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Vibrate when a reminder rings", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.vibrationEnabled, onCheckedChange = state::updateVibrationEnabled)
                    }
                }
            }
        }
        item {
            Text("The selected ringtone, volume and vibration settings apply to future reminder alarms. Your settings are saved on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TypeButton(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// =====================================================================
// TODAY
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(state: AppState, health: HealthActions, onAdd: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val doneSet = remember(state.logs) { state.logs.map { it.reminderId to it.slotMillis }.toSet() }

    val plan = state.reminders
        .filter { it.enabled }
        .map { it to Schedule.slotsOn(it, today, zone) }
        .filter { it.second.isNotEmpty() }

    var total = 0
    var done = 0
    var waterTotal = 0
    var waterDone = 0
    var medTotal = 0
    var medDone = 0
    plan.forEach { (r, slots) ->
        val d = slots.count { (r.id to it) in doneSet }
        total += slots.size
        done += d
        if (r.type == ReminderType.WATER) {
            waterTotal += slots.size; waterDone += d
        } else if (r.type == ReminderType.MEDICINE) {
            medTotal += slots.size; medDone += d
        }
    }

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    "$greeting 👋",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.needsAttention) {
            item { HealthCard(state, health) }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Today's progress",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (total == 0) "Nothing scheduled" else "$done of $total done",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = DoneGreen,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    if (waterTotal > 0) {
                        Text(
                            "💧 $waterDone of $waterTotal glasses of water",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (medTotal > 0) {
                        Text(
                            "💊 $medDone of $medTotal medicine doses",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = health.testAlarm,
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔔  Test the alarm (rings in 10 seconds)") }
        }

        if (plan.isEmpty()) {
            item {
                EmptyHint(
                    title = "No reminders for today",
                    body = "Add a water, medicine or custom reminder and it will show up here.",
                    actionLabel = "Add a reminder",
                    onAction = onAdd
                )
            }
        } else {
            items(plan, key = { it.first.id }) { (r, slots) ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.type.emoji, fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (r.note.isBlank()) describe(r) else r.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val d = slots.count { (r.id to it) in doneSet }
                            Text("$d/${slots.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            slots.forEach { slot ->
                                val isDone = (r.id to slot) in doneSet
                                val minute = Instant.ofEpochMilli(slot).atZone(zone).let { it.hour * 60 + it.minute }
                                val due = !isDone && slot <= now && now < slot + 30 * 60_000L
                                val missed = !isDone && now >= slot + 30 * 60_000L
                                val bg = when {
                                    isDone -> DoneGreen
                                    due -> MaterialTheme.colorScheme.primary
                                    missed -> MissedAmber
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val fg = when {
                                    isDone -> Color.White
                                    due -> MaterialTheme.colorScheme.onPrimary
                                    missed -> MissedAmberText
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(bg)
                                        .clickable {
                                            if (isDone) state.undoDone(r.id, slot) else state.markDone(r, slot)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        (if (isDone) "✓ " else "") + fmtTime(minute),
                                        color = fg,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap a time to mark it done — tap again to undo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HealthCard(state: AppState, actions: HealthActions) {
    val dark = Color(0xFF5C4300)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4DC))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚠️ Make sure reminders always ring", fontWeight = FontWeight.Bold, color = dark)
            if (!state.notificationsOk) HealthRow("Notifications are turned off", "Allow", dark, actions.openNotifications)
            if (!state.exactOk) HealthRow("Exact alarms are not allowed", "Allow", dark, actions.openExactAlarm)
            if (!state.fullScreenOk) HealthRow("Full-screen alarm screen is blocked", "Allow", dark, actions.openFullScreen)
            if (!state.batteryOk) HealthRow("Battery saver can delay reminders", "Fix", dark, actions.openBattery)
            Text(
                "On Xiaomi, Redmi, Oppo, Vivo, Realme, OnePlus and Samsung phones, also switch on " +
                    "“Autostart” / “Run in background”, “Show on lock screen” and " +
                    "“Display pop-up windows while running in background” for this app in your phone's settings.",
                style = MaterialTheme.typography.bodySmall,
                color = dark
            )
        }
    }
}

@Composable
private fun HealthRow(message: String, button: String, textColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), color = textColor, style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun EmptyHint(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🌿", fontSize = 36.sp)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

// =====================================================================
// REMINDERS LIST
// =====================================================================

@Composable
fun RemindersScreen(state: AppState, onEdit: (Reminder) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Your reminders",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (state.reminders.isEmpty()) {
            item {
                EmptyHint(
                    title = "No reminders yet",
                    body = "Tap “Add reminder” to create your first one — water, medicine or anything else."
                )
            }
        }
        items(state.reminders, key = { it.id }) { r ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onEdit(r) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text(r.type.emoji, fontSize = 24.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            describe(r),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = r.enabled, onCheckedChange = { state.setEnabled(r, it) })
                }
            }
        }
    }
}

// =====================================================================
// HISTORY & STATS
// =====================================================================

private data class DayStat(val date: LocalDate, val total: Int, val done: Int)

private sealed interface HRow {
    data class Head(val text: String) : HRow
    data class Item(val entry: LogEntry) : HRow
}

@Composable
fun HistoryScreen(state: AppState) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

    val stats = remember(state.logs, state.reminders, today) {
        val doneByDay = HashMap<LocalDate, Int>()
        state.logs.forEach { l ->
            val ms = if (l.slotMillis != 0L) l.slotMillis else l.doneMillis
            val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            doneByDay[d] = (doneByDay[d] ?: 0) + 1
        }
        (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            var total = 0
            state.reminders.forEach { r ->
                val created = Instant.ofEpochMilli(r.createdAt).atZone(zone).toLocalDate()
                if (r.enabled && !d.isBefore(created)) total += Schedule.slotsOn(r, d, zone).size
            }
            val done = doneByDay[d] ?: 0
            DayStat(d, maxOf(total, done), done)
        }
    }
    var weekTotal = 0
    var weekDone = 0
    stats.forEach { weekTotal += it.total; weekDone += it.done }
    val rate = if (weekTotal == 0) 0 else (weekDone * 100) / weekTotal
    val todayDone = stats.last().done

    val rows = remember(state.logs, today) {
        val out = mutableListOf<HRow>()
        var last: LocalDate? = null
        state.logs.sortedByDescending { it.doneMillis }.take(80).forEach { e ->
            val d = Instant.ofEpochMilli(e.doneMillis).atZone(zone).toLocalDate()
            if (d != last) {
                out.add(HRow.Head(dayLabel(d, today)))
                last = d
            }
            out.add(HRow.Item(e))
        }
        out
    }

    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("7-day rate", "$rate%", Modifier.weight(1f))
                StatTile("Done today", "$todayDone", Modifier.weight(1f))
                StatTile("All time", "${state.logs.size}", Modifier.weight(1f))
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().height(140.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        stats.forEach { s ->
                            val ratio = if (s.total == 0) 0f else s.done.toFloat() / s.total
                            val full = s.total > 0 && s.done >= s.total
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (s.total == 0) "–" else "${s.done}/${s.total}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                val barHeight: Dp = (80 * ratio).dp.coerceAtLeast(4.dp)
                                Box(
                                    Modifier
                                        .width(22.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (full) DoneGreen
                                            else MaterialTheme.colorScheme.primary.copy(alpha = if (s.done == 0) 0.25f else 1f)
                                        )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    s.date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (s.date == today) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                EmptyHint(
                    title = "Nothing logged yet",
                    body = "When you tap “Done” on a notification or tick a time on the Today screen, it shows up here."
                )
            }
        } else {
            item {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(rows) { row ->
                when (row) {
                    is HRow.Head -> Text(
                        row.text,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    is HRow.Item -> {
                        val e = row.entry
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(e.emoji, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (e.slotMillis != 0L) {
                                    Text(
                                        "Scheduled " + timeFmt.format(Instant.ofEpochMilli(e.slotMillis).atZone(zone)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                "✓ " + timeFmt.format(Instant.ofEpochMilli(e.doneMillis).atZone(zone)),
                                color = DoneGreen,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
            item {
                TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes your done-log and statistics. Your reminders are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    state.clearHistory()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
}
