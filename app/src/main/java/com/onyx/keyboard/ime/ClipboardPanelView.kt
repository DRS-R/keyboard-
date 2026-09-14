package com.onyx.keyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.onyx.keyboard.data.ClipboardHistory
import com.onyx.keyboard.model.KbTheme

/**
 * ClipboardPanelView — لوحة الحافظة التاريخية داخل اللوحة (M2 Nova)
 * قائمة آخر النسخ: لمس = لصق · تثبيت · حذف · مسح الكل.
 * محلي بالكامل — لا صلاحيات ولا إنترنت.
 */
class ClipboardPanelView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onPaste(text: String)
        fun onPanelClosed()
    }

    var listener: Listener? = null
    var history: ClipboardHistory? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = v * density

    private var theme: KbTheme? = null
    private val list = LinearLayout(context)
    private val header = LinearLayout(context)

    init {
        orientation = LinearLayout.VERTICAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(10f).toInt(), dp(8f).toInt(), dp(10f).toInt(), dp(4f).toInt())
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(context).apply { addView(list) }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun applyTheme(th: KbTheme) {
        theme = th
        background = GradientDrawable().apply {
            setColor(th.bg)
            cornerRadius = dp(14f)
        }
        refresh()
    }

    private fun headerLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(theme?.keyText ?: Color.WHITE)
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun headerButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = label
        textSize = 13f
        setPadding(dp(14f).toInt(), dp(6f).toInt(), dp(14f).toInt(), dp(6f).toInt())
        setTextColor(theme?.accent ?: Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(16f)
            setColor(theme?.key ?: Color.GRAY)
        }
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.marginStart = dp(6f).toInt()
        layoutParams = lp
        setOnClickListener { onClick() }
    }

    private fun row(text: String, index: Int, pinned: Boolean): View {
        val th = theme
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f).toInt(), dp(8f).toInt(), dp(10f).toInt(), dp(8f).toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(th?.surface ?: Color.DKGRAY)
                if (pinned) setStroke(dp(1.2f).toInt(), th?.accent ?: Color.YELLOW)
            }
        }
        row.addView(TextView(context).apply {
            this.text = if (pinned) "📌 $text" else text
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(th?.keyText ?: Color.WHITE)
        })
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                this.text = if (pinned) "إلغاء التثبيت" else "تثبيت"
                textSize = 12f
                setPadding(0, dp(4f).toInt(), dp(14f).toInt(), 0)
                setTextColor(th?.accent ?: Color.LTGRAY)
                setOnClickListener { history?.togglePin(index); refresh() }
            })
            addView(TextView(context).apply {
                this.text = "حذف"
                textSize = 12f
                setPadding(0, dp(4f).toInt(), 0, 0)
                setTextColor(Color.rgb(235, 90, 90))
                setOnClickListener { history?.remove(index); refresh() }
            })
        })
        row.setOnClickListener { listener?.onPaste(text) }
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(8f).toInt(), dp(4f).toInt(), dp(8f).toInt(), dp(4f).toInt())
        row.layoutParams = lp
        return row
    }

    /** إعادة بناء القائمة — تُستدعى عند الفتح والتغييرات */
    fun refresh() {
        val th = theme
        val h = history
        header.removeAllViews()
        header.addView(headerLabel("الحافظة التاريخية — ${h?.entries?.size ?: 0} عنصر"))
        header.addView(headerButton("مسح الكل") {
            h?.clearAll()
            refresh()
        })
        header.addView(headerButton("✕") { listener?.onPanelClosed() })

        list.removeAllViews()
        val entries = h?.entries ?: return
        if (entries.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "لا شيء بعد — انسخ نصاً من أي تطبيق وسيظهر هنا\nمحلي بالكامل: يُمحى تلقائياً بعد أسبوع"
                textSize = 13f
                setPadding(dp(12f).toInt(), dp(14f).toInt(), dp(12f).toInt(), dp(8f).toInt())
                setTextColor(Color.argb(170, Color.red(th?.keyText ?: Color.WHITE),
                    Color.green(th?.keyText ?: Color.WHITE), Color.blue(th?.keyText ?: Color.WHITE)))
            })
            return
        }
        for ((i, e) in entries.withIndex()) list.addView(row(e.text, i, e.pinned))
    }

    fun preferredHeightPx(): Int = dp(248f).toInt()
}
