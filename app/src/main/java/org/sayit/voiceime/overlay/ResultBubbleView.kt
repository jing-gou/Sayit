package org.sayit.voiceime.overlay

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.animation.OvershootInterpolator

class ResultBubbleView(context: Context) : FrameLayout(context) {

    private val resultText: TextView
    private val scrollView: ScrollView
    private val buttonBar: LinearLayout
    private val insertButton: Button
    private val copyButton: Button
    private val dismissButton: Button

    var onInsert: ((String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.argb(240, 33, 33, 33))
        setPadding(dp(16f), dp(12f), dp(16f), dp(12f))

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        resultText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 6
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(120f)
            )
            addView(resultText)
        }

        buttonBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8f), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        insertButton = createButton("插入", Color.parseColor("#4CAF50"))
        copyButton = createButton("复制", Color.parseColor("#2196F3"))
        dismissButton = createButton("关闭", Color.parseColor("#F44336"))

        buttonBar.addView(insertButton)
        buttonBar.addView(copyButton)
        buttonBar.addView(dismissButton)

        mainLayout.addView(scrollView)
        mainLayout.addView(buttonBar)

        addView(mainLayout)

        visibility = View.GONE
    }

    private fun createButton(text: String, bgColor: Int): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(bgColor)
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8f)
            }
        }
    }

    fun show(result: String, anchorView: View) {
        resultText.text = result

        val params = layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.topMargin = anchorView.bottom + dp(16f)
        layoutParams = params

        visibility = View.VISIBLE
        alpha = 0f
        scaleX = 0.8f
        scaleY = 0.8f
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator())
            .start()

        insertButton.setOnClickListener {
            onInsert?.invoke(result)
            dismiss()
        }

        copyButton.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("sayit_result", result)
            clipboard?.setPrimaryClip(clip)
            dismiss()
        }

        dismissButton.setOnClickListener {
            dismiss()
        }
    }

    fun dismiss() {
        animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(150)
            .withEndAction {
                visibility = View.GONE
                onDismiss?.invoke()
            }
            .start()
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value,
            context.resources.displayMetrics
        ).toInt()
    }
}