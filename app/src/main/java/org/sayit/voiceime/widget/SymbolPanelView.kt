package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewTreeObserver
import android.widget.*

class SymbolPanelView(context: Context) : FrameLayout(context) {

    var onSymbolSelected: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    /** Overlay mode (e.g. clipboard) — pushes IME inset by reported height. */
    var onPanelHeightChanged: ((Int) -> Unit)? = null
    /** Embedded in IME input view — parent relayout picks up height. */
    var onLayoutChanged: (() -> Unit)? = null

    private val tabNames = listOf("字母", "数字", "符号", "标点", "Emoji")
    private var currentTab = 0
    private var isUpperCase = false
    private val tabButtons = mutableListOf<TextView>()
    private lateinit var contentLayout: LinearLayout
    private lateinit var contentScroll: ScrollView
    private lateinit var mainLayout: LinearLayout
    /** Letter tab: 3 rows, no scroll. */
    private val letterContentHeightPx: Int
    /** Number tab: 3×4 grid, no scroll. */
    private val numberPadContentHeightPx: Int
    /** Symbol / punctuation / emoji: scroll when taller than this. */
    private val scrollableMaxContentHeightPx: Int
    /** Max total panel height (overlay mode). */
    private val maxPanelHeightPx: Int

    private companion object {
        private const val TAB_LETTER = 0
        private const val TAB_NUMBER = 1
        private const val NUMBER_COLUMNS = 3
        private const val NUMBER_ROWS = 4
    }
    private var shiftButton: TextView? = null
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeatRunnable: Runnable? = null

    private val accentColor = Color.parseColor("#6366F1")
    private val surfaceColor = Color.parseColor("#1E1E2E")
    private val keyColor = Color.parseColor("#2A2A3C")

