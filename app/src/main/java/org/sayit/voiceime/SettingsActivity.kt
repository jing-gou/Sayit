package org.sayit.voiceime

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    // LLM fields
    private lateinit var llmApiUrl: EditText
    private lateinit var llmApiKey: EditText
    private lateinit var llmModel: EditText
    private lateinit var llmPrompt: EditText

    // ASR fields
    private lateinit var asrApiKey: EditText
    private lateinit var asrResourceId: EditText
    private lateinit var asrWsUrl: EditText

    // Other settings
    private lateinit var translationLang: Spinner
    private lateinit var asrLanguage: Spinner
    private lateinit var ballSizeMode: SeekBar
    private lateinit var ballSizeLabel: TextView
    private lateinit var overlayMode: Switch

    // Custom ball image
    private lateinit var customBallPreview: ImageView
    private var customBallUri: String? = null

    companion object {
        const val PREFS_NAME = "sayit_settings"
        const val KEY_LLM_API_URL = "llm_api_url"
        const val KEY_LLM_API_KEY = "llm_api_key"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LLM_PROMPT = "llm_prompt"
        const val KEY_ASR_API_KEY = "asr_api_key"
        const val KEY_ASR_RESOURCE_ID = "asr_resource_id"
        const val KEY_ASR_WS_URL = "asr_ws_url"
        const val KEY_TRANSLATION_LANG = "translation_lang"
        const val KEY_ASR_LANGUAGE = "asr_language"
        const val KEY_BALL_SIZE = "ball_size"
        const val KEY_OVERLAY_ALWAYS = "overlay_always"
        const val KEY_CUSTOM_BALL_URI = "custom_ball_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val scrollView = ScrollView(this).apply {
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Title
        root.addView(TextView(this@SettingsActivity).apply {
            text = "Sayit 设置"
            textSize = 24f
            setPadding(0, 0, 0, dp(20))
        })

        // --- Floating Ball Section ---
        root.addSection("悬浮球")

        // Ball size
        ballSizeLabel = TextView(this).apply { text = "大小: 标准" }
        root.addView(ballSizeLabel)
        ballSizeMode = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt(KEY_BALL_SIZE, 50)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    ballSizeLabel.text = when {
                        progress < 33 -> "大小: 小"
                        progress < 66 -> "大小: 标准"
                        else -> "大小: 大"
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        root.addView(ballSizeMode)

        // Custom ball image
        root.addView(TextView(this@SettingsActivity).apply {
            text = "自定义悬浮球图片"
            setPadding(0, dp(12), 0, dp(4))
        })
        customBallPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
            setBackgroundColor(0xFF333333.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(customBallPreview)
        root.addView(Button(this@SettingsActivity).apply {
            text = "选择图片 (PNG/GIF)"
            setOnClickListener { pickImage() }
        })

        // Overlay mode
        overlayMode = Switch(this).apply {
            text = "悬浮球常驻显示（关闭则仅输入时显示）"
            isChecked = prefs.getBoolean(KEY_OVERLAY_ALWAYS, true)
        }
        root.addView(overlayMode)

        // --- LLM Section ---
        root.addSection("大模型 (LLM)")
        llmApiUrl = root.addSettingField("API 地址", prefs.getString(KEY_LLM_API_URL, BuildConfig.LLM_API_URL) ?: "")
        llmApiKey = root.addSettingField("API Key", prefs.getString(KEY_LLM_API_KEY, BuildConfig.LLM_API_KEY) ?: "")
        llmModel = root.addSettingField("模型名称", prefs.getString(KEY_LLM_MODEL, BuildConfig.LLM_MODEL) ?: "")
        llmPrompt = root.addSettingField("系统 Prompt", prefs.getString(KEY_LLM_PROMPT, "你是一个智能助手，请简洁回答用户的问题。回答要准确、有帮助。") ?: "", lines = 3)

        // --- ASR Section ---
        root.addSection("语音识别 (ASR)")
        asrApiKey = root.addSettingField("API Key", prefs.getString(KEY_ASR_API_KEY, BuildConfig.ASR_API_KEY) ?: "")
        asrResourceId = root.addSettingField("Resource ID", prefs.getString(KEY_ASR_RESOURCE_ID, BuildConfig.ASR_RESOURCE_ID) ?: "")
        asrWsUrl = root.addSettingField("WebSocket URL", prefs.getString(KEY_ASR_WS_URL, BuildConfig.ASR_WS_URL) ?: "")

        // --- Language Section ---
        root.addSection("语言设置")

        root.addView(TextView(this@SettingsActivity).apply { text = "翻译目标语种" })
        translationLang = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("英文", "日文", "韩文", "法文", "德文", "西班牙文", "俄文", "中文"))
            setSelection(
                (adapter as ArrayAdapter<String>).getPosition(
                    prefs.getString(KEY_TRANSLATION_LANG, "英文") ?: "英文"
                )
            )
        }
        root.addView(translationLang)

        root.addView(TextView(this@SettingsActivity).apply {
            text = "语音识别语种"
            setPadding(0, dp(12), 0, 0)
        })
        asrLanguage = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("中文", "英文", "日文", "粤语", "四川话"))
        }
        root.addView(asrLanguage)

        // --- Save Button ---
        root.addView(Button(this@SettingsActivity).apply {
            text = "保存设置"
            setPadding(0, dp(24), 0, 0)
            setOnClickListener { saveSettings() }
        })

        scrollView.addView(root)
        setContentView(scrollView)
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
            customBallUri?.let { putString(KEY_CUSTOM_BALL_URI, it) }
            apply()
        }
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            customBallUri = uri.toString()
            customBallPreview.setImageURI(uri)
        }
    }

    // Helper extensions
    private fun LinearLayout.addSection(title: String) {
        addView(TextView(this@SettingsActivity).apply {
            text = title
            textSize = 18f
            setPadding(0, dp(24), 0, dp(8))
        })
        addView(View(this@SettingsActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { bottomMargin = dp(8) }
            setBackgroundColor(0xFF444444.toInt())
        })
    }

    private fun LinearLayout.addSettingField(label: String, default: String, lines: Int = 1): EditText {
        addView(TextView(this@SettingsActivity).apply {
            text = label
            setPadding(0, dp(8), 0, dp(4))
        })
        val field = EditText(this@SettingsActivity).apply {
            setText(default)
            textSize = 14f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0xFF2A2A2A.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            if (lines > 1) {
                minLines = lines
                maxLines = lines
                gravity = Gravity.TOP or Gravity.START
            }
        }
        addView(field)
        return field
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
