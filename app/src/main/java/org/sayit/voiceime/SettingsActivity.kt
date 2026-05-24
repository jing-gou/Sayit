package org.sayit.voiceime

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.sayit.voiceime.widget.FloatingBallConfig

class SettingsActivity : AppCompatActivity() {

    private lateinit var pickImageLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var prefs: SharedPreferences

    private lateinit var llmApiUrl: EditText
    private lateinit var llmApiKey: EditText
    private lateinit var llmModel: EditText
    private lateinit var llmPrompt: EditText

    private lateinit var asrApiKey: EditText
    private lateinit var asrResourceId: EditText
    private lateinit var asrWsUrl: EditText

    private lateinit var translationLang: Spinner
    private lateinit var asrLanguage: Spinner
    private lateinit var ballSizeMode: SeekBar
    private lateinit var ballSizeLabel: TextView
    private lateinit var overlayMode: Switch

    private lateinit var customBallPreview: ImageView
    private var customBallImagePath: String? = null

    private val bgColor = Color.parseColor("#121218")
    private val cardColor = Color.parseColor("#1E1E2E")
    private val accentColor = Color.parseColor("#6366F1")
    private val textPrimary = Color.parseColor("#F0F0F5")
    private val textSecondary = Color.parseColor("#9090A0")
    private val fieldBg = Color.parseColor("#2A2A3C")

