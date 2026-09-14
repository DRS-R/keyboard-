package com.onyx.keyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.onyx.keyboard.engine.OnyxEngine
import com.onyx.keyboard.model.KbTheme

/**
 * SuggestionBar — شريط الاقتراحات والشرائح الذكية
 * اقتراحات بشارات الخوارزمية (#N) + شرائح سياقية (حافظة/رابط/هاتف/إيميل/OTP/تاريخ/ترحيب)
 */
class SuggestionBar(context: Context) : HorizontalScrollView(context) {

    interface Listener {
        fun onSuggestion(s: OnyxEngine.Sug)
        fun onChipText(text: String)
        fun onDecor()
        fun onEmoji()
        // M2 Nova
        fun onVoice()
        fun onClipboardPanel()
        fun onTashkeel()
        // M3 Legend
        fun onSpellFix(wrong: String, fix: String)
        fun onSpellDismiss()
        fun onFloatingToggle()
        fun onOneHandedCycle()
        // M3 Deep — المصحح القواعدي للجمل (52)
        fun onGrammarApply()
        fun onGrammarDismiss()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = v * density

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6f).toInt(), dp(4f).toInt(), dp(6f).toInt(), dp(4f).toInt())
    }

    var theme: KbTheme? = null
    var nightWarmth: Float = 0f
    /** M2: شريحة التشكيل تظهر للغات RTL فقط */
    private var showTashkeelChip = false
    private var tashkeelActive = false
    /** M3: شريحة التصحيح الفوري — الكلمة الخاطئة + بدائلها */
    private var spellWrong: String? = null
    private var spellFixes: List<String> = emptyList()
    /** M3 Deep: ملاحظات المصحح القواعدي للجملة (52) */
    private var grammarIssues: List<OnyxEngine.GrammarIssue> = emptyList()
    /** M3: حالة أدوات النافذة — للعرض النشط */
    private var floatingActive = false
    private var oneHandedState = 0
    /** M3: هل الأدوات العائمة متاحة (API 28+) */
    var floatingAvailable: Boolean = com.onyx.keyboard.ime.FloatingController.supported()

    init {
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        isHorizontalScrollBarEnabled = false
    }

    fun applyTheme(th: KbTheme, night: Float) {
        theme = th
        nightWarmth = night
        var bg = th.surface
        if (night > 0f) {
            val w = night.coerceIn(0f, 1f)
            bg = blend(bg, Color.rgb(255, 138, 40), (0.18f * w))
        }
        background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(14f)
        }
        invalidate()
    }

    private fun blend(base: Int, over: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(base) * (1 - a) + Color.red(over) * a).toInt(),
            (Color.green(base) * (1 - a) + Color.green(over) * a).toInt(),
            (Color.blue(base) * (1 - a) + Color.blue(over) * a).toInt(),
        )
    }

    private fun clear() = row.removeAllViews()

    private fun chipView(label: String, icon: String?, dim: Boolean, fixStyle: Boolean, badge: Int): TextView {
        val th = theme
        val tv = TextView(context)
        tv.text = if (icon != null) "$icon $label" else label
        tv.textSize = 14f
        tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.setPadding(dp(14f).toInt(), dp(7f).toInt(), dp(14f).toInt(), dp(7f).toInt())
        tv.gravity = Gravity.CENTER
        val bgc = when {
            fixStyle -> th?.accent ?: Color.LTGRAY
            dim -> blend(th?.key ?: Color.GRAY, th?.bg ?: Color.BLACK, 0.45f)
            else -> th?.key ?: Color.GRAY
        }
        tv.background = GradientDrawable().apply {
            setColor(bgc)
            cornerRadius = dp(20f)
            if (dim || fixStyle) setStroke(dp(1f).toInt(), Color.argb(60, 128, 128, 128))
        }
        tv.setTextColor(if (fixStyle) th?.accentText ?: Color.WHITE else th?.keyText ?: Color.WHITE)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = dp(6f).toInt()
        tv.layoutParams = lp
        return tv
    }

    /** اقتراحات الكلمة الحالية مع شارات الخوارزميات + إيموجي سياقي (35) */
    fun showSuggestions(
        list: List<OnyxEngine.Sug>,
        badges: Boolean,
        arabiziHint: String? = null,
        contextEmojis: List<String> = emptyList(),
    ) {
        clear()
        for (s in list) {
            val label = if (badges && s.algo > 0) "${s.word} #${s.algo}" else s.word
            val tv = chipView(label, icon = null, dim = false, fixStyle = s.type == "fix", badge = s.algo)
            tv.setOnClickListener { listener?.onSuggestion(s) }
            row.addView(tv)
        }
        if (arabiziHint != null && list.isNotEmpty()) {
            val tv = chipView("عربيزي: $arabiziHint", "🔤", dim = true, fixStyle = false, badge = 0)
            tv.setOnClickListener { listener?.onChipText(arabiziHint) }
            row.addView(tv)
        }
        for (e in contextEmojis) {
            val tv = chipView(e, null, dim = true, fixStyle = false, badge = 0)
            tv.setOnClickListener { listener?.onChipText(e) }
            row.addView(tv)
        }
        addToolChips()
        fullScroll(View.FOCUS_LEFT)
    }

    /** الكلمة التالية + الشرائح الذكية */
    fun showNextAndChips(next: List<OnyxEngine.Sug>, chips: List<Pair<String, String>>) {
        clear()
        addGrammarChips()   // M3 Deep: القواعد أولوية العرض (تظهر فقط بلا شريحة إملائية)
        addSpellChips()
        for (c in chips) {
            val tv = chipView(c.first.take(22), null, dim = true, fixStyle = false, badge = 0)
            tv.setOnClickListener { listener?.onChipText(c.second) }
            row.addView(tv)
        }
        for (s in next) {
            val tv = chipView(s.word, null, dim = false, fixStyle = false, badge = s.algo)
            tv.setOnClickListener { listener?.onSuggestion(s) }
            row.addView(tv)
        }
        addToolChips()
        fullScroll(View.FOCUS_LEFT)
    }

    fun showIdle(chips: List<Pair<String, String>> = emptyList()) {
        showNextAndChips(emptyList(), chips)
    }

    /** حالة شريحة التشكيل — تُستدعى من الخدمة عند تغيّر اللغة/الطبقة */
    fun setTashkeelState(show: Boolean, active: Boolean) {
        showTashkeelChip = show
        tashkeelActive = active
    }

    /** M3: تعيين شريحة التصحيح — null لإخفائها */
    fun setSpellFix(wrong: String?, fixes: List<String>) {
        spellWrong = wrong
        spellFixes = fixes
    }

    /** M3 Deep: تعيين ملاحظات المصحح القواعدي — قائمة فارغة للإخفاء */
    fun setGrammar(issues: List<OnyxEngine.GrammarIssue>) {
        grammarIssues = issues
    }

    /** M3: حالة النافذة (عائم/يد واحدة) لعرض الأدوات نشطة */
    fun setWindowState(floating: Boolean, oneHanded: Int) {
        floatingActive = floating
        oneHandedState = oneHanded
    }

    /** M3: شريحة التصحيح الفوري — أول الشريط بلون الإبراز */
    private fun addSpellChips() {
        val wrong = spellWrong ?: return
        if (spellFixes.isEmpty()) return
        val best = spellFixes.first()
        val tv = chipView("✓ $best", "⚠", dim = false, fixStyle = true, badge = 51)
        tv.setOnClickListener { listener?.onSpellFix(wrong, best) }
        row.addView(tv)
        // بدائل إضافية إن وجدت
        for (alt in spellFixes.drop(1).take(2)) {
            val tv2 = chipView(alt, null, dim = true, fixStyle = false, badge = 51)
            tv2.setOnClickListener { listener?.onSpellFix(wrong, alt) }
            row.addView(tv2)
        }
        // تجاهل
        val ig = chipView("تجاهل", null, dim = true, fixStyle = false, badge = 0)
        ig.setOnClickListener { listener?.onSpellDismiss() }
        row.addView(ig)
    }

    /** M3 Deep: شرائح المصحح القواعدي — أول الملاحظات + عدّاد، ونقرة واحدة تطبّق الكل (52) */
    private fun addGrammarChips() {
        val first = grammarIssues.firstOrNull() ?: return
        val tv = chipView("✎ ${first.before} → ${first.after}", null, dim = false, fixStyle = true, badge = 52)
        tv.setOnClickListener { listener?.onGrammarApply() }
        row.addView(tv)
        if (grammarIssues.size > 1) {
            val more = chipView("+${grammarIssues.size - 1} قواعد", null, dim = true, fixStyle = false, badge = 52)
            more.setOnClickListener { listener?.onGrammarApply() }
            row.addView(more)
        }
        val ig = chipView("تجاهل", null, dim = true, fixStyle = false, badge = 0)
        ig.setOnClickListener { listener?.onGrammarDismiss() }
        row.addView(ig)
    }

    /** أدوات ثابتة يمين الشريط: تشكيل؟ + مايك + حافظة + زخرفة + إيموجي + عائم + يد واحدة */
    private fun addToolChips() {
        val th = theme
        fun tool(label: String, size: Float, onClick: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                textSize = size
                setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(6f).toInt())
                background = GradientDrawable().apply {
                    setColor(th?.keyAlt ?: Color.GRAY)
                    cornerRadius = dp(20f)
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = dp(6f).toInt()
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        if (showTashkeelChip) {
            val tk = tool(if (tashkeelActive) "أ‌ب‌ج" else "َــ", 14f) { listener?.onTashkeel() }
            tk.setTextColor(if (tashkeelActive) th?.accent ?: Color.WHITE else th?.accent ?: Color.WHITE)
            row.addView(tk)
        }
        row.addView(tool("🎤", 15f) { listener?.onVoice() })
        row.addView(tool("📋", 14f) { listener?.onClipboardPanel() })
        // M3: الوضع العائم ويد واحدة
        if (floatingAvailable) {
            val fl = tool("⛶", 15f) { listener?.onFloatingToggle() }
            if (floatingActive) {
                fl.setTextColor(th?.accent ?: Color.WHITE)
                fl.background = GradientDrawable().apply {
                    setColor(Color.argb(70, 167, 139, 250))
                    cornerRadius = dp(20f)
                    setStroke(dp(1.2f).toInt(), th?.accent ?: Color.WHITE)
                }
            }
            row.addView(fl)
        }
        row.addView(tool(if (oneHandedState == 1) "→|" else if (oneHandedState == 2) "|←" else "⇤", 14f) {
            listener?.onOneHandedCycle()
        })
        row.addView(TextView(context).apply {
            text = "✿"
            textSize = 17f
            setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(6f).toInt())
            setTextColor(th?.accent ?: Color.WHITE)
            background = GradientDrawable().apply {
                setColor(th?.keyAlt ?: Color.GRAY)
                cornerRadius = dp(20f)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(6f).toInt()
            layoutParams = lp
            setOnClickListener { listener?.onDecor() }
        })
        row.addView(tool("😊", 15f) { listener?.onEmoji() })
    }
}
