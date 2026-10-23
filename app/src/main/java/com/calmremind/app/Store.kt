package com.calmremind.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Tiny offline storage (SharedPreferences + JSON). Everything stays on the phone. */
class Store private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("calm_reminders_store", Context.MODE_PRIVATE)

    // ---------- Reminders ----------

    @Synchronized
    fun loadReminders(): List<Reminder> {
        val raw = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { Reminder.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveReminders(list: List<Reminder>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs.edit().putString(KEY_REMINDERS, a.toString()).commit()
    }

    @Synchronized
    fun nextId(): Int {
        val n = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, n + 1).commit()
        return n
    }

    @Synchronized
    fun upsert(r: Reminder) {
        val list = loadReminders().toMutableList()
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else list.add(r)
        saveReminders(list)
    }

    @Synchronized
    fun delete(id: Int) {
        saveReminders(loadReminders().filter { it.id != id })
    }

    fun get(id: Int): Reminder? = loadReminders().firstOrNull { it.id == id }

    // ---------- History ----------

    @Synchronized
    fun loadLogs(): List<LogEntry> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { LogEntry.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveLogs(list: List<LogEntry>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs.edit().putString(KEY_LOGS, a.toString()).commit()
    }

    @Synchronized
    fun markDone(r: Reminder, slotMillis: Long) {
        val logs = loadLogs().toMutableList()
        if (slotMillis != 0L && logs.any { it.reminderId == r.id && it.slotMillis == slotMillis }) return
        val now = System.currentTimeMillis()
        logs.add(LogEntry(r.id, r.name, r.type.emoji, slotMillis, now))
        saveLogs(logs.takeLast(MAX_LOGS))
    }

    @Synchronized
    fun undoDone(reminderId: Int, slotMillis: Long) {
        saveLogs(loadLogs().filterNot { it.reminderId == reminderId && it.slotMillis == slotMillis })
    }

    @Synchronized
    fun clearLogs() {
        saveLogs(emptyList())
    }

    // ---------- Change listener (lets the UI refresh itself) ----------

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        private const val KEY_REMINDERS = "reminders"
        private const val KEY_LOGS = "logs"
        private const val KEY_NEXT_ID = "next_id"
        private const val MAX_LOGS = 3000

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