    companion object {
        const val PREFS_NAME = AppSettings.PREFS_NAME
        private const val TAG = "SettingsActivity"
        const val KEY_LLM_API_URL = "llm_api_url"
        const val KEY_LLM_API_KEY = "llm_api_key"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LLM_PROMPT = "llm_prompt"
        const val KEY_ASR_API_KEY = "asr_api_key"
        const val KEY_ASR_RESOURCE_ID = "asr_resource_id"
        const val KEY_ASR_WS_URL = "asr_ws_url"
        const val KEY_TRANSLATION_LANG = "translation_lang"
        const val KEY_ASR_LANGUAGE = "asr_language"
        const val KEY_BALL_SIZE = AppSettings.KEY_BALL_SIZE
        const val KEY_OVERLAY_ALWAYS = AppSettings.KEY_OVERLAY_ALWAYS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) onImagePicked(uri)
        }
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }

        // Header
        root.addView(TextView(this).apply {
            text = "Sayit"
            textSize = 28f
            setTextColor(textPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "语音输入法设置"
            textSize = 14f
            setTextColor(textSecondary)
            setPadding(0, dp(4), 0, dp(24))
        })

        // --- Floating Ball ---
        val ballCard = createCard()
        ballCard.addSectionTitle("悬浮球")

        ballSizeLabel = TextView(this).apply {
            text = sizeLabel(prefs.getInt(KEY_BALL_SIZE, 50))
            textSize = 14f
            setTextColor(textSecondary)
        }
        ballCard.addView(ballSizeLabel)
        ballSizeMode = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt(KEY_BALL_SIZE, 50)
            setPadding(0, dp(8), 0, dp(8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    ballSizeLabel.text = sizeLabel(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {
                    applyBallSize(sb.progress)
                }
            })
        }
        ballCard.addView(ballSizeMode)

        ballCard.addFieldLabel("自定义悬浮球图片")
        val previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        customBallPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) }
            background = rounded(fieldBg, dp(12))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        previewRow.addView(customBallPreview)
        previewRow.addView(createSecondaryButton("选择图片") { pickImage() })
        previewRow.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 0)
        })
        previewRow.addView(createSecondaryButton("清除") { clearCustomBallImage() })
        ballCard.addView(previewRow)

        overlayMode = Switch(this).apply {
            text = "悬浮球常驻显示"
            isChecked = prefs.getBoolean(KEY_OVERLAY_ALWAYS, true)
            setTextColor(textPrimary)
            setPadding(0, dp(8), 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(KEY_OVERLAY_ALWAYS, isChecked).commit()
                notifyBallSettingsChanged(overlayOnly = true)
                showToast(if (isChecked) "悬浮球将常驻显示" else "悬浮球仅在输入时显示")
            }
        }
        ballCard.addView(overlayMode)
        ballCard.addView(TextView(this).apply {
            text = "关闭后仅在输入时显示悬浮球"
            textSize = 12f
            setTextColor(textSecondary)
        })
        ballCard.addView(createSecondaryButton("查看手势教学") {
            AppSettings.requestGestureGuide(this)
            AppSettings.notifyShowGestureGuide(this)
            showToast("手势引导将在输入界面显示")
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        })
        root.addView(ballCard)

        // --- LLM ---
        val llmCard = createCard()
        llmCard.addSectionTitle("大模型 (LLM)")
        llmApiUrl = llmCard.addField("API 地址", prefs.getString(KEY_LLM_API_URL, BuildConfig.LLM_API_URL) ?: "")
        llmApiKey = llmCard.addField("API Key", prefs.getString(KEY_LLM_API_KEY, BuildConfig.LLM_API_KEY) ?: "")
        llmModel = llmCard.addField("模型名称", prefs.getString(KEY_LLM_MODEL, BuildConfig.LLM_MODEL) ?: "")
        llmPrompt = llmCard.addField(
            "系统 Prompt",
            prefs.getString(KEY_LLM_PROMPT, "你是一个智能助手，请简洁回答用户的问题。回答要准确、有帮助。") ?: "",
            lines = 3
        )
        root.addView(llmCard)

        // --- ASR ---
        val asrCard = createCard()
        asrCard.addSectionTitle("语音识别 (ASR)")
        asrApiKey = asrCard.addField("API Key", prefs.getString(KEY_ASR_API_KEY, BuildConfig.ASR_API_KEY) ?: "")
        asrResourceId = asrCard.addField("Resource ID", prefs.getString(KEY_ASR_RESOURCE_ID, BuildConfig.ASR_RESOURCE_ID) ?: "")
        asrWsUrl = asrCard.addField("WebSocket URL", prefs.getString(KEY_ASR_WS_URL, BuildConfig.ASR_WS_URL) ?: "")
        root.addView(asrCard)

        // --- Language ---
        val langCard = createCard()
        langCard.addSectionTitle("语言设置")
        langCard.addFieldLabel("翻译目标语种")
        translationLang = Spinner(this).apply {
            adapter = styledSpinnerAdapter(listOf("英文", "日文", "韩文", "法文", "德文", "西班牙文", "俄文", "中文"))
            setSelection(
                (adapter as ArrayAdapter<String>).getPosition(
                    prefs.getString(KEY_TRANSLATION_LANG, "英文") ?: "英文"
                )
            )
            background = rounded(fieldBg, dp(8))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        langCard.addView(translationLang)

        langCard.addFieldLabel("语音识别语种")
        asrLanguage = Spinner(this).apply {
            adapter = styledSpinnerAdapter(listOf("中文", "英文", "日文", "粤语", "四川话"))
            background = rounded(fieldBg, dp(8))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        langCard.addView(asrLanguage)
        root.addView(langCard)

        // Save button
        root.addView(TextView(this).apply {
            text = "保存设置"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(accentColor, dp(12))
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
            setOnClickListener { saveSettings() }
        })

        scrollView.addView(root)
        setContentView(scrollView)

        customBallImagePath = prefs.getString(AppSettings.KEY_CUSTOM_BALL_PATH, null)
        refreshCustomBallPreview()
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putString(KEY_LLM_API_URL, llmApiUrl.text.toString().trim())
            putString(KEY_LLM_API_KEY, llmApiKey.text.toString().trim())
            putString(KEY_LLM_MODEL, llmModel.text.toString().trim())
            putString(KEY_LLM_PROMPT, llmPrompt.text.toString().trim())
            putString(KEY_ASR_API_KEY, asrApiKey.text.toString().trim())
            putString(KEY_ASR_RESOURCE_ID, asrResourceId.text.toString().trim())
            putString(KEY_ASR_WS_URL, asrWsUrl.text.toString().trim())
            putString(KEY_TRANSLATION_LANG, translationLang.selectedItem.toString())
            putString(KEY_ASR_LANGUAGE, asrLanguage.selectedItem.toString())
            putInt(KEY_BALL_SIZE, ballSizeMode.progress)
            putBoolean(KEY_OVERLAY_ALWAYS, overlayMode.isChecked)
            if (customBallImagePath.isNullOrBlank()) {
                remove(AppSettings.KEY_CUSTOM_BALL_PATH)
                java.io.File(filesDir, "custom_ball.jpg").delete()
            } else {
                putString(AppSettings.KEY_CUSTOM_BALL_PATH, customBallImagePath)
            }
            commit()
        }
        notifyBallSettingsChanged()
        showToast("设置已保存，悬浮球将立即更新")
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 350)
    }

    private fun applyBallSize(progress: Int) {
        prefs.edit().putInt(KEY_BALL_SIZE, progress).commit()
        ballSizeLabel.text = sizeLabel(progress)
        notifyBallSettingsChanged()
        showToast("悬浮球大小：${FloatingBallConfig.radiusDpForProgress(progress)}dp")
    }

    private fun notifyBallSettingsChanged(overlayOnly: Boolean = false) {
        sendBroadcast(
            Intent(AppSettings.ACTION_BALL_SETTINGS_CHANGED).apply {
                setPackage(packageName)
                putExtra(AppSettings.EXTRA_OVERLAY_ONLY, overlayOnly)
            }
        )
    }

    private fun pickImage() {
        pickImageLauncher.launch(arrayOf("image/*"))
    }

    private fun onImagePicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some providers don't support persistable grants
        }

        val path = copyImageToInternal(uri)
        if (path == null) {
            showToast("图片读取失败，请换一张试试")
            return
        }
        customBallImagePath = path
        prefs.edit().putString(AppSettings.KEY_CUSTOM_BALL_PATH, path).commit()
        refreshCustomBallPreview()
        notifyBallSettingsChanged()
        showToast("图片已应用")
    }

    private fun copyImageToInternal(uri: Uri): String? {
        return try {
            val dest = java.io.File(filesDir, "custom_ball.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                Log.e(TAG, "openInputStream returned null for $uri")
                return null
            }
            if (!dest.exists() || dest.length() == 0L) {
                Log.e(TAG, "dest file empty: ${dest.absolutePath}")
                return null
            }
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "copyImageToInternal failed", e)
            null
        }
    }

    private fun clearCustomBallImage() {
        customBallImagePath = null
        java.io.File(filesDir, "custom_ball.jpg").delete()
        prefs.edit().remove(AppSettings.KEY_CUSTOM_BALL_PATH).commit()
        refreshCustomBallPreview()
        notifyBallSettingsChanged()
        showToast("已清除自定义图片")
    }

    private fun refreshCustomBallPreview() {
        val path = customBallImagePath
        if (path != null && java.io.File(path).exists()) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = BitmapFactory.decodeFile(path, opts)
            customBallPreview.setImageBitmap(bitmap)
        } else {
            customBallPreview.setImageDrawable(null)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, dp(14))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
    }

    private fun LinearLayout.addSectionTitle(title: String) {
        addView(TextView(this@SettingsActivity).apply {
            text = title
            textSize = 16f
            setTextColor(textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(12))
        })
    }

    private fun LinearLayout.addFieldLabel(label: String) {
        addView(TextView(this@SettingsActivity).apply {
            text = label
            textSize = 13f
            setTextColor(textSecondary)
            setPadding(0, dp(8), 0, dp(4))
        })
    }

    private fun LinearLayout.addField(label: String, default: String, lines: Int = 1): EditText {
        addFieldLabel(label)
        val field = EditText(this@SettingsActivity).apply {
            setText(default)
            textSize = 14f
            setTextColor(textPrimary)
            setHintTextColor(textSecondary)
            background = rounded(fieldBg, dp(8))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            if (lines > 1) {
                minLines = lines
                maxLines = lines
                gravity = Gravity.TOP or Gravity.START
            }
        }
        addView(field)
        return field
    }

    private fun createSecondaryButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            background = rounded(fieldBg, dp(8))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun styledSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun sizeLabel(progress: Int): String {
        val radiusDp = FloatingBallConfig.radiusDpForProgress(progress)
        return "大小：${radiusDp}dp（${progress}%）"
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
