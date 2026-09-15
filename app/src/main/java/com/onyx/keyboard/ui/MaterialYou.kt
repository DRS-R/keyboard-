package com.onyx.keyboard.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.onyx.keyboard.model.KbTheme

/**
 * Material You — توليد ثيم اللوحة كاملاً من ألوان خلفية النظام (API 31+)
 * صفر أذونات: getWallpaperColors متاح لكل التطبيقات منذ API 24.
 * يتبع الوضع الليلي للنظام تلقائياً، ويتحدث حياً عند تغيير الخلفية.
 */
object MaterialYou {

    const val THEME_ID = "matyou"

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 31

    /** عينات الخلفية [أصلي، ثانوي، ثالث] — للمعاينة الحية في الإعدادات */
    fun swatches(context: Context): IntArray? {
        if (!supported()) return null
        return try {
            val wc = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
            val prim = wc.primaryColor.toArgb()
            val sec = wc.secondaryColor?.toArgb() ?: prim
            val tert = wc.tertiaryColor?.toArgb() ?: sec
            intArrayOf(prim, sec, tert)
        } catch (e: Exception) {
            null
        }
    }

    /** ثيم كامل مشتق من خلفية الجهاز — null إن لم تتوفر ألوان (شاشة قفل فقط مثلًا) */
    fun buildTheme(context: Context): KbTheme? {
        val sw = swatches(context) ?: return null
        val dark = (context.resources.configuration.uiMode
            and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return derive(sw[0], sw[1], dark)
    }

    /** تسجيل مستمع تغيّر الخلفية — يعيد الكائن لإلغائه في onDestroy */
    fun listen(
        context: Context,
        onChange: () -> Unit,
    ): WallpaperManager.OnColorsChangedListener? {
        if (!supported()) return null
        return try {
            val wm = WallpaperManager.getInstance(context)
            val l = WallpaperManager.OnColorsChangedListener { _, _ -> onChange() }
            wm.addOnColorsChangedListener(l, Handler(Looper.getMainLooper()))
            l
        } catch (e: Exception) {
            null
        }
    }

    fun stopListening(context: Context, listener: WallpaperManager.OnColorsChangedListener?) {
        if (listener == null || !supported()) return
        try {
            WallpaperManager.getInstance(context).removeOnColorsChangedListener(listener)
        } catch (e: Exception) {
            // مدير الخلفية غير متاح — لا شيء
        }
    }

    /* ================= اشتقاق اللوحة اللونية ================= */

    /** تثبيت قيمة الإضاءة V في HSV مع تهدئة التشبع قليلاً */
    fun withV(color: Int, v: Float, satMul: Float = 0.92f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * satMul).coerceIn(0f, 1f)
        hsv[2] = v.coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    fun vOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2]
    }

    /** نسبة تباين تقريبية (WCAG مبسطة) لضمان قراءة نص الإبراز */
    fun contrast(a: Int, b: Int): Float {
        fun lum(c: Int): Float {
            fun ch(v: Int): Float {
                val s = v / 255f
                return if (s <= 0.03928f) s / 12.92f else Math.pow(((s + 0.055) / 1.055).toDouble(), 2.4).toFloat()
            }
            return 0.2126f * ch(Color.red(c)) + 0.7152f * ch(Color.green(c)) + 0.0722f * ch(Color.blue(c))
        }
        val l1 = lum(a).coerceAtLeast(lum(b))
        val l2 = lum(a).coerceAtMost(lum(b))
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    /** بناء KbTheme من اللون الأصلي والثانوي للخلفية */
    fun derive(primary: Int, secondary: Int, dark: Boolean): KbTheme {
        val bg: Int; val surface: Int; val key: Int; val keyAlt: Int
        val keyText: Int; val accent: Int; val accentText: Int; val trail: Int

        if (dark) {
            bg = withV(primary, 0.10f)
            surface = withV(primary, 0.14f)
            key = withV(primary, 0.20f)
            keyAlt = withV(secondary, 0.28f)
            keyText = withV(primary, 0.96f, satMul = 0.25f)
            // الإبراز فاتح على المفاتيح الداكنة
            accent = withV(primary, if (vOf(primary) < 0.65f) 0.74f else vOf(primary).coerceAtMost(0.92f))
            trail = withV(secondary, 0.62f)
        } else {
            bg = withV(primary, 0.965f, satMul = 0.35f)
            surface = withV(primary, 0.935f, satMul = 0.40f)
            key = withV(primary, 0.995f, satMul = 0.30f)
            keyAlt = withV(primary, 0.90f, satMul = 0.45f)
            keyText = withV(primary, 0.16f, satMul = 0.45f)
            // الإبراز غامق على المفاتيح الفاتحة
            accent = withV(primary, if (vOf(primary) > 0.55f) 0.46f else vOf(primary))
            trail = withV(secondary, 0.58f)
        }

        // نص الإبراز: داكن أو فاتح حسب تباينه مع اللون المميز
        accentText = if (contrast(accent, Color.BLACK) >= contrast(accent, Color.WHITE))
            Color.argb(255, 16, 16, 22) else Color.WHITE
        // تصحيح أخير: إن كان التباين ضعيفاً نعكس الاختيار
        if (contrast(accent, accentText) < 2.6f) {
            val flipped = if (accentText == Color.WHITE)
                Color.argb(255, 16, 16, 22) else Color.WHITE
            return KbTheme(THEME_ID, "Material You", dark, bg, surface, key, keyText, keyAlt,
                accent, flipped, trail)
        }
        return KbTheme(THEME_ID, "Material You", dark, bg, surface, key, keyText, keyAlt,
            accent, accentText, trail)
    }
}
