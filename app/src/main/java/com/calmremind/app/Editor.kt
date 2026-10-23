package com.calmremind.app

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate

private enum class Picker { START, END, ADD, MEDICINE_TIME }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    initial: Reminder,
    isNew: Boolean,
    onSave: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onClose: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var note by remember { mutableStateOf(initial.note) }
    var type by remember { mutableStateOf(initial.type) }
    var mode by remember { mutableStateOf(initial.mode) }
    var interval by remember { mutableIntStateOf(initial.intervalMinutes) }
    var wStart by remember { mutableIntStateOf(initial.windowStart) }
    var wEnd by remember { mutableIntStateOf(initial.windowEnd) }
    var times by remember { mutableStateOf(initial.times) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var weekdays by remember { mutableStateOf(initial.weekdays) }
    var anchor by remember { mutableLongStateOf(initial.anchorEpochDay) }
    var medicineFromDate by remember { mutableStateOf(initial.medicineFromDate) }
    var medicineToDate by remember { mutableStateOf(initial.medicineToDate) }
    var medicineMoments by remember { mutableStateOf(initial.medicineMoments.toSet()) }
    var medicinePlans by remember { mutableStateOf(
        initial.medicinePlans.ifEmpty {
            val legacyNames = initial.medicineNames.ifEmpty { listOf("") }
            legacyNames.map { MedicinePlan(name = it, fromDate = initial.medicineFromDate, toDate = initial.medicineToDate, moments = initial.medicineMoments, times = if (initial.mode == ScheduleMode.TIMES) initial.times else emptyList()) }
        }
    ) }
    var medicinePickerIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val todayEpoch = remember { LocalDate.now().toEpochDay() }

    fun trySave() {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> error = "Please give this reminder a name."
            type != ReminderType.MEDICINE && mode == ScheduleMode.INTERVAL && wEnd < wStart -> error = "The end time must be after the start time."
            type != ReminderType.MEDICINE && mode == ScheduleMode.TIMES && times.isEmpty() -> error = "Add at least one time."
            repeat == RepeatMode.WEEKDAYS && weekdays.isEmpty() -> error = "Pick at least one day of the week."
            type == ReminderType.MEDICINE && medicinePlans.none { it.name.isNotBlank() } -> error = "Add at least one medicine name."
            type == ReminderType.MEDICINE && medicinePlans.any { it.name.isNotBlank() && it.times.isEmpty() && it.moments.isEmpty() } -> error = "Select at least one routine or time for every medicine."
            type == ReminderType.MEDICINE && medicinePlans.any { plan -> plan.fromDate.isNotBlank() && plan.toDate.isNotBlank() && runCatching { LocalDate.parse(plan.toDate).isBefore(LocalDate.parse(plan.fromDate)) }.getOrDefault(false) } -> error = "Each medicine course end date must be on or after its start date."
            else -> onSave(
                initial.copy(
                    name = if (type == ReminderType.MEDICINE && trimmed == defaultName(ReminderType.MEDICINE)) {
                        medicinePlans.map { it.name.trim() }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { trimmed }
                    } else trimmed,
                    note = note.trim(),
                    type = type,
                    intervalMinutes = interval,
                    windowStart = wStart,
                    windowEnd = wEnd,
                    repeat = repeat,
                    weekdays = weekdays,
                    anchorEpochDay = anchor,
                    medicineNames = if (type == ReminderType.MEDICINE) medicinePlans.map { it.name.trim() }.filter { it.isNotBlank() } else emptyList(),
                    medicinePlans = if (type == ReminderType.MEDICINE) medicinePlans.filter { it.name.isNotBlank() }.map { it.copy(name = it.name.trim(), moments = it.moments.distinct(), times = it.times.distinct().sorted()) } else emptyList(),
                    // Per-medicine schedules are stored in medicinePlans; clear legacy shared schedule fields.
                    medicineFromDate = "", medicineToDate = "", medicineMoments = emptyList(),
                    mode = if (type == ReminderType.MEDICINE) ScheduleMode.TIMES else mode,
                    times = if (type == ReminderType.MEDICINE) emptyList() else times
                )
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New reminder" else "Edit reminder") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- What ----
            SectionCard("What is it for?") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderType.values().forEach { t ->
                        Pill("${t.emoji} ${t.label}", type == t, onClick = {
                            if (name.isBlank() || name == defaultName(type)) name = defaultName(t)
                            type = t
                        })
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional) — e.g. 1 tablet after food") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (type == ReminderType.MEDICINE) {
                SectionCard("Medicines and individual schedules") {
                    Text("Set dates, routines, and times separately for each medicine.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val moments = listOf(
                        "AFTER_BRUSH" to "After brushing", "BEFORE_BREAKFAST" to "Before breakfast",
                        "AFTER_BREAKFAST" to "After breakfast", "BEFORE_LUNCH" to "Before lunch",
                        "AFTER_LUNCH" to "After lunch", "EVENING" to "Evening",
                        "BEFORE_DINNER" to "Before dinner", "AFTER_DINNER" to "After dinner",
                        "BEFORE_SLEEP" to "Before sleep"
                    )
                    medicinePlans.forEachIndexed { index, plan ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Medicine ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (medicinePlans.size > 1) TextButton(onClick = { medicinePlans = medicinePlans.filterIndexed { i, _ -> i != index } }) { Text("Remove") }
                                }
                                OutlinedTextField(value = plan.name, onValueChange = { value -> medicinePlans = medicinePlans.mapIndexed { i, old -> if (i == index) old.copy(name = value) else old } }, label = { Text("Medicine name") }, placeholder = { Text("e.g. Vitamin D") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Text("Course dates (optional)", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { showDatePicker(context, plan.fromDate) { value -> medicinePlans = medicinePlans.mapIndexed { i, old -> if (i == index) old.copy(fromDate = value) else old } } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(if (plan.fromDate.isBlank()) "Start date" else displayDate(plan.fromDate)) }
                                    OutlinedButton(onClick = { showDatePicker(context, plan.toDate) { value -> medicinePlans = medicinePlans.mapIndexed { i, old -> if (i == index) old.copy(toDate = value) else old } } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(if (plan.toDate.isBlank()) "End date" else displayDate(plan.toDate)) }
                                }
                                Text("Routine (select any)", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    moments.forEach { (key, label) -> Pill(label, key in plan.moments, onClick = { medicinePlans = medicinePlans.mapIndexed { i, old -> if (i == index) old.copy(moments = if (key in old.moments) old.moments - key else old.moments + key) else old } }) }
                                }
                                Text("Extra reminder times", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    plan.times.sorted().forEach { minute -> Pill("${fmtTime(minute)}  ✕", true, onClick = { medicinePlans = medicinePlans.mapIndexed { i, old -> if (i == index) old.copy(times = old.times - minute) else old } }) }
                                }
                                OutlinedButton(onClick = { medicinePickerIndex = index; picker = Picker.MEDICINE_TIME }) { Text("+ Add time for ${plan.name.ifBlank { "this medicine" }}") }
                                val allTimes = (plan.times + plan.moments.mapNotNull { Schedule.MEDICINE_MOMENTS[it] }).distinct().sorted()
                                Text(if (allTimes.isEmpty()) "Choose at least one routine or time." else "This medicine will remind at: ${allTimes.joinToString(", ") { fmtTime(it) }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    OutlinedButton(onClick = { medicinePlans = medicinePlans + MedicinePlan(name = "") }, modifier = Modifier.fillMaxWidth()) { Text("+ Add Medicine") }
                }
            }

            // ---- When ----
            SectionCard("When should it remind you?") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Every few hours", mode == ScheduleMode.INTERVAL, onClick = { mode = ScheduleMode.INTERVAL })
                    Pill("At exact times", mode == ScheduleMode.TIMES, onClick = { mode = ScheduleMode.TIMES })
                }

                if (mode == ScheduleMode.INTERVAL) {
                    Text("Repeat every", style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalIconButton(onClick = {
                            interval = (interval - (if (interval <= 60) 15 else 30)).coerceAtLeast(15)
                        }) { Text("−", fontSize = 22.sp) }
                        Text(
                            fmtInterval(interval),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalIconButton(onClick = {
                            interval = (interval + (if (interval < 60) 15 else 30)).coerceAtMost(24 * 60)
                        }) { Text("+", fontSize = 22.sp) }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 60, 120, 180, 240, 360, 480).forEach { m ->
                            Pill(fmtInterval(m), interval == m, onClick = { interval = m })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeButton("First reminder", wStart, Modifier.weight(1f)) { picker = Picker.START }
                        TimeButton("Last reminder", wEnd, Modifier.weight(1f)) { picker = Picker.END }
                    }
                    val preview = Schedule.intervalDoses(wStart, wEnd, interval)
                    Text(
                        if (preview.isEmpty()) {
                            "No reminders — the last time must be after the first."
                        } else {
                            "${preview.size} reminders a day: " +
                                preview.take(8).joinToString(", ") { fmtTime(it) } +
                                (if (preview.size > 8) " …" else "")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        times.sorted().forEach { m ->
                            Pill("${fmtTime(m)}  ✕", true, onClick = { times = times - m })
                        }
                    }
                    OutlinedButton(onClick = { picker = Picker.ADD }) { Text("+ Add a time") }
                    if (times.isEmpty()) {
                        Text(
                            "No times yet — tap “Add a time”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Tap a time to remove it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- Which days ----
            SectionCard("On which days?") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pill("Every day", repeat == RepeatMode.DAILY, onClick = { repeat = RepeatMode.DAILY })
                    Pill("Every other day", repeat == RepeatMode.ALTERNATE, onClick = { repeat = RepeatMode.ALTERNATE })
                    Pill("Choose days", repeat == RepeatMode.WEEKDAYS, onClick = { repeat = RepeatMode.WEEKDAYS })
                }
                when (repeat) {
                    RepeatMode.ALTERNATE -> {
                        Text("Start on", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Today", anchor == todayEpoch, onClick = { anchor = todayEpoch })
                            Pill("Tomorrow", anchor == todayEpoch + 1, onClick = { anchor = todayEpoch + 1 })
                        }
                    }
                    RepeatMode.WEEKDAYS -> {
                        val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            labels.forEachIndexed { i, label ->
                                val day = i + 1
                                val selected = day in weekdays
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { weekdays = if (selected) weekdays - day else weekdays + day },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    RepeatMode.DAILY -> Unit
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = { trySave() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Save reminder", style = MaterialTheme.typography.titleMedium) }

            if (!isNew) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Delete reminder", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picker?.let { p ->
        TimePickerDialog(
            title = when (p) {
                Picker.START -> "First reminder of the day"
                Picker.END -> "Last reminder of the day"
                Picker.ADD -> "Add a reminder time"
                Picker.MEDICINE_TIME -> "Add time for ${medicinePlans.getOrNull(medicinePickerIndex)?.name ?: "medicine"}"
            },
            initialMinutes = when (p) {
                Picker.START -> wStart
                Picker.END -> wEnd
                Picker.ADD -> 9 * 60
                Picker.MEDICINE_TIME -> medicinePlans.getOrNull(medicinePickerIndex)?.times?.lastOrNull()?.plus(240)?.rem(1440) ?: 9 * 60
            },
            onDismiss = { picker = null },
            onConfirm = { minutes ->
                when (p) {
                    Picker.START -> wStart = minutes
                    Picker.END -> wEnd = minutes
                    Picker.ADD -> times = (times + minutes).distinct().sorted()
                    Picker.MEDICINE_TIME -> medicinePlans = medicinePlans.mapIndexed { i, old -> if (i == medicinePickerIndex) old.copy(times = (old.times + minutes).distinct().sorted()) else old }
                }
                picker = null
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reminder?") },
            text = { Text("“${initial.name}” will stop reminding you. Your past history is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(initial)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun showDatePicker(context: android.content.Context, current: String, onSelected: (String) -> Unit) {
    val date = runCatching { if (current.isBlank()) LocalDate.now() else LocalDate.parse(current) }.getOrDefault(LocalDate.now())
    DatePickerDialog(context, { _, year, month, day ->
        onSelected(LocalDate.of(year, month + 1, day).toString())
    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}

private fun displayDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
}.getOrDefault(iso)

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TimeButton(label: String, minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(fmtTime(minutes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    style = MaterialTheme.typography.labelMedium
                )
                TimePicker(state = pickerState)
                Row(Modifier.height(40.dp).fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) { Text("OK") }
                }
            }
        }
    }
}
