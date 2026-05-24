package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewTreeObserver
import android.widget.*

class SymbolPanelView(context: Context) : FrameLayout(context) {

    var onSymbolSelected: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    var onPanelHeightChanged: ((Int) -> Unit)? = null

    private val tabNames = listOf("字母", "数字", "符号", "标点", "Emoji")
    private var currentTab = 0
    private var isUpperCase = false
    private val tabButtons = mutableListOf<TextView>()
    private lateinit var contentLayout: LinearLayout
    private lateinit var mainLayout: LinearLayout
    private var shiftButton: TextView? = null

    private val accentColor = Color.parseColor("#6366F1")
    private val surfaceColor = Color.parseColor("#1E1E2E")
    private val keyColor = Color.parseColor("#2A2A3C")

    private val numberKeys = listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "␣"
    )

    private val punctuationKeys = listOf(
        "。", "，", "！", "？", "；", "：",
        "\u201C", "\u201D", "\u2018", "\u2019", "·", "——",
        "……", "、", "（", "）", "【", "】",
        "《", "》", "「", "」", "『", "』"
    )

    private val symbolKeys = listOf(
        "@", "#", "$", "%", "&", "*",
        "+", "-", "×", "÷", "=", "≠",
        "<", ">", "≤", "≥", "~", "^",
        "_", "|", "\\", "/", "€", "¥"
    )

    private val emojiKeys = listOf(
        "😀", "😂", "🤣", "😍", "🥰", "😎",
        "🤔", "😢", "😡", "🥺", "😴", "🤯",
        "👍", "👎", "❤️", "🔥", "⭐", "✅",
        "❌", "🎉", "🙏", "💪", "👏", "🤝"
    )

    private val letterRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )

    init {
        background = roundedBg(surfaceColor, dp(16), dp(16), 0, 0)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        elevation = dp(8).toFloat()

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        this.mainLayout = mainLayout

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        header.addView(TextView(context).apply {
            text = "符号输入"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(createIconButton("✕", keyColor) { onDismiss?.invoke() })
        mainLayout.addView(header)

        val tabScroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            isHorizontalScrollBarEnabled = false
        }
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabNames.forEachIndexed { index, name ->
            val tab = TextView(context).apply {
                text = name
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                setOnClickListener { switchTab(index) }
            }
            styleTab(tab, index == 0)
            tabButtons.add(tab)
            tabRow.addView(tab)
        }
        tabScroll.addView(tabRow)
        mainLayout.addView(tabScroll)

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        mainLayout.addView(contentLayout)
        mainLayout.addView(createBottomBar())

        addView(mainLayout)
        showTabContent(0)
        visibility = View.GONE
    }

    private fun createBottomBar(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            shiftButton = TextView(context).apply {
                text = "⇧"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBg(keyColor, dp(10))
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
                ).apply { marginEnd = dp(8) }
                visibility = View.GONE
                setOnClickListener {
                    isUpperCase = !isUpperCase
                    showTabContent(0)
                }
            }
            addView(shiftButton)

            addView(TextView(context).apply {
                text = "⌫  退格"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBg(keyColor, dp(10))
                setPadding(dp(20), dp(10), dp(20), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
                )
                setOnClickListener { onBackspace?.invoke() }
            })
        }
    }

    private fun updateBottomBarForTab() {
        val btn = shiftButton ?: return
        btn.visibility = if (currentTab == 0) View.VISIBLE else View.GONE
        btn.background = roundedBg(if (isUpperCase) accentColor else keyColor, dp(10))
        btn.text = if (isUpperCase) "⇪" else "⇧"
    }

    private fun switchTab(index: Int) {
        currentTab = index
        tabButtons.forEachIndexed { i, btn -> styleTab(btn, i == index) }
        showTabContent(index)
    }

    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.background = roundedBg(if (selected) accentColor else keyColor, dp(20))
        tab.setTextColor(if (selected) Color.WHITE else Color.parseColor("#AAAAAA"))
        tab.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD
        else android.graphics.Typeface.DEFAULT
    }

    private fun showTabContent(index: Int) {
        contentLayout.removeAllViews()
        when (index) {
            0 -> showLetterKeyboard()
            1 -> showNumberPad()
            2 -> showSymbolGrid(symbolKeys)
            3 -> showSymbolGrid(punctuationKeys)
            4 -> showSymbolGrid(emojiKeys)
        }
        updateBottomBarForTab()
        scheduleHeightUpdate()
    }

    private fun showNumberPad() {
        val grid = GridLayout(context).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        numberKeys.forEach { key -> grid.addView(createKeyButton(key, isWide = true)) }
        contentLayout.addView(grid)
    }

    private fun showSymbolGrid(keys: List<String>) {
        val grid = GridLayout(context).apply {
            columnCount = 6
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        keys.forEach { key -> grid.addView(createKeyButton(key, isWide = false)) }
        contentLayout.addView(grid)
    }

    private fun showLetterKeyboard() {
        val kbLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        letterRows.forEach { row ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3) }
            }
            row.forEach { letter ->
                val output = if (isUpperCase) letter else letter.lowercase()
                rowLayout.addView(createKeyButton(output, isWide = false, letterKey = true))
            }
            kbLayout.addView(rowLayout)
        }
        contentLayout.addView(kbLayout)
    }

    private fun createKeyButton(key: String, isWide: Boolean, letterKey: Boolean = false): TextView {
        val label = if (key == "␣") "␣" else key
        val keyW = when {
            isWide -> 0
            letterKey -> dp(34)
            else -> dp(44)
        }
        val keyH = if (letterKey) dp(42) else dp(44)
        return TextView(context).apply {
            text = label
            textSize = when {
                letterKey -> 15f
                key.length > 1 && key[0].code > 0xFFFF -> 20f
                else -> 18f
            }
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBg(keyColor, dp(8))
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = GridLayout.LayoutParams().apply {
                width = keyW
                height = keyH
                if (isWide) columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener {
                val output = if (key == "␣") " " else key
                onSymbolSelected?.invoke(output)
            }
        }
    }

    private fun createIconButton(label: String, bgColor: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBg(bgColor, dp(8))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun roundedBg(color: Int, radius: Int, tl: Int = radius, tr: Int = radius,
                          bl: Int = radius, br: Int = radius): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(
                tl.toFloat(), tl.toFloat(),
                tr.toFloat(), tr.toFloat(),
                br.toFloat(), br.toFloat(),
                bl.toFloat(), bl.toFloat()
            )
        }
    }

    private fun notifyHeightChanged() {
        if (visibility != View.VISIBLE) {
            onPanelHeightChanged?.invoke(0)
            return
        }
        val width = resources.displayMetrics.widthPixels
        val wSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        mainLayout.measure(wSpec, hSpec)
        val measured = mainLayout.measuredHeight + paddingTop + paddingBottom
        if (measured <= 0) return
        val maxInset = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        onPanelHeightChanged?.invoke(measured.coerceAtMost(maxInset))
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
        visibility = View.VISIBLE
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
            visibility = View.GONE
            onDismiss?.invoke()
        }.start()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
