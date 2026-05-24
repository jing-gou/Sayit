package org.sayit.voiceime.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
class ClipboardHistoryManager(context: Context) {

    private val appContext = context.applicationContext
    private val store = ClipboardHistoryStore(appContext)
    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastCaptured: String? = null

    fun start() {
        if (listener != null) return
        captureClip()
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            mainHandler.post { captureClip() }
        }
        clipboard.addPrimaryClipChangedListener(listener!!)
    }

    fun stop() {
        listener?.let { clipboard.removePrimaryClipChangedListener(it) }
        listener = null
    }

    fun captureNow() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            captureClip()
        } else {
            mainHandler.post { captureClip() }
        }
    }

    fun getEntries(): List<ClipboardEntry> {
        store.clearExpired()
        return store.getAll()
    }

    private fun captureClip() {
        try {
            val text = readPrimaryText() ?: return
            if (text.isBlank()) return
            if (text == lastCaptured) return
            store.add(text)
            lastCaptured = text
        } catch (_: Exception) {
        }
    }

    private fun readPrimaryText(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(appContext)?.toString()
    }

}
