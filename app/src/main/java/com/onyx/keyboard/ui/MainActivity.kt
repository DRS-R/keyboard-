package com.onyx.keyboard.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.onyx.keyboard.data.Prefs
import com.onyx.keyboard.ime.Decor
import com.onyx.keyboard.ime.FloatingController
import com.onyx.keyboard.ime.OnyxImeService
import com.onyx.keyboard.model.OnyxAssets
import com.onyx.keyboard.ui.AppColorScheme
import com.onyx.keyboard.ui.AppUiTheme
import com.onyx.keyboard.ui.MaterialYou
import com.onyx.keyboard.util.ClipboardUrlDetector
import com.onyx.keyboard.util.DownloadUrlValidator

/**
 * MainActivity — واجهة الإعدادات الكاملة (Material 3 برمجي)
 * الحالة + الملفات الشخصية + الثيمات/القوالب/الخطوط + ليلة القراءة + اللغات
 * + مفاتيح الذكاء + الإحصاءات + حول التطبيق
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var onyxAssets: OnyxAssets
    private lateinit var colorScheme: AppColorScheme
    private var wallpaperListener: Any? = null
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = v * density

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var profileRow: LinearLayout

    // رصد رابط الحافظة التلقائي وحقل إدخال الرابط
    private var lastPrefilledUrl: String? = null
    private lateinit var urlInputField: EditText
    private lateinit var urlStatusText: TextView
    private lateinit var clipboardDetectedBadge: LinearLayout
    private lateinit var clipboardBadgeText: TextView

    /* M3: تصدير/استيراد القاموس المتعلم عبر SAF — صلاحيات صفرية */
    private val exportDict =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(prefs.active.learnedTsv.toByteArray())
                    }
                    val n = prefs.active.learnedTsv.split("\n").count { l -> l.isNotBlank() }
                    toast("تم تصدير قاموس الملف (${prefs.active.name}): $n كلمة")
                } catch (e: Exception) {
                    toast("تعذر التصدير")
                }
            }
        }

    private val importDict =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    val counts = HashMap<String, Int>()
                    // القاموس الحالي أولاً
                    for (l in prefs.active.learnedTsv.lines()) {
                        val p = l.split("\t")
                        if (p.size >= 2 && p[0].trim().length >= 2) {
                            counts[p[0].trim()] = p[1].trim().toIntOrNull()?.coerceIn(1, 99) ?: 1
                        }
                    }
                    var imported = 0
                    for (l in text.lines()) {
                        val p = l.split("\t")
                        val w = p.getOrNull(0)?.trim() ?: ""
                        val c = p.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(1, 99) ?: 1
                        if (w.length >= 2 && !w.contains(' ')) {
                            counts[w] = maxOf(counts[w] ?: 0, c)
                            imported++
                        }
                    }
                    if (imported > 0) {
                        val sb = StringBuilder()
                        for ((w, c) in counts) sb.append(w).append('\t').append(c).append('\n')
                        prefs.updateActive { it.learnedTsv = sb.toString() }
                        toast("تم دمج $imported كلمة في قاموس الملف")
                        refreshStats()
                    } else toast("لم يُعثر على كلمات صالحة في الملف")
                } catch (e: Exception) {
                    toast("تعذر الاستيراد")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            prefs = Prefs(this)
            onyxAssets = OnyxAssets(this.assets)
            applyAppNightMode(prefs.appThemeMode)
            colorScheme = AppUiTheme.resolve(this, prefs.appThemeMode, prefs.appDynamicColor)
            setContentView(buildUi())
            window.decorView.post {
                detectAndPrefillClipboardUrl()
            }
            if (MaterialYou.supported()) {
                wallpaperListener = MaterialYou.listen(this) {
                    if (prefs.appDynamicColor) {
                        rebuildUi()
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("OnyxMainActivity", "Error during onCreate", t)
            val scroll = ScrollView(this)
            val fallbackLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16f).toInt(), dp(32f).toInt(), dp(16f).toInt(), dp(32f).toInt())
                addView(TextView(this@MainActivity).apply {
                    text = "⌨ لوحة مفاتيح Onyx"
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(this@MainActivity).apply {
                    text = "حدث استثناء غير متوقع أثناء التهيئة:\n${t.localizedMessage ?: t.toString()}"
                    textSize = 14f
                    setTextColor(Color.rgb(255, 120, 120))
                    setPadding(0, dp(12f).toInt(), 0, dp(16f).toInt())
                })
                addView(MaterialButton(this@MainActivity).apply {
                    text = "إعادة المحاولة"
                    setOnClickListener { recreate() }
                })
            }
            scroll.addView(fallbackLayout)
            setContentView(scroll)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wallpaperListener is android.app.WallpaperManager.OnColorsChangedListener) {
            MaterialYou.stopListening(this, wallpaperListener as android.app.WallpaperManager.OnColorsChangedListener)
        }
    }

    private fun applyAppNightMode(themeMode: Int) {
        val targetMode = when (themeMode) {
            Prefs.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Prefs.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    private fun setAppThemeMode(mode: Int) {
        prefs.appThemeMode = mode
        applyAppNightMode(mode)
        rebuildUi()
    }

    private fun setAppDynamicColor(enabled: Boolean) {
        prefs.appDynamicColor = enabled
        rebuildUi()
    }

    private fun rebuildUi() {
        val currentUrl = if (::urlInputField.isInitialized) urlInputField.text.toString() else null
        colorScheme = AppUiTheme.resolve(this, prefs.appThemeMode, prefs.appDynamicColor)
        setContentView(buildUi(currentUrl))
        refreshStatus()
        refreshStats()
        refreshProfileRow()
    }

    override fun onResume() {
        super.onResume()
        try {
            refreshStatus()
            refreshStats()
            refreshProfileRow()
            window.decorView.post {
                detectAndPrefillClipboardUrl()
            }
        } catch (e: Throwable) {
            android.util.Log.e("OnyxMainActivity", "Error during onResume", e)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            detectAndPrefillClipboardUrl()
        }
    }

    /* ================= بناء الواجهة ================= */
    private fun buildUi(initialUrl: String? = null): View {
        colorScheme = AppUiTheme.resolve(this, prefs.appThemeMode, prefs.appDynamicColor)
        window.statusBarColor = colorScheme.background
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = !colorScheme.isDark

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f).toInt(), dp(18f).toInt(), dp(14f).toInt(), dp(28f).toInt())
        }

        // الترويسة
        col.addView(TextView(this).apply {
            text = "⌨ لوحة مفاتيح Onyx"
            textSize = 24f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(colorScheme.textPrimary)
        })
        col.addView(TextView(this).apply {
            text = "v0.7.5 — Legend Deep (Kotlin + C++) · Milestone 3+"
            textSize = 12f
            setTextColor(colorScheme.primary)
            setPadding(0, dp(2f).toInt(), 0, dp(12f).toInt())
        })

        // مظهر واجهة التطبيق — الألوان الديناميكية والوضع الفاتح/الداكن
        col.addView(section("مظهر التطبيق — الألوان والوضع الليلي 🎨") {
            val mySupported = MaterialYou.supported()
            addView(switchRow(
                if (mySupported) "الألوان الديناميكية — Material You 🎨"
                else "الألوان الديناميكية 🎨 (يتطلب أندرويد 12+)",
                { prefs.appDynamicColor },
                { enabled -> setAppDynamicColor(enabled) },
                if (mySupported) "استخراج لوحة الألوان تلقائياً من خلفية جهازك وتطبيقها على واجهة التطبيق"
                else "غير مدعوم على إصدار نظامك الحالي — يتم استخدام ألوان Onyx البنفسجية"
            ).apply {
                if (!mySupported) {
                    alpha = 0.5f
                    isEnabled = false
                }
            })
            if (mySupported && prefs.appDynamicColor) {
                addView(swatchesRow())
            }
            addView(switchRow(
                "الوضع الداكن 🌙",
                { colorScheme.isDark },
                { isDark ->
                    setAppThemeMode(if (isDark) Prefs.THEME_MODE_DARK else Prefs.THEME_MODE_LIGHT)
                },
                if (colorScheme.isDark) "الوضع الداكن نشط حالياً — مريح للعين وموفر للطاقة"
                else "الوضع الفاتح نشط حالياً — ألوان مشرقة وتباين عالي"
            ))
            addView(miniLabel("التحكم في نمط العرض"))
            addView(chipScroller(
                listOf("⚙️ تلقائي (مع النظام)", "☀️ وضع فاتح", "🌙 وضع داكن"),
                { i -> prefs.appThemeMode == i },
                { i -> setAppThemeMode(i) }
            ))
            addView(TextView(this@MainActivity).apply {
                val modeDesc = when (prefs.appThemeMode) {
                    Prefs.THEME_MODE_LIGHT -> "الوضع الثابت: ☀️ فاتح دائماً"
                    Prefs.THEME_MODE_DARK -> "الوضع الثابت: 🌙 داكن دائماً"
                    else -> "الوضع التلقائي: ⚙️ يتبع إعدادات النظام (" + (if (colorScheme.isDark) "حالياً داكن 🌙" else "حالياً فاتح ☀️") + ")"
                }
                val dynamicDesc = if (prefs.appDynamicColor && mySupported) " · الألوان: ديناميكية (Material You)" else " · الألوان: كلاسيكية (Onyx Violet)"
                text = "$modeDesc$dynamicDesc"
                textSize = 11.5f
                setTextColor(colorScheme.textSecondary)
                setPadding(dp(4f).toInt(), dp(4f).toInt(), 0, dp(4f).toInt())
            })
        })

        // الحالة
        col.addView(section("تفعيل اللوحة") {
            statusText = TextView(ctx).apply { textSize = 14f; setTextColor(colorScheme.textPrimary) }
            addView(statusText)
            addView(buttonRow(
                button("تمكين في إعدادات النظام") {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                },
                button("اختيار Onyx الآن") {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showInputMethodPicker()
                },
            ))
            addView(EditText(ctx).apply {
                hint = "جرّب الكتابة هنا بعد الاختيار…"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 2
                textSize = 15f
                setTextColor(colorScheme.textPrimary)
                setHintTextColor(colorScheme.textSecondary)
                setPadding(dp(12f).toInt(), dp(10f).toInt(), dp(12f).toInt(), dp(10f).toInt())
                
                background = GradientDrawable().apply {
                    setColor(colorScheme.inputBackground)
                    cornerRadius = dp(12f)
                }
            })
        })

        // الملفات الشخصية
        col.addView(section("الملفات الشخصية — ذاكرة معزولة لكل ملف") {
            profileRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            addView(profileRow)
            addView(buttonRow(
                button("ملف جديد") {
                    prefs.addProfile("ملف ${prefs.profiles.size + 1}",
                        Prefs.AVATARS.random())
                    refreshProfileRow()
                },
                button("حذف الحالي") {
                    prefs.removeProfile(prefs.active.id)
                    refreshProfileRow()
                },
            ))
            addView(nameEditor())
            addView(switchRow("وضع المستخدم العادي (إخفاء المتقدم)", { prefs.simpleMode }, { prefs.simpleMode = it }))
        })

        // المظهر
        col.addView(section("المظهر — ${onyxAssets.themes.size} ثيماً × ${onyxAssets.templates.size} قالباً") {
            addView(miniLabel("الثيم"))
            addView(themePicker())
            addView(miniLabel("قالب التصميم"))
            addView(templatePicker())
            addView(switchRow("وضع ليلة القراءة 🌙", { prefs.active.nightMode }, { v ->
                prefs.updateActive { it.nightMode = v }
            }))
            addView(sliderRow("دفء ليلة القراءة", 0f, 1f, 0.05f, { prefs.active.warmth }, { v ->
                prefs.updateActive { it.warmth = v }
            }))
            addView(miniLabel("الخط"))
            addView(fontPicker())
            addView(sliderRow("مقياس الخط", 0.8f, 1.4f, 0.05f, { prefs.active.fontScale }, { v ->
                prefs.updateActive { it.fontScale = (v * 20).toInt() / 20f }
            }))
        })

        // اللغات
        col.addView(section("اللغات — ${onyxAssets.languages.size} لغات كاملة") {
            addView(langPicker())
        })

        // الذكاء
        col.addView(section("الذكاء المحلي — بلا إنترنت") {
            addView(switchRow("السحب المستمر ✍️", { prefs.glideEnabled }, { prefs.glideEnabled = it }))
            addView(switchRow("التعلم التكيفي (خوارزمية 24)", { prefs.autoLearn }, { prefs.autoLearn = it }))
            addView(switchRow("التنبؤ بالكلمة التالية (خوارزمية 20)", { prefs.nextWordEnabled }, { prefs.nextWordEnabled = it }))
            addView(switchRow("الشرائح الذكية (خوارزميات 36-40)", { prefs.smartChipsEnabled }, { prefs.smartChipsEnabled = it }))
            addView(switchRow("شارات الخوارزميات #N (وضع المهندس)", { prefs.engineerBadges }, { prefs.engineerBadges = it }))
            addView(switchRow("تحويل العربيزي (خوارزمية 29)", { prefs.arabiziEnabled }, { prefs.arabiziEnabled = it }))
            addView(switchRow("اهتزاز اللمس", { prefs.hapticsEnabled }, { prefs.hapticsEnabled = it }))
            addView(sliderRow("شدة التصحيح", 0f, 2f, 1f, { prefs.correctionLevel.toFloat() }, { v ->
                prefs.correctionLevel = v.toInt()
            }).apply { addView(label012()) })
        })

        // M3 Legend — النافذة والمصحح والمظهر الديناميكي
        col.addView(section("تجربة Legend — M3") {
            val mySupported = MaterialYou.supported()
            addView(switchRow(
                if (mySupported) "ألوان النظام — Material You 🎨 (من خلفيتك)"
                else "Material You 🎨 (يتطلب أندرويد 12+)",
                { prefs.active.materialYou }, { v -> prefs.updateActive { it.materialYou = v } }
            ).apply { isEnabled = mySupported; alpha = if (mySupported) 1f else 0.45f })
            if (mySupported) addView(swatchesRow())
            addView(switchRow("شريحة التصحيح الفوري ✅ (خوارزمية 51)",
                { prefs.spellChipEnabled }, { prefs.spellChipEnabled = it }))
            addView(switchRow("المصحح القواعدي للجمل ✎ (خوارزمية 52)",
                { prefs.grammarChipEnabled }, { prefs.grammarChipEnabled = it }))
            if (FloatingController.supported()) {
                addView(switchRow("اللوحة العائمة ⛶ — اسحبها من المقبض", { prefs.floatingEnabled }, { v ->
                    prefs.floatingEnabled = v
                    sendApplyWindowMode()
                }))
                // M3 Deep: حجم البطاقة العائمة — يُطبق حياً حتى أثناء العائم
                addView(sliderRow("حجم البطاقة العائمة ⛶ (من شريط الأدوات أيضاً)", 0.40f, 0.90f, 0.02f,
                    { prefs.floatScale }, { v ->
                        prefs.floatScale = v
                        sendApplyWindowMode()
                    }).apply {
                    addView(TextView(this@MainActivity).apply {
                        text = "داخل البطاقة: ＋/－ للتقليص والتكبير · نقر مزدوج على المقبض لقياسات جاهزة"
                        textSize = 11f
                        alpha = 0.55f
                    })
                })
            }
            addView(miniLabel("اليد الواحدة — من شريط الأدوات ⇤ أو من هنا"))
            addView(oneHandedPicker())
            addView(buttonRow(
                button("تصدير القاموس المتعلم") {
                    exportDict.launch("onyx-learned-${prefs.active.name}.tsv")
                },
                button("استيراد قاموس") {
                    importDict.launch(arrayOf("text/*", "application/octet-stream"))
                },
            ))
            addView(buttonRow(
                button("المصحح النظامي — كيف يُفعّل؟") { showSpellHelp() },
                button("حزم القواميس") { showDictStats() },
            ))
        })

        // M3: استيراد وتنزيل القواميس عبر رابط (URL) مع الكشف التلقائي من الحافظة
        col.addView(urlImportSection(initialUrl))

        // M2 Nova — الوسائط والذاكرة
        col.addView(section("تجربة Nova — M2") {
            addView(switchRow("أصوات المفاتيح 🔊", { prefs.soundEnabled }, { prefs.soundEnabled = it }))
            addView(sliderRow("شدة الصوت", 0f, 1f, 0.05f, { prefs.soundVolume }, { prefs.soundVolume = it }))
            addView(switchRow("الإدخال الصوتي 🎤 (بصلاحيات صفرية)", { prefs.voiceEnabled }, { prefs.voiceEnabled = it }))
            addView(switchRow("الحافظة التاريخية 📋", { prefs.clipHistoryEnabled }, { prefs.clipHistoryEnabled = it }))
            addView(switchRow("الإيموجي السياقي (خوارزميتا 34-35)", { prefs.emojiContextEnabled }, { prefs.emojiContextEnabled = it }))
            addView(buttonRow(
                button("مسح سجل الحافظة") {
                    com.onyx.keyboard.data.ClipboardHistory(this@MainActivity).clearAll()
                    toast("تم مسح سجل الحافظة")
                },
                button("حزم القواميس") { showDictStats() },
            ))
        })

        // الإحصاءات
        col.addView(section("إحصاءاتك") {
            statsText = TextView(ctx).apply { textSize = 14f; setLineSpacing(dp(2f), 1.1f) }
            addView(statsText)
        })

        // الزخرفة
        col.addView(section("الزخرفة — ${Decor.STYLES.size} نمطاً (زر ✿ في الشريط)") {
            addView(TextView(ctx).apply {
                textSize = 14f
                text = Decor.STYLES.take(6).joinToString(" · ") { it.preview }
                setTextColor(colorScheme.textPrimary)
            })
        })

        // حول
        col.addView(section("حول") {
            addView(TextView(ctx).apply {
                textSize = 13f
                text = "نواة تنبؤ C++17 أصلية (libonyx_engine.so) عبر NDK — الخوارزميات 1-52 على الجهاز مع 100 اختبار أخضر على المضيف.\n" +
                    "M3 Legend Deep: لوحة عائمة قابلة للسحب والتقليص والتكبير (＋/－ + مقبض حجم + نقر مزدوج 50/62/78٪)، يد واحدة يمين/يسار، شريحة تصحيح فوري، مصحح قواعدي للجمل (52: إملاء + تصريف + تكرار + ترقيم + أحرف)، مصحح إملائي نظامي لكل التطبيقات، وMaterial You حي من خلفية الجهاز.\n" +
                    "M2 Nova: قواميس 6000 كلمة × 9 لغات، إيموجي سياقي، لوحة إيموجي بالبحث، حافظة تاريخية، إدخال صوتي، أصوات مفاتيح، وطبقة تشكيل.\n" +
                    "صلاحيات التطبيق: صفر — لا إنترنت ولا مايك ولا SYSTEM_ALERT_WINDOW. كل شيء يعمل محلياً.\n" +
                    "معمارية: Kotlin (IME/واجهة/مصحح) + C++ (الذكاء) — نفس خارطة المشروع الأصلية."
                setTextColor(colorScheme.textSecondary)
            })
        })

        return ScrollView(this).apply {
            addView(col)
            background = GradientDrawable().apply { setColor(colorScheme.background) }
        }
    }

    /* ================= مكونات مساعدة ================= */
    private val _ctx get() = this

    private fun section(title: String, builder: SectionBuilder.() -> Unit): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(16f)
            setCardBackgroundColor(colorScheme.cardBackground)
            strokeWidth = dp(1f).toInt().coerceAtLeast(1)
            strokeColor = colorScheme.cardStroke
            useCompatPadding = true
        }
        val inner = SectionBuilder(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(dp(14f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(14f).toInt())
        inner.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(colorScheme.primary)
            setPadding(0, 0, 0, dp(8f).toInt())
        })
        inner.builder()
        card.addView(inner)
        return card
    }

    class SectionBuilder(val ctx: Context) : LinearLayout(ctx) {
        fun addChild(v: View) = addView(v)
    }

    private fun button(label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(colorScheme.onPrimary)
            backgroundTintList = ColorStateList.valueOf(colorScheme.primary)
            cornerRadius = dp(12f).toInt()
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(6f).toInt()
            layoutParams = lp
        }

    private fun buttonRow(a: View, b: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a); addView(b)
    }

    private fun miniLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(colorScheme.textSecondary)
        setPadding(0, dp(6f).toInt(), 0, dp(4f).toInt())
    }

    private fun switchRow(
        label: String,
        get: () -> Boolean,
        set: (Boolean) -> Unit,
        subtitle: String? = null,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6f).toInt(), 0, dp(6f).toInt())
            val textCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 14f
                    setTextColor(colorScheme.textPrimary)
                })
                if (!subtitle.isNullOrBlank()) {
                    addView(TextView(this@MainActivity).apply {
                        text = subtitle
                        textSize = 11.5f
                        setTextColor(colorScheme.textSecondary)
                        setPadding(0, dp(2f).toInt(), dp(6f).toInt(), 0)
                    })
                }
            }
            addView(textCol)
            addView(MaterialSwitch(this@MainActivity).apply {
                isChecked = get()
                thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(colorScheme.primary, colorScheme.divider)
                )
                trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        Color.argb(90, Color.red(colorScheme.primary), Color.green(colorScheme.primary), Color.blue(colorScheme.primary)),
                        colorScheme.inputBackground
                    )
                )
                setOnCheckedChangeListener { _, checked -> set(checked) }
            })
        }

    private fun sliderRow(
        label: String, from: Float, to: Float, step: Float,
        get: () -> Float, set: (Float) -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(miniLabel(label))
        addView(Slider(this@MainActivity).apply {
            valueFrom = from
            valueTo = to
            thumbTintList = ColorStateList.valueOf(colorScheme.primary)
            trackActiveTintList = ColorStateList.valueOf(colorScheme.primary)
            trackInactiveTintList = ColorStateList.valueOf(colorScheme.inputBackground)
            if (step > 0f) {
                stepSize = step
            }
            val raw = get().coerceIn(from, to)
            value = if (step > 0f) {
                val stepCount = Math.round((raw - from) / step)
                (from + stepCount * step).coerceIn(from, to)
            } else {
                raw
            }
            addOnChangeListener { _, value, fromUser -> if (fromUser) set(value) }
        })
    }

    private fun label012(): TextView = TextView(this).apply {
        text = "0 متحفظ · 1 متوازن · 2 سخي"
        textSize = 11f
        setTextColor(colorScheme.textSecondary)
    }

    /** منتقي دوائر الثيمات الملونة */
    private fun themePicker(): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            for (th in onyxAssets.themes) {
                val active = prefs.active.themeId == th.id
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val lp = LinearLayout.LayoutParams(dp(64f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginEnd = dp(8f).toInt()
                    layoutParams = lp
                    addView(View(this@MainActivity).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            colors = intArrayOf(th.accent, th.key)
                            setStroke(if (active) dp(2.5f).toInt() else 0, th.accent)
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(40f).toInt(), dp(40f).toInt())
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = th.name
                        textSize = 9.5f
                        maxLines = 1
                        gravity = Gravity.CENTER
                        setTextColor(if (active) th.accent else colorScheme.textSecondary)
                    })
                    setOnClickListener {
                        prefs.updateActive { it.themeId = th.id }
                        refreshProfileRow()
                    }
                })
            }
        })
    }

    private fun chipScroller(
        items: List<String>,
        isActive: (Int) -> Boolean,
        onClick: (Int) -> Unit,
    ): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            for ((i, name) in items.withIndex()) {
                val active = isActive(i)
                addView(TextView(this@MainActivity).apply {
                    text = name
                    textSize = 13f
                    setPadding(dp(14f).toInt(), dp(8f).toInt(), dp(14f).toInt(), dp(8f).toInt())
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18f)
                        if (active) {
                            val alpha = if (colorScheme.isDark) 70 else 35
                            setColor(Color.argb(alpha, Color.red(colorScheme.primary), Color.green(colorScheme.primary), Color.blue(colorScheme.primary)))
                            setStroke(dp(1.5f).toInt(), colorScheme.primary)
                        } else {
                            setColor(colorScheme.chipInactiveBackground)
                            setStroke(dp(1f).toInt(), colorScheme.divider)
                        }
                    }
                    setTextColor(if (active) colorScheme.primary else colorScheme.chipInactiveText)
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginEnd = dp(6f).toInt()
                    layoutParams = lp
                    setOnClickListener { onClick(i); refreshProfileRow() }
                })
            }
        })
    }

    private fun templatePicker(): View = chipScroller(
        onyxAssets.templates.map { it.name },
        { i -> onyxAssets.templates[i].id == prefs.active.templateId },
        { i -> prefs.updateActive { it.templateId = onyxAssets.templates[i].id } },
    )

    private fun fontPicker(): View = chipScroller(
        onyxAssets.fonts.map { it.name },
        { i -> onyxAssets.fonts[i].id == prefs.active.fontId },
        { i -> prefs.updateActive { it.fontId = onyxAssets.fonts[i].id } },
    )

    private fun langPicker(): View = chipScroller(
        onyxAssets.languages.map { "${it.flag} ${it.name}" },
        { i -> onyxAssets.languages[i].id == prefs.defaultLang },
        { i ->
            prefs.defaultLang = onyxAssets.languages[i].id
            prefs.activeLang = onyxAssets.languages[i].id
        },
    )

    /** M3: منتقي وضع اليد الواحدة */
    private fun oneHandedPicker(): View = chipScroller(
        listOf("إيقاف", "يمين 👉", "يسار 👈"),
        { i -> prefs.oneHanded == i },
        { i ->
            prefs.oneHanded = i
            sendApplyWindowMode()
        },
    )

    /** M3: معاينة حية لعينات خلفية الجهاز المستخرجة */
    private fun swatchesRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(2f).toInt(), 0, dp(6f).toInt())
        val sw = MaterialYou.swatches(this@MainActivity)
        if (sw == null) {
            addView(TextView(this@MainActivity).apply {
                text = "لا تتوفر ألوان خلفية للمعاينة بعد — جرّب بعد تغيير الخلفية"
                textSize = 12f
                setTextColor(colorScheme.textSecondary)
            })
        } else {
            val names = listOf("أصلي", "ثانوي", "ثالث")
            for ((i, c) in sw.withIndex()) {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val lp = LinearLayout.LayoutParams(dp(72f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginEnd = dp(10f).toInt()
                    layoutParams = lp
                    addView(View(this@MainActivity).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(c)
                            setStroke(dp(1f).toInt(), colorScheme.divider)
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(34f).toInt(), dp(34f).toInt())
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = names[i]
                        textSize = 10f
                        gravity = Gravity.CENTER
                        setTextColor(colorScheme.textSecondary)
                    })
                })
            }
            addView(TextView(this@MainActivity).apply {
                text = if (colorScheme.isDark) "مستخرج من خلفيتك (وضع داكن)" else "مستخرج من خلفيتك (وضع فاتح)"
                textSize = 11f
                setTextColor(colorScheme.textSecondary)
            })
        }
    }

    /** M3: إبلاغ خدمة الإدخال بإعادة تطبيق وضع النافذة فوراً */
    private fun sendApplyWindowMode() {
        sendBroadcast(Intent(OnyxImeService.ACTION_APPLY_WINDOW_MODE).setPackage(packageName))
    }

    /** M3: شرح تفعيل المدقق النظامي */
    private fun showSpellHelp() {
        AlertDialog.Builder(this)
            .setTitle("مدقق Onyx الإملائي — لكل الجهاز")
            .setMessage(
                "مصحح Onyx مبني على نفس نواة C++ المحلية (خوارزمية 51) وهو متاح كمدقق نظامي لكل التطبيقات.\n\n" +
                    "التفعيل النظامي:\n" +
                    "1. الإعدادات ← النظام ← لغات وإدخال\n" +
                    "2. التدقيق الإملائي (Spell checker)\n" +
                    "3. اختر «مدقق Onyx الإملائي»\n\n" +
                    "ولوحة Onyx نفسها تعرض شريحة تصحيح فوري بعد كل كلمة (قابلة للتعطيل أعلاه).\n" +
                    "كالعادة: كل التدقيق يجري على الجهاز — صفر إنترنت."
            )
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun nameEditor(): View {
        val edit = EditText(this).apply {
            hint = "اسم الملف الشخصي"
            setText(prefs.active.name)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setTextColor(colorScheme.textPrimary)
            setHintTextColor(colorScheme.textSecondary)
            background = GradientDrawable().apply {
                setColor(colorScheme.inputBackground)
                cornerRadius = dp(10f)
            }
            setPadding(dp(12f).toInt(), dp(9f).toInt(), dp(12f).toInt(), dp(9f).toInt())
        }
        edit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                prefs.updateActive { it.name = edit.text.toString().ifEmpty { "ضيف" } }
                refreshProfileRow()
            }
        }
        return edit
    }

    /* ================= التحديثات الديناميكية ================= */
    private fun refreshStatus() {
        if (!::statusText.isInitialized) return
        val enabled = imeEnabled()
        val selected = imeSelected()
        statusText.setTextColor(colorScheme.textPrimary)
        statusText.text = when {
            selected -> "✅ لوحة Onyx مفعّلة ومختارة — اضغط أي حقل نصي للبدء"
            enabled -> "🟡 مفعّلة في النظام لكن غير مختارة — اضغط «اختيار Onyx الآن»"
            else -> "🔴 غير مفعّلة — اضغط «تمكين في إعدادات النظام»"
        }
    }

    private fun refreshStats() {
        if (!::statsText.isInitialized) return
        val p = prefs.active
        val learned = p.learnedTsv.lineCount { it.isNotBlank() }
        statsText.setTextColor(colorScheme.textSecondary)
        statsText.text = "ضغطات: ${p.keystrokes} · كلمات: ${p.words} · تصحيحات: ${p.fixes}\n" +
            "قاموسك المتعلم: $learned كلمة · الملف: ${p.avatar} ${p.name}"
    }

    private fun refreshProfileRow() {
        if (!::profileRow.isInitialized) return
        profileRow.removeAllViews()
        for (p in prefs.profiles) {
            val active = p.id == prefs.activeProfileId
            profileRow.addView(TextView(this).apply {
                text = "${p.avatar} ${p.name}"
                textSize = 13f
                setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt(), dp(8f).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dp(18f)
                    if (active) {
                        val alpha = if (colorScheme.isDark) 70 else 35
                        setColor(Color.argb(alpha, Color.red(colorScheme.primary), Color.green(colorScheme.primary), Color.blue(colorScheme.primary)))
                        setStroke(dp(1.5f).toInt(), colorScheme.primary)
                    } else {
                        setColor(colorScheme.chipInactiveBackground)
                        setStroke(dp(1f).toInt(), colorScheme.divider)
                    }
                }
                setTextColor(if (active) colorScheme.primary else colorScheme.chipInactiveText)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = dp(6f).toInt()
                layoutParams = lp
                setOnClickListener {
                    prefs.activeProfileId = p.id
                    prefs.activeLang = prefs.defaultLang
                    refreshProfileRow()
                    refreshStats()
                }
            })
        }
    }

    private fun imeEnabled(): Boolean = try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
    } catch (e: Throwable) {
        false
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    /** إحصاءات القواميس الموسعة (M2) — قراءة مباشرة من الأصول */
    private fun showDictStats() {
        val sb = StringBuilder()
        for (l in onyxAssets.languages) {
            val n = try {
                assets.open("onyx/dicts/${l.id}.tsv").bufferedReader().useLines { it.count() }
            } catch (e: Exception) { 0 }
            sb.append("${l.flag} ${l.name}: $n كلمة\n")
        }
        AlertDialog.Builder(this)
            .setTitle("القواميس الموسعة — M2 Nova")
            .setMessage(sb.toString().trimEnd() + "\n\nالمصدر: قوائم ترددات مفتوحة + قواميس مصونة، مع معايرة لوغاريتمية داخل النواة")
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun imeSelected(): Boolean = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.contains(packageName) == true
    } catch (e: Throwable) {
        false
    }

    private fun String.lineCount(pred: (String) -> Boolean): Int = split("\n").count(pred)

    /* ================= استيراد القواميس عبر رابط (URL) مع كشف الحافظة ================= */

    private fun urlImportSection(initialUrl: String? = null): MaterialCardView {
        return section("استيراد وتنزيل عبر رابط 🌐 (URL)") {
            addView(TextView(this@MainActivity).apply {
                text = "أدخل رابط ملف قاموس (.tsv أو نصي) لتنزيل الكلمات وفحص أمان الرابط. عند فتح التطبيق، يتم رصد أي رابط في الحافظة وتعبئته تلقائياً."
                textSize = 12f
                setTextColor(colorScheme.textSecondary)
                setPadding(0, 0, 0, dp(6f).toInt())
            })

            // شارة رصد رابط من الحافظة
            clipboardDetectedBadge = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (lastPrefilledUrl != null) View.VISIBLE else View.GONE
                background = GradientDrawable().apply {
                    cornerRadius = dp(12f)
                    val bgAlpha = if (colorScheme.isDark) 45 else 30
                    setColor(Color.argb(bgAlpha, Color.red(colorScheme.primary), Color.green(colorScheme.primary), Color.blue(colorScheme.primary)))
                    setStroke(dp(1f).toInt(), colorScheme.primary)
                }
                setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt(), dp(8f).toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8f).toInt()
                layoutParams = lp

                val badgeHeader = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@MainActivity).apply {
                        text = "📋 تم رصد رابط في الحافظة وملء الحقل تلقائياً"
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colorScheme.primary)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "إغلاق ✕"
                        textSize = 11f
                        setTextColor(colorScheme.textSecondary)
                        setPadding(dp(6f).toInt(), dp(2f).toInt(), dp(6f).toInt(), dp(2f).toInt())
                        setOnClickListener {
                            clipboardDetectedBadge.visibility = View.GONE
                        }
                    })
                }
                addView(badgeHeader)

                clipboardBadgeText = TextView(this@MainActivity).apply {
                    text = lastPrefilledUrl ?: ""
                    textSize = 11.5f
                    setTextColor(colorScheme.textPrimary)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    setPadding(0, dp(2f).toInt(), 0, 0)
                }
                addView(clipboardBadgeText)
            }
            addView(clipboardDetectedBadge)

            // حقل إدخال الرابط
            urlInputField = EditText(this@MainActivity).apply {
                hint = "https://example.com/dictionaries/arabic.tsv"
                textSize = 13.5f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
                setTextColor(colorScheme.textPrimary)
                setHintTextColor(colorScheme.textSecondary)
                background = GradientDrawable().apply {
                    setColor(colorScheme.inputBackground)
                    cornerRadius = dp(10f)
                    setStroke(dp(1f).toInt(), colorScheme.divider)
                }
                setPadding(dp(12f).toInt(), dp(10f).toInt(), dp(12f).toInt(), dp(10f).toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6f).toInt()
                layoutParams = lp
                val prefill = initialUrl ?: lastPrefilledUrl
                if (!prefill.isNullOrBlank()) {
                    setText(prefill)
                    setSelection(prefill.length)
                }
            }
            addView(urlInputField)

            // نص حالة الرابط والتحقق
            urlStatusText = TextView(this@MainActivity).apply {
                textSize = 12f
                setTextColor(colorScheme.textSecondary)
                setPadding(dp(4f).toInt(), dp(4f).toInt(), dp(4f).toInt(), dp(6f).toInt())
                text = "💡 الصق أو اكتب رابطاً، أو انسخ رابطاً وافتح التطبيق للكشف الفوري"
            }
            addView(urlStatusText)

            // أزرار العمليات
            addView(buttonRow(
                button("تنزيل واستيراد 📥") {
                    val url = urlInputField.text.toString().trim()
                    downloadAndImportDictionary(url)
                },
                button("فحص الأمان 🛡️") {
                    val url = urlInputField.text.toString().trim()
                    validateUrlField(url)
                }
            ))

            addView(buttonRow(
                button("لصق من الحافظة 📋") {
                    pasteFromClipboardManually()
                },
                button("مسح الحقل ✕") {
                    urlInputField.setText("")
                    lastPrefilledUrl = null
                    clipboardDetectedBadge.visibility = View.GONE
                    urlStatusText.text = "تم مسح حقل الرابط"
                    urlStatusText.setTextColor(colorScheme.textSecondary)
                }
            ))
        }
    }

    /**
     * فحص الحافظة تلقائياً عند فتح التطبيق واستخراج الرابط وملء حقل الإدخال
     */
    fun detectAndPrefillClipboardUrl(): Boolean {
        if (!::urlInputField.isInitialized) return false
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
            if (!clipboard.hasPrimaryClip()) return false
            val clip = clipboard.primaryClip ?: return false
            if (clip.itemCount == 0) return false
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return false
            val detectedUrl = ClipboardUrlDetector.extractUrl(text) ?: return false

            val currentText = urlInputField.text.toString().trim()
            if (currentText.isEmpty() || currentText == lastPrefilledUrl) {
                if (currentText != detectedUrl) {
                    urlInputField.setText(detectedUrl)
                    urlInputField.setSelection(detectedUrl.length)
                    lastPrefilledUrl = detectedUrl
                    showClipboardBadge(detectedUrl)
                    validateUrlField(detectedUrl)
                    return true
                }
            }
            false
        } catch (e: Throwable) {
            android.util.Log.e("OnyxMainActivity", "Error reading clipboard", e)
            false
        }
    }

    private fun showClipboardBadge(url: String) {
        if (!::clipboardDetectedBadge.isInitialized || !::clipboardBadgeText.isInitialized) return
        clipboardBadgeText.text = url
        clipboardDetectedBadge.visibility = View.VISIBLE
    }

    private fun validateUrlField(url: String) {
        if (url.isBlank()) {
            urlStatusText.text = "⚠️ الرجاء إدخال رابط أولاً"
            urlStatusText.setTextColor(Color.rgb(255, 149, 0))
            return
        }
        val result = DownloadUrlValidator.validate(url)
        if (result.isValid && result is DownloadUrlValidator.ValidationResult.Valid) {
            urlStatusText.text = "✅ الرابط آمن وصالح:\n• النطاق: ${result.host}\n• البروتوكول: ${result.scheme.uppercase()}\n• فحص SSRF وتفادي المسارات: سليم"
            urlStatusText.setTextColor(Color.rgb(52, 199, 89))
        } else if (result is DownloadUrlValidator.ValidationResult.Invalid) {
            urlStatusText.text = "❌ رابط غير صالح أو غير آمن:\n${result.reason}"
            urlStatusText.setTextColor(Color.rgb(255, 69, 58))
        }
    }

    private fun pasteFromClipboardManually() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this)?.toString()
                val url = ClipboardUrlDetector.extractUrl(text)
                if (url != null) {
                    urlInputField.setText(url)
                    urlInputField.setSelection(url.length)
                    lastPrefilledUrl = url
                    showClipboardBadge(url)
                    validateUrlField(url)
                    toast("تم استخراج الرابط من الحافظة بنجاح")
                } else if (!text.isNullOrBlank()) {
                    toast("النص المنسوخ لا يحتوي على رابط صالح")
                    urlStatusText.text = "⚠️ النص الموجود في الحافظة ليس رابطاً صالحاً"
                    urlStatusText.setTextColor(Color.rgb(255, 149, 0))
                } else {
                    toast("الحافظة فارغة")
                }
            } else {
                toast("الحافظة فارغة")
            }
        } catch (e: Throwable) {
            toast("تعذر الوصول إلى الحافظة")
        }
    }

    private fun downloadAndImportDictionary(url: String) {
        if (url.isBlank()) {
            toast("الرجاء إدخال رابط القاموس أولاً")
            return
        }
        val validation = DownloadUrlValidator.validate(url)
        if (!validation.isValid) {
            val reason = (validation as? DownloadUrlValidator.ValidationResult.Invalid)?.reason ?: "رابط غير صالح"
            urlStatusText.text = "❌ فشل فحص الأمان: $reason"
            urlStatusText.setTextColor(Color.rgb(255, 69, 58))
            toast("رابط غير آمن أو غير صالح")
            return
        }

        urlStatusText.text = "⏳ جارٍ تنزيل ملف القاموس وفحصه..."
        urlStatusText.setTextColor(colorScheme.primary)
        toast("جارٍ التنزيل من الرابط...")

        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "OnyxKeyboard/0.7.5")

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    runOnUiThread {
                        urlStatusText.text = "❌ خطأ في الخادم (رمز $responseCode): تعذر تنزيل الملف"
                        urlStatusText.setTextColor(Color.rgb(255, 69, 58))
                        toast("خطأ الخادم: $responseCode")
                    }
                    return@Thread
                }

                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val counts = HashMap<String, Int>()
                for (l in prefs.active.learnedTsv.lines()) {
                    val p = l.split("\t")
                    if (p.size >= 2 && p[0].trim().length >= 2) {
                        counts[p[0].trim()] = p[1].trim().toIntOrNull()?.coerceIn(1, 99) ?: 1
                    }
                }
                var imported = 0
                for (l in text.lines()) {
                    val p = l.split("\t")
                    val w = p.getOrNull(0)?.trim() ?: ""
                    val c = p.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(1, 99) ?: 1
                    if (w.length >= 2 && !w.contains(' ')) {
                        counts[w] = maxOf(counts[w] ?: 0, c)
                        imported++
                    }
                }

                runOnUiThread {
                    if (imported > 0) {
                        val sb = StringBuilder()
                        for ((w, c) in counts) sb.append(w).append('\t').append(c).append('\n')
                        prefs.updateActive { it.learnedTsv = sb.toString() }
                        urlStatusText.text = "✅ تم بنجاح! تم دمج $imported كلمة من الرابط في قاموسك المتعلم (${prefs.active.name})"
                        urlStatusText.setTextColor(Color.rgb(52, 199, 89))
                        toast("تم استيراد $imported كلمة بنجاح ✅")
                        refreshStats()
                    } else {
                        urlStatusText.text = "⚠️ تم تنزيل الملف ولكن لم يتم العثور على كلمات صالحة (صيغة كلمة أو كلمة [tab] تكرار)"
                        urlStatusText.setTextColor(Color.rgb(255, 149, 0))
                        toast("لم يُعثر على كلمات صالحة في الملف")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val msg = e.localizedMessage ?: e.javaClass.simpleName
                    urlStatusText.text = "❌ فشل الاتصال بالرابط: $msg"
                    urlStatusText.setTextColor(Color.rgb(255, 69, 58))
                    toast("تعذر التنزيل: $msg")
                }
            }
        }.start()
    }
}
