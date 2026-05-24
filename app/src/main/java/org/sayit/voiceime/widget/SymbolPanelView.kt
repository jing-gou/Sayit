package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SymbolPanelView(context: Context) : FrameLayout(context) {

    var onSymbolSelected: ((String) -> Unit)? = null

    private val categories = mapOf(
        "数字" to listOf("0","1","2","3","4","5","6","7","8","9","+","-","×","÷","=","%"),
        "标点" to listOf("。","，","！","？","；","：","""","""","'","'","（","）","【","】","—","…"),
        "符号" to listOf("@","#","$","&","*","~","^","_","|","\\","/","<",">","€","£","¥"),
        "Emoji" to listOf("😀","😂","🤣","😍","🥰","😎","🤔","😢","😡","👍","👎","❤️","🔥","⭐","✅","❌")
    )

    init {
        setBackgroundColor(Color.argb(245, 30, 30, 30))
        setPadding(dp(8), dp(8), dp(8), dp(8))

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        // Category tabs
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val contentScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(200)
            )
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "content"
        }
        contentScroll.addView(contentLayout)

        // Build category tabs
        categories.keys.forEachIndexed { index, name ->
            val tab = Button(context).apply {
                text = name
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundColor(if (index == 0) 0xFF4CAF50.toInt() else 0xFF444444.toInt())
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(4) }
                setOnClickListener {
                    // Update tab colors
                    for (i in 0 until tabRow.childCount) {
                        (tabRow.getChildAt(i) as? Button)?.setBackgroundColor(0xFF444444.toInt())
                    }
                    setBackgroundColor(0xFF4CAF50.toInt())
                    showCategory(name, contentLayout)
                }
            }
            tabRow.addView(tab)
        }

        mainLayout.addView(tabRow)
        mainLayout.addView(contentScroll)
        addView(mainLayout)

        // Show first category
        showCategory(categories.keys.first(), contentLayout)

        visibility = View.GONE
    }

    private fun showCategory(name: String, container: LinearLayout) {
        container.removeAllViews()
        val symbols = categories[name] ?: return

        val grid = GridLayout(context).apply {
            columnCount = 8
            rowCount = (symbols.size + 7) / 8
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        symbols.forEach { sym ->
            val btn = Button(context).apply {
                text = sym
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFF333333.toInt())
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(40)
                    height = dp(40)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                setOnClickListener {
                    onSymbolSelected?.invoke(sym)
                }
            }
            grid.addView(btn)
        }

        container.addView(grid)
    }

    fun show() {
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(150).start()
    }

    fun dismiss() {
        animate().alpha(0f).setDuration(100).withEndAction {
            visibility = View.GONE
            (parent as? android.view.ViewGroup)?.removeView(this)
        }.start()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
