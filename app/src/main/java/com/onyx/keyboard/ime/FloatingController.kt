package com.onyx.keyboard.ime

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.onyx.keyboard.data.Prefs
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * FloatingController — الوضع العائم (M3 Legend + M3 Deep)
 * يحوّل نافذة لوحة الإدخال نفسها إلى بطاقة صغيرة قابلة للسحب بأي مكان على الشاشة،
 * تماماً كاللوحات العائمة في الأنظمة التجارية — عبر إعادة ضبط معاملات نافذة IME
 * (مسموح للنظام منذ API 28) — صفر أذونات وبدون SYSTEM_ALERT_WINDOW.
 *
 * M3 Deep — تقليص/تكبير البطاقة:
 *  • زرا «－ / ＋» في الرأس: خطوة ±6٪ مع تحديث حي للنافذة
 *  • مقبض «⤡»: سحب أفقي يغيّر الحجم باستمرار
 *  • نقر مزدوج على المقبض: دورة بين قياسات جاهزة 50٪ / 62٪ / 78٪
 *  • الحجم يُحفظ (floatScale 0.40..0.90) ويُطبق عند كل دخول
 */
class FloatingController(private val ime: InputMethodService, private val prefs: Prefs) {

    companion object {
        fun supported(): Boolean = Build.VERSION.SDK_INT >= 28

        /* M3 Deep: حدود الحجم والقياسات الجاهزة */
        const val MIN_SCALE = 0.40f
        const val MAX_SCALE = 0.90f
        const val STEP = 0.06f
        val PRESETS = floatArrayOf(0.50f, 0.62f, 0.78f)
        fun clamp(s: Float): Float = s.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    var active = false
        private set

    private val density = ime.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var sizeLabel: TextView? = null

    /* ================= الحجم (M3 Deep) ================= */

    private fun scale(): Float = clamp(prefs.floatScale)

    /** عرض البطاقة بالبكسل وفق الحجم الحالي */
    fun cardWidth(): Int =
        (ime.resources.displayMetrics.widthPixels * scale()).toInt()

    private fun pctText(): String = "${(scale() * 100).toInt()}٪"

    /** تغيير الحجم مع حفظ وتحديث حي للنافذة والتسمية */
    fun setScale(s: Float) {
        prefs.floatScale = clamp(s)
        sizeLabel?.text = pctText()
        if (active) applyWidth()
    }

    fun nudge(direction: Int) = setScale(scale() + direction * STEP)

    /** إعادة تطبيق العرض المحفوظ — تُستدعى من الإعدادات أثناء العائم الحي */
    fun refreshWidth() {
        if (active) applyWidth()
    }

    private fun applyWidth() {
        val dm = ime.resources.displayMetrics
        val lp = windowParams() ?: return
        lp.width = cardWidth()
        // تثبيت داخل الحدود اليمنى بعد التكبير
        lp.x = min(max(0, lp.x), (dm.widthPixels - lp.width).coerceAtLeast(0))
        setWindowParams(lp)
    }

    /** نقر مزدوج: أقرب قياس جاهز ثم التالي في الدورة */
    private fun cyclePreset() {
        val cur = scale()
        var idx = 0
        var best = Float.MAX_VALUE
        for ((i, p) in PRESETS.withIndex()) {
            val d = abs(p - cur)
            if (d < best) { best = d; idx = i }
        }
        setScale(PRESETS[(idx + 1) % PRESETS.size])
    }

    /* ================= بناء مقبض السحب ================= */

    /** شريط علوي رفيع: ⠿ للسحب + نسبة الحجم + －/＋ + مقبض الحجم ⤡ + ✕ للعودة */
    fun buildHeader(onExit: () -> Unit): LinearLayout {
        val ctx = ime
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f).toInt(), dp(6f).toInt(), dp(10f).toInt(), dp(6f).toInt())
        }
        val handle = TextView(ctx).apply {
            text = "⠿  اسحبني"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.argb(220, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        // M3 Deep: نسبة الحجم الحالية — تُحدّث مع كل تغيير
        sizeLabel = TextView(ctx).apply {
            text = pctText()
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.argb(200, 255, 255, 255))
            setPadding(dp(8f).toInt(), dp(2f).toInt(), dp(8f).toInt(), dp(2f).toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(Color.argb(40, 128, 128, 128))
            }
        }
        fun sizeBtn(label: String, onClick: () -> Unit): TextView = TextView(ctx).apply {
            text = label
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(dp(12f).toInt(), dp(2f).toInt(), dp(12f).toInt(), dp(2f).toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(Color.argb(50, 128, 128, 128))
            }
            setOnClickListener { onClick() }
        }
        val close = TextView(ctx).apply {
            text = "✕"
            textSize = 15f
            setPadding(dp(14f).toInt(), dp(4f).toInt(), dp(14f).toInt(), dp(4f).toInt())
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(Color.argb(50, 128, 128, 128))
            }
            setOnClickListener { onExit() }
        }
        row.addView(handle)
        row.addView(sizeLabel)
        row.addView(sizeBtn("－") { nudge(-1) })
        row.addView(sizeBtn("＋") { nudge(+1) })
        // M3 Deep: مقبض الحجم — سحب أفقي مستمر
        val grip = TextView(ctx).apply {
            text = "⤡"
            textSize = 14f
            setPadding(dp(10f).toInt(), dp(2f).toInt(), dp(10f).toInt(), dp(2f).toInt())
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(Color.argb(50, 128, 128, 128))
            }
        }
        attachResize(grip)
        row.addView(grip)
        row.addView(close)
        attachDrag(row)
        return row
    }

    /** لمس المقبض يحرّك النافذة كاملة — إحداثيات مطلقة مع تثبيت داخل حدود الشاشة
     *  M3 Deep: نقر مزدوج (بلا سحب) يُدوّر القياسات الجاهزة */
    fun attachDrag(handle: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var lastTapAt = 0L
        handle.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val lp = windowParams() ?: return@setOnTouchListener false
                    downRawX = ev.rawX; downRawY = ev.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val lp = windowParams() ?: return@setOnTouchListener false
                    val dx = (ev.rawX - downRawX).toInt()
                    val dy = (ev.rawY - downRawY).toInt()
                    if (!moved && max(abs(dx), abs(dy)) < dp(4f)) return@setOnTouchListener true
                    moved = true
                    val dm = ime.resources.displayMetrics
                    lp.x = min(max(0, startX + dx), dm.widthPixels - min(cardWidth(), dm.widthPixels))
                    lp.y = min(max(0, startY + dy), dm.heightPixels - dp(120f).toInt())
                    setWindowParams(lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val lp = windowParams()
                        if (lp != null) {
                            prefs.floatX = lp.x
                            prefs.floatY = lp.y
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastTapAt < 320L) cyclePreset()
                        lastTapAt = now
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    /** M3 Deep: سحب أفقي على مقبض الحجم يغيّر النسبة باستمرار مع تحديث حي */
    fun attachResize(grip: View) {
        var downRawX = 0f
        var downScale = 0f
        var resizing = false
        grip.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downScale = scale()
                    resizing = true
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!resizing) return@setOnTouchListener true
                    val dm = ime.resources.displayMetrics
                    val dx = ev.rawX - downRawX
                    // الاتجاهان يعملان: سحب يميناً أو يساراً يوسّط الاتجاه الأقرب للطبيعة
                    setScale(downScale + abs(dx) / dm.widthPixels * (if (dx >= 0) 1f else -1f))
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resizing = false
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    /* ================= تطبيق الوضع على النافذة ================= */

    private fun windowParams(): WindowManager.LayoutParams? = try {
        ime.window?.window?.attributes as? WindowManager.LayoutParams
    } catch (e: Exception) {
        null
    }

    private fun setWindowParams(lp: WindowManager.LayoutParams) = try {
        ime.window?.window?.attributes = lp
    } catch (e: Exception) {
        // النافذة لم تُنشأ بعد — نتجاهل
    }

    /** تفعيل العائم: عرض البطاقة وفق الحجم المحفوظ + الرفع لآخر موضع */
    fun enter(root: View) {
        if (!supported()) return
        active = true
        val dm = ime.resources.displayMetrics
        val lp = windowParams() ?: run { active = false; return }
        lp.width = cardWidth()
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = if (prefs.floatX >= 0) prefs.floatX.coerceIn(0, (dm.widthPixels - lp.width).coerceAtLeast(0))
        else ((dm.widthPixels - lp.width) / 2f).toInt()
        lp.y = if (prefs.floatY >= 0) prefs.floatY else (dm.heightPixels * 0.18f).toInt()
        setWindowParams(lp)
        sizeLabel?.text = pctText()
        // مظهر بطاقة: حواف دائرية + ظل خارجي عبر حشوة شفافة
        root.setPadding(dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt())
    }

    /** العودة إلى الوضع الراسي الكامل */
    fun exit(root: View) {
        active = false
        val lp = windowParams() ?: return
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.BOTTOM
        lp.x = 0
        lp.y = 0
        setWindowParams(lp)
        root.setPadding(0, 0, 0, 0)
    }
}
