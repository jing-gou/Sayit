package org.sayit.voiceime.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ResultBubbleView(context: Context) : FrameLayout(context) {

    private val accentColor = Color.parseColor("#6366F1")
    private val surfaceColor = Color.parseColor("#1E1E2E")
    private val cardBg = Color.parseColor("#2A2A3C")
    private val textPrimary = Color.parseColor("#F0F0F5")
    private val textSecondary = Color.parseColor("#9090A0")

    private val headerTitle: TextView
    private val statusLabel: TextView
    private val resultText: TextView
    private val scrollView: ScrollView
    private val buttonBar: LinearLayout
    private val insertButton: TextView
    private val copyButton: TextView
    private val dismissButton: TextView
    private val loadingDots: TextView

    var onInsert: ((String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    init {
        background = roundedBg(surfaceColor, 16f)
        elevation = dp(12f).toFloat()
        setPadding(dp(14f), dp(12f), dp(14f), dp(12f))

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8f))
        }
        headerTitle = TextView(context).apply {
            text = "AI 回复"
            textSize = 14f
            setTextColor(textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusLabel = TextView(context).apply {
            text = "思考中"
            textSize = 11f
            setTextColor(accentColor)
            setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
            background = roundedBg(Color.argb(40, 99, 102, 241), 8f)
        }
        header.addView(headerTitle)
        header.addView(statusLabel)
        mainLayout.addView(header)

        loadingDots = TextView(context).apply {
            text = "●  ●  ●"
            textSize = 18f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(16f), 0, dp(16f))
            visibility = View.GONE
        }

        resultText = TextView(context).apply {
            setTextColor(textPrimary)
            textSize = 15f
            setLineSpacing(dp(4f).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(160f)
            )
            isVerticalScrollBarEnabled = false
            addView(resultText)
            visibility = View.GONE
        }

        buttonBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10f), 0, 0)
        }

        insertButton = createActionButton("插入", accentColor)
        copyButton = createActionButton("复制", cardBg)
        dismissButton = createActionButton("关闭", Color.parseColor("#3D3D56"))

        buttonBar.addView(insertButton)
        buttonBar.addView(copyButton)
        buttonBar.addView(dismissButton)

        mainLayout.addView(loadingDots)
        mainLayout.addView(scrollView)
        mainLayout.addView(buttonBar)
        addView(mainLayout)
        visibility = View.GONE
    }

    private fun createActionButton(label: String, bg: Int): TextView {
        return TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(bg, 8f)
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6f) }
        }
    }

    fun showLoading(ballX: Int, ballY: Int, ballWidth: Int, ballHeight: Int, screenWidth: Int) {
        resultText.text = ""
        statusLabel.text = "思考中"
        statusLabel.visibility = View.VISIBLE
        loadingDots.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        insertButton.isEnabled = false
        copyButton.isEnabled = false
        insertButton.alpha = 0.4f
        copyButton.alpha = 0.4f

        positionAtScreen(ballX, ballY, ballWidth, ballHeight, screenWidth)
        visibility = View.VISIBLE
        alpha = 0f
        translationY = dp(12f).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(220).start()
        dismissButton.setOnClickListener { dismiss() }
    }

    fun appendText(delta: String) {
        if (loadingDots.visibility == View.VISIBLE) {
            loadingDots.visibility = View.GONE
            scrollView.visibility = View.VISIBLE
            statusLabel.text = "生成中"
            insertButton.isEnabled = true
            copyButton.isEnabled = true
            insertButton.alpha = 1f
            copyButton.alpha = 1f
            wireUpButtons()
        }
        resultText.append(delta)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    fun finalizeResult() {
        statusLabel.text = "完成"
        statusLabel.setTextColor(Color.parseColor("#4ADE80"))
        statusLabel.background = roundedBg(Color.argb(40, 74, 222, 128), 8f)
        loadingDots.visibility = View.GONE
        wireUpButtons()
    }

    private fun wireUpButtons() {
        insertButton.setOnClickListener {
            onInsert?.invoke(resultText.text.toString())
            dismiss()
        }
        copyButton.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("sayit_result", resultText.text.toString())
            clipboard?.setPrimaryClip(clip)
            dismiss()
        }
        dismissButton.setOnClickListener { dismiss() }
    }

    fun show(result: String, ballX: Int, ballY: Int, ballWidth: Int, ballHeight: Int, screenWidth: Int) {
        resultText.text = result
        loadingDots.visibility = View.GONE
        scrollView.visibility = View.VISIBLE
        statusLabel.text = "完成"
        insertButton.isEnabled = true
        copyButton.isEnabled = true
        insertButton.alpha = 1f
        copyButton.alpha = 1f

        positionAtScreen(ballX, ballY, ballWidth, ballHeight, screenWidth)
        visibility = View.VISIBLE
        alpha = 0f
        scaleX = 0.92f
        scaleY = 0.92f
        animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
        wireUpButtons()
    }

    private fun positionAtScreen(ballX: Int, ballY: Int, ballWidth: Int, ballHeight: Int, screenWidth: Int) {
        val params = layoutParams as? WindowManager.LayoutParams ?: return
        val bubbleWidth = dp(300f)
        params.x = (ballX + ballWidth / 2 - bubbleWidth / 2)
            .coerceIn(0, (screenWidth - bubbleWidth).coerceAtLeast(0))
        params.y = (ballY + ballHeight + dp(8f)).coerceAtLeast(dp(8f))
    }

    fun dismiss() {
        animate()
            .alpha(0f)
            .translationY(dp(8f).toFloat())
            .setDuration(150)
            .withEndAction {
                visibility = View.GONE
                onDismiss?.invoke()
            }
            .start()
    }

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value,
            context.resources.displayMetrics
        ).toInt()
    }
}
