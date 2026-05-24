package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class GestureGuideView(
    context: Context,
    defaultDontShowAgain: Boolean = true
) : FrameLayout(context) {

    var onDismiss: (() -> Unit)? = null
    var onComplete: ((dontShowAgain: Boolean) -> Unit)? = null

    private val accentColor = Color.parseColor("#6366F1")
    private val surfaceColor = Color.parseColor("#1E1E2E")
    private val cardColor = Color.parseColor("#252536")
    private val keyColor = Color.parseColor("#2A2A3C")
    private val mutedColor = Color.parseColor("#94A3B8")

    private lateinit var dontShowAgainCheck: CheckBox

    init {
        setBackgroundColor(Color.argb(200, 0, 0, 0))
        isClickable = true
        setOnClickListener { }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(cardColor, dp(16))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setOnClickListener { }
        }

        card.addView(TextView(context).apply {
            text = "悬浮球手势指南"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })
        card.addView(TextView(context).apply {
            text = "熟悉以下操作，语音输入更高效"
            textSize = 12f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })

        val maxScrollH = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                maxScrollH.coerceAtLeast(dp(120))
            )
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        list.addView(sectionTitle("基础操作"))
        list.addView(gestureItem("👆", "单击", "打开快捷轮盘：符号键盘、剪贴板、设置等"))
        list.addView(gestureItem("🎤", "长按", "开始语音识别，松手结束并提交文字"))
        list.addView(gestureItem("✥", "拖动", "按住悬浮球拖动，可移动到顺手的位置"))

        list.addView(sectionTitle("录音时滑动手势"))
        list.addView(gestureItem("↑", "上滑", "撤销本次语音输入，不写入文本框", Color.parseColor("#F43F5E")))
        list.addView(gestureItem("←", "左滑", "翻译模式：松手后翻译识别内容", Color.parseColor("#06B6D4")))
        list.addView(gestureItem("↓", "下滑", "问答模式：松手后向 AI 提问", Color.parseColor("#A855F7")))
        list.addView(gestureItem("→", "右滑", "松手后提交语音并发送（微信等聊天框）；多行输入框则换行", Color.parseColor("#10B981")))

        list.addView(sectionTitle("文字编辑"))
        list.addView(gestureItem("←", "左滑", "未录音时向左滑：逐字删除光标前文字"))
        list.addView(gestureItem("→", "右滑", "删除过程中向右滑：撤回已删文字（可恢复顺序）", Color.parseColor("#22C55E")))

        scroll.addView(list)
        card.addView(scroll)

        dontShowAgainCheck = CheckBox(context).apply {
            text = "不再自动显示此引导"
            textSize = 13f
            setTextColor(mutedColor)
            isChecked = defaultDontShowAgain
            setPadding(dp(4), dp(10), 0, dp(4))
            buttonTintList = android.content.res.ColorStateList.valueOf(accentColor)
        }
        card.addView(dontShowAgainCheck)

        card.addView(TextView(context).apply {
            text = "开始体验"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(accentColor, dp(12))
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            setOnClickListener {
                onComplete?.invoke(dontShowAgainCheck.isChecked)
            }
        })

        card.addView(TextView(context).apply {
            text = "稍后再看"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(mutedColor)
            setPadding(0, dp(12), 0, 0)
            setOnClickListener { onDismiss?.invoke() }
        })

        root.addView(card)
        addView(root)
        alpha = 0f
    }

    fun show() {
        visibility = VISIBLE
        animate().alpha(1f).setDuration(220).start()
    }

    fun dismiss() {
        animate().alpha(0f).setDuration(160).withEndAction {
            visibility = GONE
            onDismiss?.invoke()
        }.start()
    }

    private fun sectionTitle(title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 13f
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(6))
        }
    }

    private fun gestureItem(
        icon: String,
        title: String,
        description: String,
        badgeColor: Int = accentColor
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = roundedBg(keyColor, dp(10))
            setPadding(dp(10), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }

            addView(TextView(context).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBg(badgeColor, dp(8))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(12) }
                minWidth = dp(44)
                minHeight = dp(44)
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = title
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(context).apply {
                    text = description
                    textSize = 12f
                    setTextColor(mutedColor)
                    setPadding(0, dp(4), 0, 0)
                    setLineSpacing(dp(2).toFloat(), 1f)
                })
            })
        }
    }

    private fun roundedBg(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
