package org.sayit.voiceime

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    const val PREFS_NAME = "sayit_settings"
    const val KEY_BALL_SIZE = "ball_size"
    const val KEY_OVERLAY_ALWAYS = "overlay_always"
    const val KEY_CUSTOM_BALL_PATH = "custom_ball_path"
    const val KEY_GESTURE_GUIDE_SHOWN = "gesture_guide_shown"
    const val KEY_PENDING_GESTURE_GUIDE = "pending_gesture_guide"
    const val ACTION_BALL_SETTINGS_CHANGED = "org.sayit.voiceime.BALL_SETTINGS_CHANGED"
    const val ACTION_SHOW_GESTURE_GUIDE = "org.sayit.voiceime.SHOW_GESTURE_GUIDE"
    const val EXTRA_OVERLAY_ONLY = "overlay_only"

    private var prefs: SharedPreferences? = null

    fun setup(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun prefs(context: Context): SharedPreferences {
        setup(context)
        return prefs!!
    }

    private fun getString(key: String, fallback: String): String {
        return prefs?.getString(key, null)?.takeIf { it.isNotEmpty() } ?: fallback
    }

    private fun getBool(key: String, fallback: Boolean): Boolean {
        return prefs?.getBoolean(key, fallback) ?: fallback
    }

    private fun getInt(key: String, fallback: Int): Int {
        return prefs?.getInt(key, fallback) ?: fallback
    }

    val llmApiUrl: String get() = getString("llm_api_url", BuildConfig.LLM_API_URL)
    val llmApiKey: String get() = getString("llm_api_key", BuildConfig.LLM_API_KEY)
    val llmModel: String get() = getString("llm_model", BuildConfig.LLM_MODEL)
    val llmPrompt: String get() = getString("llm_prompt", "你是一个智能助手，请简洁回答用户的问题。回答要准确、有帮助。")
    val translationLang: String get() = getString("translation_lang", "英文")
    val asrApiKey: String get() = getString("asr_api_key", BuildConfig.ASR_API_KEY)
    val asrResourceId: String get() = getString("asr_resource_id", BuildConfig.ASR_RESOURCE_ID)
    val asrWsUrl: String get() = getString("asr_ws_url", BuildConfig.ASR_WS_URL)
    val ballSize: Int get() = getInt(KEY_BALL_SIZE, 50)

    fun readBallSize(context: Context): Int {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_BALL_SIZE, 50)
            .coerceIn(0, 100)
    }
    val overlayAlways: Boolean get() = getBool(KEY_OVERLAY_ALWAYS, true)

    fun readOverlayAlways(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERLAY_ALWAYS, true)
    }
    val customBallImagePath: String? get() {
        val path = prefs?.getString(KEY_CUSTOM_BALL_PATH, null) ?: return null
        return path.takeIf { java.io.File(it).isFile }
    }

    fun readGestureGuideShown(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_GESTURE_GUIDE_SHOWN, false)
    }

    fun markGestureGuideShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_GESTURE_GUIDE_SHOWN, true).apply()
    }

    fun requestGestureGuide(context: Context) {
        prefs(context).edit().putBoolean(KEY_PENDING_GESTURE_GUIDE, true).apply()
    }

    fun consumePendingGestureGuide(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING_GESTURE_GUIDE, false)) return false
        p.edit().remove(KEY_PENDING_GESTURE_GUIDE).apply()
        return true
    }

    fun notifyShowGestureGuide(context: Context) {
        context.applicationContext.sendBroadcast(
            android.content.Intent(ACTION_SHOW_GESTURE_GUIDE)
        )
    }
}