    private val numberKeys = listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "␣"
    )

    private val punctuationKeys = listOf(
        // 中文标点
        "。", "，", "！", "？", "；", "：",
        "、", "……", "—", "～", "·", "『",
        "』", "「", "」", "（", "）", "【",
        "】", "《", "》", "\u201C", "\u201D",
        "\u2018", "\u2019", "〈", "〉", "﹏", "﹑",
        // 英文 / 半角标点
        ".", ",", "!", "?", ";", ":",
        "'", "\"", "(", ")", "[", "]",
        "{", "}", "-", "…", "/", "\\"
    )

    private val symbolKeys = listOf(
        "@", "#", "$", "%", "&", "*",
        "+", "-", "×", "÷", "=", "≠",
        "<", ">", "≤", "≥", "~", "^",
        "_", "|", "\\", "/", "€", "¥",
        "£", "¢", "°", "±", "∞", "√",
        "π", "≈", "≡", "•", "※", "§",
        "©", "®", "™", "№", "‰", "★",
        "☆", "○", "●", "◆", "◇", "□",
        "■", "△", "▽", "←", "→", "↑",
        "↓", "↔", "☑", "☒", "♂", "♀",
        "♠", "♥", "♦", "♣", "♪", "♫",
        "✓", "✗", "✦", "✧", "❖", "◉"
    )

    private val emojiKeys = listOf(
        // 表情
        "😀", "😃", "😄", "😁", "😆", "😅",
        "😂", "🤣", "🥲", "😊", "😇", "🙂",
        "😉", "😍", "🥰", "😘", "😎", "🤔",
        "😐", "😑", "😶", "🙄", "😏", "😣",
        "😢", "😭", "😤", "😡", "🥺", "😴",
        "🤯", "🤗", "🤭", "🫡", "🫠", "🤫",
        // 手势 / 人物
        "👍", "👎", "👌", "✌️", "🤞", "🤝",
        "🙏", "👏", "💪", "🫶", "👋", "🤷",
        // 心形 / 符号
        "❤️", "🧡", "💛", "💚", "💙", "💜",
        "🖤", "🤍", "💔", "❣️", "💕", "✨",
        "💯", "🔥", "⭐", "🌟", "💫", "⚡",
        "✅", "❌", "❗", "❓", "‼️", "💢",
        // 庆祝 / 物品
        "🎉", "🎊", "🎁", "🏆", "📱", "💻",
        "📧", "📝", "📌", "🔗", "☕", "🍎",
        "🌈", "☀️", "🌙", "☁️", "🌸", "🐶"
    )

    private val letterRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )

    init {
        val screenH = resources.displayMetrics.heightPixels
        val keyVMargin = dp(6) // GridLayout top+bottom margin per key (3+3)
        val numberKeyH = dp(44)
        val letterKeyH = dp(42)
        numberPadContentHeightPx = NUMBER_ROWS * (numberKeyH + keyVMargin)
        letterContentHeightPx = 3 * (letterKeyH + keyVMargin) + 2 * dp(3)
        scrollableMaxContentHeightPx = minOf(dp(168), (screenH * 0.22f).toInt())
        val chromeEstimatePx = dp(10 + 10) + dp(40) + dp(36) + dp(8 + 44) // padding, header, tabs, bottom bar
        maxPanelHeightPx = minOf(
            (screenH * 0.42f).toInt(),
            chromeEstimatePx + maxOf(numberPadContentHeightPx, letterContentHeightPx, scrollableMaxContentHeightPx)
        )
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
        contentScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = false
            addView(contentLayout)
        }
        mainLayout.addView(contentScroll)
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
                text = "⌫"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBg(keyColor, dp(10))
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
                )
                attachRepeatBackspace(this)
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
        updateContentScrollHeight()
    }

    private fun contentHeightForTab(tab: Int, measuredContentH: Int): Int {
        return when (tab) {
            TAB_LETTER -> maxOf(measuredContentH, letterContentHeightPx)
            TAB_NUMBER -> maxOf(measuredContentH, numberPadContentHeightPx)
            else -> measuredContentH.coerceAtMost(scrollableMaxContentHeightPx)
        }
    }

    private fun updateContentScrollHeight() {
        contentLayout.post {
            val width = contentLayout.width.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - paddingLeft - paddingRight)
            val wSpec = MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), MeasureSpec.EXACTLY)
            val hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            contentLayout.measure(wSpec, hSpec)
            val contentH = contentLayout.measuredHeight.coerceAtLeast(0)
            val targetH = contentHeightForTab(currentTab, contentH)

            val params = contentScroll.layoutParams as LinearLayout.LayoutParams
            params.height = if (targetH > 0) targetH else LinearLayout.LayoutParams.WRAP_CONTENT
            contentScroll.layoutParams = params
            contentScroll.isVerticalScrollBarEnabled = currentTab != TAB_NUMBER && currentTab != TAB_LETTER
            if (currentTab == TAB_NUMBER || currentTab == TAB_LETTER) {
                contentScroll.scrollTo(0, 0)
            }
            contentScroll.requestLayout()
            mainLayout.post { requestLayoutChanged() }
        }
    }

    private fun showNumberPad() {
        val grid = GridLayout(context).apply {
            columnCount = NUMBER_COLUMNS
            rowCount = NUMBER_ROWS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        numberKeys.forEach { key -> grid.addView(createKeyButton(key, isWide = true, numberKey = true)) }
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
        keys.forEach { key -> grid.addView(createKeyButton(key, fillColumn = true)) }
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

    private fun createKeyButton(
        key: String,
        isWide: Boolean = false,
        letterKey: Boolean = false,
        numberKey: Boolean = false,
        fillColumn: Boolean = false
    ): TextView {
        val label = if (key == "␣") "␣" else key
        val expandColumn = isWide || numberKey || fillColumn
        val keyW = when {
            expandColumn -> 0
            letterKey -> dp(34)
            else -> dp(44)
        }
        val keyH = when {
            letterKey -> dp(42)
            numberKey -> dp(44)
            else -> dp(44)
        }
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
                if (expandColumn) {
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                }
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener {
                val output = if (key == "␣") " " else key
                onSymbolSelected?.invoke(output)
            }
        }
    }

    private fun attachRepeatBackspace(button: TextView) {
        val repeatIntervalMs = 50L
        val longPressDelayMs = 400L
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopBackspaceRepeat()
                    onBackspace?.invoke()
                    backspaceRepeatRunnable = object : Runnable {
                        override fun run() {
                            onBackspace?.invoke()
                            backspaceHandler.postDelayed(this, repeatIntervalMs)
                        }
                    }
                    backspaceHandler.postDelayed(backspaceRepeatRunnable!!, longPressDelayMs)
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopBackspaceRepeat()
                    v.isPressed = false
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun stopBackspaceRepeat() {
        backspaceRepeatRunnable?.let { backspaceHandler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
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

    private fun requestLayoutChanged() {
        if (visibility != View.VISIBLE) {
            onPanelHeightChanged?.invoke(0)
            onLayoutChanged?.invoke()
            return
        }
        if (onPanelHeightChanged != null) {
            val width = resources.displayMetrics.widthPixels
            val wSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            val hSpec = MeasureSpec.makeMeasureSpec(maxPanelHeightPx, MeasureSpec.AT_MOST)
            mainLayout.measure(wSpec, hSpec)
            val measured = mainLayout.measuredHeight + paddingTop + paddingBottom
            if (measured > 0) {
                onPanelHeightChanged?.invoke(measured.coerceAtMost(maxPanelHeightPx))
            }
        }
        requestLayout()
        onLayoutChanged?.invoke()
    }

    private fun scheduleHeightUpdate() {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                updateContentScrollHeight()
            }
        })
    }

    fun show() {
        visibility = View.VISIBLE
        alpha = 0f
        translationY = dp(40).toFloat()
        scheduleHeightUpdate()
        animate().alpha(1f).translationY(0f).setDuration(200).withEndAction {
            requestLayoutChanged()
        }.start()
    }

    fun dismiss() {
        stopBackspaceRepeat()
        onPanelHeightChanged?.invoke(0)
        onLayoutChanged?.invoke()
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
