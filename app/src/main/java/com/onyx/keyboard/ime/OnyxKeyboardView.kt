package com.onyx.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.onyx.keyboard.model.KeyDef
import com.onyx.keyboard.model.KbTemplate
import com.onyx.keyboard.model.KbTheme
import kotlin.math.abs
import kotlin.math.hypot

/**
 * OnyxKeyboardView — لوحة مرسومة بالكامل على Canvas (أداء أصلي بدون XML لكل زر)
 * إيماءات: نقرة + ضغط مطول (بدائل/تكرار) + سحب مستمر بمسار حي — ترحيل من OnyxKeyboard.tsx
 */
class OnyxKeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onChar(c: String)
        fun onAction(action: String, key: KeyDef?)
        fun onAlt(c: String)
        fun onGlide(seq: List<String>)
    }

    var listener: Listener? = null

    private var rows: List<List<KeyDef>> = emptyList()
    private var mirrorRTL = false
    private val rects = ArrayList<ArrayList<RectF>>()

    var theme: KbTheme? = null
    var template: KbTemplate? = null
    var fontFamily: String = "sans-serif"
    var fontScale: Float = 1f
    var nightWarmth: Float = 0f
    var haptics: Boolean = true
    var glideEnabled: Boolean = true

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = v * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = dp(9f)
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val trailPath = Path()

    private var pressedR = -1
    private var pressedC = -1
    private var popupIndex = -1
    private var popupR = -1
    private var popupC = -1

    private val handler = Handler(Looper.getMainLooper())
    private var longRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null

    private var downX = 0f
    private var downY = 0f
    private var isGlide = false
    private var popupShown = false
    private val glidePts = ArrayList<FloatArray>()
    private var lastGx = 0f
    private var lastGy = 0f
    private var glideStartIsLetter = false

    private val rowHeightPx = dp(46f)
    private val padPx = dp(4f)

    fun setRows(rows: List<List<KeyDef>>, mirrorRTL: Boolean) {
        this.rows = rows
        this.mirrorRTL = mirrorRTL
        computeGeometry()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = rows.size.coerceAtLeast(1)
        val h = (padPx * 2 + rowHeightPx * rowCount + dp(5) * (rowCount - 1)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
    }

    private fun computeGeometry() {
        rects.clear()
        if (rows.isEmpty() || width <= 0) return
        val gap = dp(template?.gap?.toFloat() ?: 5f)
        for (row in rows) {
            val weights = row.sumOf { it.w.toDouble() }.toFloat()
            val avail = width - padPx * 2 - gap * (row.size - 1)
            val unit = avail / weights
            val list = ArrayList<RectF>(row.size)
            var x = padPx
            if (mirrorRTL) {
                // حساب من اليمين للوحة العربية
                var xr = width - padPx
                for (key in row) {
                    val kw = unit * key.w
                    xr -= kw
                    list.add(RectF(xr, 0f, xr + kw, 0f))
                    xr -= gap
                }
                // أعِد الترتيب بحيث يطابق الفهرس ترتيب المفاتيح المنطقي
            } else {
                for (key in row) {
                    val kw = unit * key.w
                    list.add(RectF(x, 0f, x + kw, 0f))
                    x += kw + gap
                }
            }
            rects.add(list)
        }
        // الإحداثيات الرأسية
        var y = padPx
        for (list in rects) {
            for (r in list) { r.top = y; r.bottom = y + rowHeightPx }
            y += rowHeightPx + dp(5)
        }
    }

    /* ================= الرسم ================= */
    override fun onDraw(canvas: Canvas) {
        val th = theme ?: return
        val tpl = template
        val radius = dp((tpl?.radius?.coerceAtMost(24)?.toFloat() ?: 10f))
        val gap = dp(tpl?.gap?.toFloat() ?: 5f)

        bgPaint.color = th.bg
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(14f), dp(14f), bgPaint)

        val fam = when (fontFamily) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            "sans-serif-condensed", "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "sans-serif-medium", "medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            "sans-serif-smallcaps", "smallcaps" -> Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)
            "sans-serif-thin", "thin" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            "sans-serif-black", "black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        textPaint.typeface = fam
        textPaint.textSize = dp(19f) * fontScale
        hintPaint.typeface = fam

        for (r in rects.indices) {
            for (c in rects[r].indices) {
                val key = rows[r][c]
                val rect = rects[r][c] ?: continue
                val isPressed = r == pressedR && c == pressedC && !isGlide
                val isModifier = key.action != null
                val isAccent = key.accent || key.action == "enter"

                // ظل القالب
                when (tpl?.shadow) {
                    "deep" -> {
                        keyPaint.color = Color.argb(70, 0, 0, 0)
                        canvas.drawRoundRect(rect.left, rect.top + dp(3f), rect.right, rect.bottom + dp(3f), radius, radius, keyPaint)
                    }
                    else -> {}
                }

                keyPaint.color = when {
                    isPressed && isAccent -> shade(th.accent, 0.85f)
                    isPressed && isModifier -> shade(th.keyAlt, 0.85f)
                    isPressed -> shade(th.key, 0.82f)
                    isAccent -> th.accent
                    isModifier -> th.keyAlt
                    else -> th.key
                }
                canvas.drawRoundRect(rect, radius, radius, keyPaint)

                if (tpl?.outline == true) {
                    strokePaint.color = Color.argb(110, Color.red(th.accent), Color.green(th.accent), Color.blue(th.accent))
                    strokePaint.strokeWidth = dp(1.4f)
                    canvas.drawRoundRect(rect, radius, radius, strokePaint)
                }
                if (tpl?.glass == true) {
                    keyPaint.color = Color.argb(16, 255, 255, 255)
                    canvas.drawRoundRect(rect, radius, radius, keyPaint)
                }
                if (tpl?.shadow == "inset") {
                    strokePaint.color = Color.argb(60, 0, 0, 0)
                    strokePaint.strokeWidth = dp(2f)
                    val inner = RectF(rect.left + dp(2f), rect.top + dp(2f), rect.right - dp(2f), rect.bottom - dp(2f))
                    canvas.drawRoundRect(inner, radius * 0.8f, radius * 0.8f, strokePaint)
                }

                // النص/الأيقونة
                val glyph = when (key.icon) {
                    "shift" -> "⇧"; "backspace" -> "⌫"; "globe" -> "🌐"
                    "smile" -> "😊"; "mic" -> "🎤"; "clipboard" -> "📋"
                    "check" -> "✓"; "keyboard" -> "⌨"; "hash" -> "#+="
                    else -> key.label ?: key.char ?: ""
                }
                textPaint.color = if (isAccent) th.accentText else th.keyText
                val cy = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(glyph, rect.centerX(), cy, textPaint)

                // تلميح بدائل الضغط المطول
                if (key.alts.isNotEmpty()) {
                    hintPaint.color = Color.argb(130, Color.red(th.keyText), Color.green(th.keyText), Color.blue(th.keyText))
                    canvas.drawText("•", rect.right - dp(8f), rect.top + dp(12f), hintPaint)
                }
                if (key.action == "space") {
                    hintPaint.color = Color.argb(110, Color.red(th.keyText), Color.green(th.keyText), Color.blue(th.keyText))
                    canvas.drawText("مسافة", rect.centerX(), rect.centerY() + dp(4f), hintPaint)
                }
            }
        }

        // منبثقة الضغط المطول
        if (popupIndex >= 0 && popupR >= 0 && popupC >= 0) {
            val key = rows[popupR][popupC]
            val alt = key.alts.getOrNull(popupIndex) ?: return
            val rect = rects[popupR][popupC]
            val pw = rect.width().coerceAtLeast(dp(52f))
            val ph = dp(50f)
            val left = (rect.centerX() - pw / 2).coerceIn(dp(2f), width - pw - dp(2f))
            val top = rect.top - ph - dp(6f)
            popupPaint.color = th.surface
            canvas.drawRoundRect(left, top, left + pw, top + ph, dp(12f), dp(12f), popupPaint)
            strokePaint.color = th.accent
            strokePaint.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(left, top, left + pw, top + ph, dp(12f), dp(12f), strokePaint)
            popupTextPaint.color = th.keyText
            popupTextPaint.textSize = dp(26f) * fontScale
            canvas.drawText(alt, left + pw / 2, top + ph / 2 + dp(9f), popupTextPaint)
        }

        // مسار السحب الحي
        if (isGlide && glidePts.isNotEmpty()) {
            trailPath.reset()
            trailPath.moveTo(glidePts[0][0], glidePts[0][1])
            for (i in 1 until glidePts.size) trailPath.lineTo(glidePts[i][0], glidePts[i][1])
            trailPaint.color = th.trail
            trailPaint.alpha = 170
            trailPaint.strokeWidth = dp(9f)
            canvas.drawPath(trailPath, trailPaint)
        }

        // طبقة ليلة القراءة (دافئة + تعتيم)
        if (nightWarmth > 0f) {
            val w = nightWarmth.coerceIn(0f, 1f)
            canvas.drawColor(Color.argb((52 * w).toInt(), 255, 138, 40))
            canvas.drawColor(Color.argb((34 * w).toInt(), 10, 4, 0))
        }
    }

    private fun shade(color: Int, factor: Float): Int {
        val mul = if (factor > 1f) 2f - factor else factor
        return Color.rgb(
            (Color.red(color) * mul).toInt().coerceIn(0, 255),
            (Color.green(color) * mul).toInt().coerceIn(0, 255),
            (Color.blue(color) * mul).toInt().coerceIn(0, 255),
        )
    }

    /* ================= اللمس ================= */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                isGlide = false
                popupShown = false
                glidePts.clear()
                val loc = findKey(event.x, event.y)
                pressedR = loc.first; pressedC = loc.second
                popupIndex = -1; popupR = -1; popupC = -1
                if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (pressedR >= 0) {
                    glideStartIsLetter = rows[pressedR][pressedC].char != null && rows[pressedR][pressedC].action == null
                    val key = rows[pressedR][pressedC]
                    if (key.action == "backspace") startRepeat()
                    else if (key.alts.isNotEmpty()) scheduleLongPress()
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                // أولاً: تتبع السحب المستمر
                if (isGlide) {
                    addGlidePoint(event.x, event.y)
                    invalidate()
                    return true
                }
                if (pressedR < 0) return true
                val key = rows[pressedR][pressedC]
                val moved = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()).toFloat()
                val glideThreshold = rowHeightPx * 0.7f
                if (!isGlide && glideEnabled && glideStartIsLetter && moved > glideThreshold) {
                    cancelPending()
                    isGlide = true
                    pressedR = -1; pressedC = -1
                    addGlidePoint(event.x, event.y)
                } else if (popupShown && key.alts.isNotEmpty()) {
                    // اختيار بديل بالسحب الأفقي أثناء الضغط المطول
                    val rect = rects[pressedR][pressedC]
                    val idx = ((event.x - downX) / (rect.width() * 0.6f)).toInt()
                    popupIndex = idx.coerceIn(0, key.alts.size - 1)
                    popupR = pressedR; popupC = pressedC
                } else {
                    // تحديث الزر المضغوط أثناء الانزلاق
                    val loc = findKey(event.x, event.y)
                    if (loc != (pressedR to pressedC)) {
                        pressedR = loc.first; pressedC = loc.second
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPending()
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (isGlide && glidePts.size >= 3) {
                        listener?.onGlide(glideSequence())
                    } else if (popupIndex >= 0 && popupR >= 0) {
                        val alt = rows[popupR][popupC].alts.getOrNull(popupIndex)
                        if (alt != null) listener?.onAlt(alt)
                        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } else if (pressedR >= 0) {
                        fire(pressedR, pressedC)
                    }
                }
                pressedR = -1; pressedC = -1
                popupIndex = -1; popupR = -1; popupC = -1
                popupShown = false
                isGlide = false
                glidePts.clear()
                invalidate()
            }
        }
        return true
    }

    private fun fire(r: Int, c: Int) {
        val key = rows[r][c]
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (key.action != null) listener?.onAction(key.action!!, key)
        else if (key.char != null) listener?.onChar(key.char!!)
    }

    private fun findKey(x: Float, y: Float): Pair<Int, Int> {
        for (r in rects.indices) {
            for (c in rects[r].indices) {
                val rect = rects[r][c]
                val pad = dp(3f)
                if (x >= rect.left - pad && x <= rect.right + pad && y >= rect.top - pad && y <= rect.bottom + pad) {
                    return r to c
                }
            }
        }
        return -1 to -1
    }

    private fun scheduleLongPress() {
        longRunnable = Runnable {
            if (pressedR >= 0 && rows[pressedR][pressedC].alts.isNotEmpty()) {
                popupR = pressedR; popupC = pressedC; popupIndex = 0
                popupShown = true
                if (haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
        }
        handler.postDelayed(longRunnable!!, 420)
    }

    private fun startRepeat() {
        repeatRunnable = object : Runnable {
            override fun run() {
                if (pressedR >= 0 && rows[pressedR][pressedC].action == "backspace") {
                    listener?.onAction("backspace", null)
                    handler.postDelayed(this, 55)
                }
            }
        }
        handler.postDelayed(repeatRunnable!!, 420)
    }

    private fun cancelPending() {
        longRunnable?.let { handler.removeCallbacks(it) }
        longRunnable = null
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    private fun addGlidePoint(x: Float, y: Float) {
        if (glidePts.isNotEmpty()) {
            val d = hypot((x - lastGx).toDouble(), (y - lastGy).toDouble())
            if (d < dp(12f)) return
        }
        glidePts.add(floatArrayOf(x, y))
        lastGx = x; lastGy = y
    }

    /** إسقاط المسار على حروف المفاتيح (ترحيل collectSwipeChars من الويب) */
    private fun glideSequence(): List<String> {
        val seq = ArrayList<String>()
        var last = ""
        val minD = dp(26f)
        for (pt in glidePts) {
            var bestR = -1; var bestC = -1; var bestD = Float.MAX_VALUE
            for (r in rects.indices) {
                for (c in rects[r].indices) {
                    val key = rows[r][c]
                    if (key.char == null || key.action != null) continue
                    val rect = rects[r][c]
                    val d = hypot((pt[0] - rect.centerX()).toDouble(), (pt[1] - rect.centerY()).toDouble()).toFloat()
                    if (d < bestD) { bestD = d; bestR = r; bestC = c }
                }
            }
            if (bestR >= 0 && bestD < rowHeightPx * 1.2f + minD) {
                val ch = rows[bestR][bestC].char!!
                if (ch != last) { seq.add(ch); last = ch }
            }
        }
        return seq
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPending()
    }
}
