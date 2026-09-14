package com.onyx.keyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.onyx.keyboard.model.KbTheme
import com.onyx.keyboard.model.OnyxAssets

/**
 * EmojiPanelView — لوحة إيموجي كاملة داخل اللوحة (M2 Nova)
 * تبويبات فئات + بحث بالوسوم العربي/الإنجليزي (من emoji_tags.tsv) + قسم الأحدث.
 * ترحيل مباشر لتجربة لوحة الإيموجي في نسخة الويب — بلا حوار نظام.
 */
class EmojiPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onEmojiPicked(emoji: String)
        fun onPanelClosed()
    }

    var listener: Listener? = null
    var assets: OnyxAssets? = null
    /** مزوّد الأحدث — الخدمة تدير التخزين */
    var recentsProvider: () -> List<String> = { emptyList() }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = v * density

    private var theme: KbTheme? = null

    private val search = EditText(context)
    private val tabsScroll = HorizontalScrollView(context)
    private val tabsRow = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val content = LinearLayout(context)

    private var activeTab = -1  // -1 = الأحدث، 0.. = فئات؛ -2 = نتائج بحث

    init {
        orientation = LinearLayout.VERTICAL
        search.hint = "ابحث: قلب، حزن، pizza…"
        search.setTextSize(14f)
        search.setSingleLine(true)
        search.setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt(), dp(8f).toInt())
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                activeTab = if (s.isNullOrBlank()) lastTab else -2
                rebuild()
            }
        })
        val searchWrap = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt(), 0)
            addView(search, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "✕"
                textSize = 16f
                setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt(), dp(8f).toInt())
                setOnClickListener { listener?.onPanelClosed() }
            })
        }
        tabsScroll.isHorizontalScrollBarEnabled = false
        tabsScroll.addView(tabsRow)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(content)
        addView(searchWrap, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(tabsScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private var lastTab = 0

    /** تلوين وإعادة بناء — تُستدعى من الخدمة عند فتح اللوحة أو تغيير الثيم */
    fun applyTheme(th: KbTheme) {
        theme = th
        background = GradientDrawable().apply {
            setColor(th.bg)
            cornerRadius = dp(14f)
        }
        search.setTextColor(th.keyText)
        search.setHintTextColor(Color.argb(120, Color.red(th.keyText), Color.green(th.keyText), Color.blue(th.keyText)))
        search.background = GradientDrawable().apply {
            setColor(th.surface)
            cornerRadius = dp(10f)
        }
        rebuild()
    }

    private fun tabChip(label: String, index: Int): View {
        val th = theme
        return TextView(context).apply {
            text = label
            textSize = 13f
            setPadding(dp(14f).toInt(), dp(7f).toInt(), dp(14f).toInt(), dp(7f).toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(18f)
                setColor(
                    if (activeTab == index) th?.accent ?: Color.GRAY
                    else th?.key ?: Color.GRAY
                )
            }
            setTextColor(if (activeTab == index) th?.accentText ?: Color.WHITE else th?.keyText ?: Color.WHITE)
            val lp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(6f).toInt()
            layoutParams = lp
            setOnClickListener {
                lastTab = index.coerceAtLeast(0)
                activeTab = index
                search.setText("")
                rebuild()
            }
        }
    }

    private fun emojiGrid(emojis: List<String>, columns: Int = 8): View {
        val grid = GridLayout(context).apply { columnCount = columns }
        val per = ((emojis.size + columns - 1) / columns).coerceAtLeast(1)
        for (e in emojis) {
            grid.addView(TextView(context).apply {
                text = e
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(dp(6f).toInt(), dp(5f).toInt(), dp(6f).toInt(), dp(5f).toInt())
                setOnClickListener { listener?.onEmojiPicked(e) }
            })
        }
        // املأ الصف الأخير حتى لا يظهر مزاحاً في RTL
        val missing = (per * columns - emojis.size).coerceAtLeast(0)
        for (i in 0 until missing) {
            grid.addView(View(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(34f).toInt(); height = dp(38f).toInt()
                }
            })
        }
        return grid
    }

    private fun sectionLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(4f).toInt(), dp(10f).toInt(), dp(4f).toInt(), dp(2f).toInt())
        setTextColor(theme?.keyText ?: Color.WHITE)
    }

    private fun rebuild() {
        val a = assets ?: return
        val th = theme
        tabsRow.removeAllViews()
        content.removeAllViews()

        if (activeTab == -2) {
            // نتائج البحث
            val results = a.searchEmojis(search.text.toString())
            if (results.isEmpty()) {
                content.addView(TextView(context).apply {
                    text = "لا نتائج — جرّب كلمة أخرى"
                    textSize = 13f
                    setPadding(dp(8f).toInt(), dp(16f).toInt(), dp(8f).toInt(), dp(8f).toInt())
                    setTextColor(th?.keyText ?: Color.WHITE)
                })
            } else {
                content.addView(emojiGrid(results))
            }
            return
        }

        // التبويبات: الأحدث + الفئات
        val recents = recentsProvider()
        if (recents.isNotEmpty()) tabsRow.addView(tabChip("🕘 الأحدث", -1))
        for ((i, g) in a.emojiGroups.withIndex()) tabsRow.addView(tabChip(g.name, i))

        when {
            activeTab == -1 && recents.isNotEmpty() -> {
                content.addView(sectionLabel("الذين استخدمتهم مؤخراً"))
                content.addView(emojiGrid(recents))
            }
            activeTab in a.emojiGroups.indices -> {
                val g = a.emojiGroups[activeTab]
                content.addView(sectionLabel(g.name))
                content.addView(emojiGrid(g.emojis))
            }
            else -> {
                // لا أحدث بعد → أول فئة
                if (a.emojiGroups.isNotEmpty()) {
                    val g = a.emojiGroups[0]
                    content.addView(sectionLabel(g.name))
                    content.addView(emojiGrid(g.emojis))
                }
            }
        }
    }

    /** ارتفاع اللوحة القياسي داخل الخدمة */
    fun preferredHeightPx(): Int = dp(248f).toInt()
}
