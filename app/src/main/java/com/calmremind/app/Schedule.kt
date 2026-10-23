package com.calmremind.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

/** Pure schedule maths: which doses happen on a given day, and when the next one is due. */
object Schedule {

    fun occursOn(r: Reminder, date: LocalDate): Boolean = when (r.repeat) {
        RepeatMode.DAILY -> true
        RepeatMode.ALTERNATE -> Math.floorMod(date.toEpochDay() - r.anchorEpochDay, 2L) == 0L
        RepeatMode.WEEKDAYS -> date.dayOfWeek.value in r.weekdays
    }

    fun intervalDoses(start: Int, end: Int, step: Int): List<Int> {
        val s = step.coerceAtLeast(5)
        val out = ArrayList<Int>()
        var t = start
        while (t <= end && t < 24 * 60) {
            out.add(t)
            t += s
        }
        return out
    }

    /** Minutes-after-midnight of every dose on [date] (empty if the reminder skips that day). */
    fun dosesOn(r: Reminder, date: LocalDate): List<Int> {
        if (!occursOn(r, date)) return emptyList()
        if (r.type == ReminderType.MEDICINE && r.medicinePlans.isNotEmpty()) {
            // A parent reminder can contain many medicines, each with its own course dates and times.
            return r.medicinePlans.flatMap { plan ->
                val from = runCatching { if (plan.fromDate.isBlank()) null else LocalDate.parse(plan.fromDate) }.getOrNull()
                val to = runCatching { if (plan.toDate.isBlank()) null else LocalDate.parse(plan.toDate) }.getOrNull()
                if ((from != null && date.isBefore(from)) || (to != null && date.isAfter(to))) emptyList()
                else (plan.times + plan.moments.mapNotNull { MEDICINE_MOMENTS[it] }).distinct()
            }.distinct().sorted()
        }
        if (r.type == ReminderType.MEDICINE) {
            val from = runCatching { if (r.medicineFromDate.isBlank()) null else LocalDate.parse(r.medicineFromDate) }.getOrNull()
            val to = runCatching { if (r.medicineToDate.isBlank()) null else LocalDate.parse(r.medicineToDate) }.getOrNull()
            if (from != null && date.isBefore(from)) return emptyList()
            if (to != null && date.isAfter(to)) return emptyList()
        }
        val regularDoses = when (r.mode) {
            ScheduleMode.TIMES -> r.times.distinct()
            ScheduleMode.INTERVAL -> intervalDoses(r.windowStart, r.windowEnd, r.intervalMinutes)
        }
        val routineDoses = if (r.type == ReminderType.MEDICINE) r.medicineMoments.mapNotNull { MEDICINE_MOMENTS[it] } else emptyList()
        return (regularDoses + routineDoses).distinct().sorted()
    }

    /** Default clock times for routine-based medicine moments. Users can select several moments. */
    val MEDICINE_MOMENTS: Map<String, Int> = linkedMapOf(
        "AFTER_BRUSH" to 7 * 60,
        "BEFORE_BREAKFAST" to 7 * 60 + 30,
        "AFTER_BREAKFAST" to 8 * 60 + 30,
        "BEFORE_LUNCH" to 11 * 60 + 45,
        "AFTER_LUNCH" to 13 * 60,
        "EVENING" to 17 * 60,
        "BEFORE_DINNER" to 18 * 60 + 45,
        "AFTER_DINNER" to 20 * 60,
        "BEFORE_SLEEP" to 22 * 60
    )

    fun slotMillis(date: LocalDate, minute: Int, zone: ZoneId): Long =
        date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()

    fun slotsOn(r: Reminder, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Long> =
        dosesOn(r, date).map { slotMillis(date, it, zone) }

    /** First dose strictly after [after], looking up to two weeks ahead. Null if none / disabled. */
    fun nextTrigger(r: Reminder, after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!r.enabled) return null
        val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        val medicineStart = if (r.type == ReminderType.MEDICINE) {
            (listOf(r.medicineFromDate) + r.medicinePlans.map { it.fromDate }).filter { it.isNotBlank() }
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull()
        } else null
        val daysUntilCourse = medicineStart?.let { ChronoUnit.DAYS.between(start, it).coerceAtLeast(0).toInt() } ?: 0
        // Look far enough ahead to support medicine courses that begin in the future.
        val horizon = maxOf(14, daysUntilCourse + 14).coerceAtMost(3660)
        for (d in 0..horizon) {
            val date = start.plusDays(d.toLong())
            for (m in dosesOn(r, date)) {
                val millis = slotMillis(date, m, zone)
                if (millis > after) return millis
            }
        }
        return null
    }
}

// ---------- Display helpers ----------

fun fmtTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.getDefault(), "%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
}

fun fmtInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

fun defaultName(t: ReminderType): String = when (t) {
    ReminderType.WATER -> "Drink water"
    ReminderType.MEDICINE -> "Take medicine"
    ReminderType.CUSTOM -> "My reminder"
}

fun repeatText(r: Reminder): String = when (r.repeat) {
    RepeatMode.DAILY -> "Every day"
    RepeatMode.ALTERNATE -> "Every other day"
    RepeatMode.WEEKDAYS -> r.weekdays.sorted().joinToString(", ") {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}

fun describe(r: Reminder): String {
    if (r.type == ReminderType.MEDICINE && r.medicinePlans.isNotEmpty()) {
        return r.medicinePlans.joinToString(" · ") { plan ->
            val planTimes = (plan.times + plan.moments.mapNotNull { Schedule.MEDICINE_MOMENTS[it] }).distinct().sorted()
            "${plan.name}: " + (if (planTimes.isEmpty()) "No times set" else planTimes.joinToString(", ") { fmtTime(it) })
        } + " · ${repeatText(r)}"
    }
    val whenText = when (r.mode) {
        ScheduleMode.INTERVAL ->
            "Every ${fmtInterval(r.intervalMinutes)} · ${fmtTime(r.windowStart)} – ${fmtTime(r.windowEnd)}"
        ScheduleMode.TIMES ->
            if (r.times.isEmpty()) "No times set" else "At " + r.times.sorted().joinToString(", ") { fmtTime(it) }
    }
    return "$whenText · ${repeatText(r)}"
}
