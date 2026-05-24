package org.sayit.voiceime.clipboard

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ClipboardHistoryStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "clipboard_history.json")
    private val gson = Gson()
    private val lock = Any()

    fun getAll(): List<ClipboardEntry> = synchronized(lock) {
        val loaded = loadLocked()
        val pruned = pruneLocked(loaded)
        if (pruned.size != loaded.size) saveLocked(pruned)
        pruned
    }

    fun add(text: String) = synchronized(lock) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val clipped = trimmed.take(MAX_TEXT_LENGTH)
        val entries = pruneLocked(loadLocked())
        if (entries.firstOrNull()?.text == clipped) return
        entries.add(0, ClipboardEntry(text = clipped))
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.lastIndex)
        }
        saveLocked(entries)
    }

    fun clearExpired() = synchronized(lock) {
        saveLocked(pruneLocked(loadLocked()))
    }

    private fun loadLocked(): MutableList<ClipboardEntry> {
        if (!file.exists()) return mutableListOf()
        return try {
            val json = file.readText()
            val type = object : TypeToken<MutableList<ClipboardEntry>>() {}.type
            gson.fromJson<MutableList<ClipboardEntry>>(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveLocked(entries: List<ClipboardEntry>) {
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(entries))
    }

    private fun pruneLocked(entries: MutableList<ClipboardEntry>): MutableList<ClipboardEntry> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val pruned = entries.filter { it.timestamp >= cutoff }.toMutableList()
        pruned.sortByDescending { it.timestamp }
        return pruned
    }

    companion object {
        const val RETENTION_MS = 48L * 60 * 60 * 1000
        private const val MAX_ENTRIES = 500
        private const val MAX_TEXT_LENGTH = 8192
    }
}
