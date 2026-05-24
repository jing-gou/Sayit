package org.sayit.voiceime.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

class ClipboardHistoryManager(context: Context) {

    private val appContext = context.applicationContext
    private val store = ClipboardHistoryStore(appContext)
    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastCaptured: String? = null

    fun start() {
        if (listener != null) return
        captureClip("initial")
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            mainHandler.post { captureClip("changed") }
        }
        clipboard.addPrimaryClipChangedListener(listener!!)
    }

    fun stop() {
        listener?.let { clipboard.removePrimaryClipChangedListener(it) }
        listener = null
    }

    fun captureNow() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            captureClip("manual")
        } else {
            mainHandler.post { captureClip("manual") }
        }
    }

    fun getEntries(): List<ClipboardEntry> {
        store.clearExpired()
        return store.getAll()
    }

    private fun captureClip(reason: String) {
        try {
            val text = readPrimaryText() ?: return
            if (text.isBlank()) return
            if (text == lastCaptured) return
            store.add(text)
            lastCaptured = text
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard read denied ($reason): ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard capture failed ($reason)", e)
        }
    }

    private fun readPrimaryText(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(appContext)?.toString()
    }

    companion object {
        private const val TAG = "ClipboardHistory"
    }
}
