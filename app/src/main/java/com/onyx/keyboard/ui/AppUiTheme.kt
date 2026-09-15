package com.onyx.keyboard.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.onyx.keyboard.data.Prefs

/**
 * لوحة ألوان واجهة تطبيق Onyx (MainActivity)
 * تدعم الوضعين الداكن والفاتح مع الألوان الديناميكية (Material You) المأخوذة من خلفية الجهاز.
 */
data class AppColorScheme(
    val isDark: Boolean,
    val isDynamic: Boolean,
    val background: Int,
    val cardBackground: Int,
    val cardStroke: Int,
    val primary: Int,
    val onPrimary: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val inputBackground: Int,
    val chipInactiveBackground: Int,
    val chipInactiveText: Int,
    val divider: Int,
)

object AppUiTheme {

    /**
     * فحص هل الوضع الحالي داكن أم فاتح بناء على تفضيل المستخدم وحالة النظام
     */
    fun isNightMode(context: Context, themeMode: Int): Boolean {
        return when (themeMode) {
            Prefs.THEME_MODE_LIGHT -> false
            Prefs.THEME_MODE_DARK -> true
            else -> {
                val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    /**
     * اشتقاق لوحة الألوان كاملة لواجهة التطبيق
     */
    fun resolve(context: Context, themeMode: Int, dynamicColor: Boolean): AppColorScheme {
        val dark = isNightMode(context, themeMode)
        val swatches = if (dynamicColor) MaterialYou.swatches(context) else null

        if (dynamicColor && swatches != null && swatches.isNotEmpty()) {
            val prim = swatches[0]
            val sec = if (swatches.size > 1) swatches[1] else prim

            return if (dark) {
                val primaryColor = MaterialYou.withV(prim, 0.82f, 0.85f)
                val bg = MaterialYou.withV(prim, 0.08f, 0.35f)
                val card = MaterialYou.withV(prim, 0.13f, 0.35f)
                AppColorScheme(
                    isDark = true,
                    isDynamic = true,
                    background = bg,
                    cardBackground = card,
                    cardStroke = Color.argb(45, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                    primary = primaryColor,
                    onPrimary = Color.parseColor("#101018"),
                    textPrimary = Color.parseColor("#EEEEF8"),
                    textSecondary = MaterialYou.withV(sec, 0.72f, 0.30f),
                    inputBackground = MaterialYou.withV(prim, 0.20f, 0.25f),
                    chipInactiveBackground = MaterialYou.withV(prim, 0.19f, 0.20f),
                    chipInactiveText = Color.argb(210, 240, 240, 255),
                    divider = Color.argb(35, 255, 255, 255),
                )
            } else {
                val primaryColor = MaterialYou.withV(prim, 0.44f, 0.90f)
                val bg = MaterialYou.withV(prim, 0.965f, 0.15f)
                val card = Color.WHITE
                AppColorScheme(
                    isDark = false,
                    isDynamic = true,
                    background = bg,
                    cardBackground = card,
                    cardStroke = Color.argb(40, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                    primary = primaryColor,
                    onPrimary = Color.WHITE,
                    textPrimary = Color.parseColor("#151622"),
                    textSecondary = Color.parseColor("#5A5D72"),
                    inputBackground = MaterialYou.withV(prim, 0.93f, 0.18f),
                    chipInactiveBackground = MaterialYou.withV(prim, 0.93f, 0.18f),
                    chipInactiveText = Color.parseColor("#383A4C"),
                    divider = Color.argb(20, 0, 0, 0),
                )
            }
        }

        // ثيم Onyx القياسي المصقول (عند تعطيل الألوان الديناميكية أو عدم توفرها)
        return createStatic(dark)
    }

    /**
     * إنشاء نظام ألوان Onyx القياسي الثابت (فاتح أو داكن)
     */
    fun createStatic(isDark: Boolean): AppColorScheme {
        return if (isDark) {
            val accent = 0xFFA78BFA.toInt()
            AppColorScheme(
                isDark = true,
                isDynamic = false,
                background = 0xFF101017.toInt(),
                cardBackground = 0xFF16161F.toInt(),
                cardStroke = 0x28A78BFA.toInt(),
                primary = accent,
                onPrimary = 0xFF14101F.toInt(),
                textPrimary = 0xFFEEEEF8.toInt(),
                textSecondary = 0xFF9D9DB5.toInt(),
                inputBackground = 0x1E808080.toInt(),
                chipInactiveBackground = 0x1C808080.toInt(),
                chipInactiveText = 0xC8FFFFFF.toInt(),
                divider = 0x1EFFFFFF.toInt(),
            )
        } else {
            val accent = 0xFF7C3AED.toInt()
            AppColorScheme(
                isDark = false,
                isDynamic = false,
                background = 0xFFF5F6FA.toInt(),
                cardBackground = 0xFFFFFFFF.toInt(),
                cardStroke = 0x237C3AED.toInt(),
                primary = accent,
                onPrimary = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF1A1828.toInt(),
                textSecondary = 0xFF636578.toInt(),
                inputBackground = 0x10000000.toInt(),
                chipInactiveBackground = 0x12000000.toInt(),
                chipInactiveText = 0xFF3B3D50.toInt(),
                divider = 0x19000000.toInt(),
            )
        }
    }
}
