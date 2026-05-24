package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.sayit.voiceime.clipboard.ClipboardEntry
import org.sayit.voiceime.clipboard.ClipboardHistoryStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ClipboardHistoryView(context: Context) : FrameLayout(context) {

    var onPaste: ((String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    var onPanelHeightChanged: ((Int) -> Unit)? = null

    private val accentColor = Color.parseColor("#6366F1")
    private val surfaceColor = Color.parseColor("#1E1E2E")
    private val keyColor = Color.parseColor("#2A2A3C")
    private val mutedColor = Color.parseColor("#94A3B8")

    private lateinit var mainLayout: LinearLayout
    private lateinit var listLayout: LinearLayout
    private val maxPanelHeightPx: Int

    init {
        val screenH = resources.displayMetrics.heightPixels
        maxPanelHeightPx = (screenH * 0.45f).toInt()
        background = roundedBg(surfaceColor, dp(16), dp(16), 0, 0)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        elevation = dp(8).toFloat()

        mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = "剪贴板历史"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = "保留最近 48 小时 · 点击条目粘贴"
                textSize = 11f
                setTextColor(mutedColor)
                setPadding(0, dp(2), 0, 0)
            })
        })
        header.addView(createIconButton("✕") { onDismiss?.invoke() })
        mainLayout.addView(header)

        listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = false
            addView(listLayout)
        }
        mainLayout.addView(scroll)
        addView(mainLayout)
        visibility = GONE
    }

    fun setEntries(entries: List<ClipboardEntry>) {
        listLayout.removeAllViews()
        if (entries.isEmpty()) {
            listLayout.addView(TextView(context).apply {
                text = "暂无记录\n复制文字后会自动出现在这里"
                textSize = 13f
                setTextColor(mutedColor)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(24), dp(8), dp(24))
            })
        } else {
            entries.forEach { entry ->
                listLayout.addView(createEntryRow(entry))
            }
        }
        scheduleHeightUpdate()
    }

    private fun createEntryRow(entry: ClipboardEntry): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(keyColor, dp(10))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onPaste?.invoke(entry.text) }

            addView(TextView(context).apply {
                text = entry.text.replace('\n', ' ')
                textSize = 14f
                setTextColor(Color.WHITE)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = formatTime(entry.timestamp)
                textSize = 11f
                setTextColor(mutedColor)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun createIconButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBg(keyColor, dp(8))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun notifyHeightChanged() {
        if (visibility != VISIBLE) {
            onPanelHeightChanged?.invoke(0)
            return
        }
        val width = resources.displayMetrics.widthPixels
        val wSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        mainLayout.measure(wSpec, hSpec)
        val measured = (mainLayout.measuredHeight + paddingTop + paddingBottom)
            .coerceAtMost(maxPanelHeightPx)
        if (measured > 0) {
            onPanelHeightChanged?.invoke(measured)
        }
    }

    private fun scheduleHeightUpdate() {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                notifyHeightChanged()
            }
        })
    }

    fun show() {
        visibility = VISIBLE
        alpha = 0f
        translationY = dp(40).toFloat()
        scheduleHeightUpdate()
        animate().alpha(1f).translationY(0f).setDuration(200).withEndAction {
            notifyHeightChanged()
        }.start()
    }

    fun dismiss() {
        onPanelHeightChanged?.invoke(0)
        animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(150).withEndAction {
            visibility = GONE
            onDismiss?.invoke()
        }.start()
    }

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "刚刚"
            diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)} 分钟前"
            diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)} 小时前"
            diff < TimeUnit.DAYS.toMillis(2) -> "昨天 ${timeFormat.format(Date(timestamp))}"
            diff < ClipboardHistoryStore.RETENTION_MS -> dateFormat.format(Date(timestamp))
            else -> "已过期"
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

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
    }
}
