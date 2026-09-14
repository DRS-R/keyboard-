package com.onyx.keyboard.ui

import android.content.Context
import android.content.Intent
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.onyx.keyboard.data.Prefs
import com.onyx.keyboard.ime.Decor
import com.onyx.keyboard.ime.FloatingController
import com.onyx.keyboard.ime.OnyxImeService
import com.onyx.keyboard.model.OnyxAssets
import com.onyx.keyboard.ui.MaterialYou

/**
 * MainActivity — واجهة الإعدادات الكاملة (Material 3 برمجي)
 * الحالة + الملفات الشخصية + الثيمات/القوالب/الخطوط + ليلة القراءة + اللغات
 * + مفاتيح الذكاء + الإحصاءات + حول التطبيق
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var onyxAssets: OnyxAssets
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = v * density

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var profileRow: LinearLayout

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
            setContentView(buildUi())
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

    override fun onResume() {
        super.onResume()
        try {
            refreshStatus()
            refreshStats()
            refreshProfileRow()
        } catch (e: Throwable) {
            android.util.Log.e("OnyxMainActivity", "Error during onResume", e)
        }
    }

    /* ================= بناء الواجهة ================= */
    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f).toInt(), dp(18f).toInt(), dp(14f).toInt(), dp(28f).toInt())
        }

        // الترويسة
        col.addView(TextView(this).apply {
            text = "⌨ لوحة مفاتيح Onyx"
            textSize = 24f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(Color.rgb(238, 236, 248))
        })
        col.addView(TextView(this).apply {
            text = "v0.7.5 — Legend Deep (Kotlin + C++) · Milestone 3+"
            textSize = 12f
            setTextColor(Color.rgb(167, 139, 250))
            setPadding(0, dp(2f).toInt(), 0, dp(12f).toInt())
        })

        // الحالة
        col.addView(section("تفعيل اللوحة") {
            statusText = TextView(ctx).apply { textSize = 14f }
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
                setPadding(dp(12f).toInt(), dp(10f).toInt(), dp(12f).toInt(), dp(10f).toInt())
                
                background = GradientDrawable().apply {
                    setColor(Color.argb(30, 128, 128, 128))
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
                setTextColor(Color.argb(210, 255, 255, 255))
            })
        })

        return ScrollView(this).apply {
            addView(col)
            background = GradientDrawable().apply { setColor(Color.parseColor("#101017")) }
        }
    }

    /* ================= مكونات مساعدة ================= */
    private val _ctx get() = this

    private fun section(title: String, builder: SectionBuilder.() -> Unit): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(16f)
            setCardBackgroundColor(Color.parseColor("#16161f"))
            strokeWidth = 1
            strokeColor = Color.argb(40, 167, 139, 250)
            useCompatPadding = true
        }
        val inner = SectionBuilder(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(dp(14f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(14f).toInt())
        inner.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(167, 139, 250))
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
        alpha = 0.75f
        setPadding(0, dp(6f).toInt(), 0, dp(4f).toInt())
    }

    private fun switchRow(label: String, get: () -> Boolean, set: (Boolean) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6f).toInt(), 0, dp(6f).toInt())
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(MaterialSwitch(this@MainActivity).apply {
                isChecked = get()
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
        alpha = 0.6f
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
                        setTextColor(if (active) th.accent else Color.argb(190, 255, 255, 255))
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
                        setColor(if (active) Color.argb(70, 167, 139, 250) else Color.argb(28, 128, 128, 128))
                        setStroke(if (active) dp(1.5f).toInt() else 0, Color.rgb(167, 139, 250))
                    }
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
                alpha = 0.6f
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
                            setStroke(dp(1f).toInt(), Color.argb(60, 255, 255, 255))
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(34f).toInt(), dp(34f).toInt())
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = names[i]
                        textSize = 10f
                        gravity = Gravity.CENTER
                        alpha = 0.7f
                    })
                })
            }
            addView(TextView(this@MainActivity).apply {
                text = "ثيم اللوحة يتغير مع خلفيتك — فاتح وليلاً"
                textSize = 11f
                alpha = 0.55f
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
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 128, 128, 128))
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
        val enabled = imeEnabled()
        val selected = imeSelected()
        statusText.text = when {
            selected -> "✅ لوحة Onyx مفعّلة ومختارة — اضغط أي حقل نصي للبدء"
            enabled -> "🟡 مفعّلة في النظام لكن غير مختارة — اضغط «اختيار Onyx الآن»"
            else -> "🔴 غير مفعّلة — اضغط «تمكين في إعدادات النظام»"
        }
    }

    private fun refreshStats() {
        val p = prefs.active
        val learned = p.learnedTsv.lineCount { it.isNotBlank() }
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
                    setColor(if (active) Color.argb(70, 167, 139, 250) else Color.argb(28, 128, 128, 128))
                    setStroke(if (active) dp(1.5f).toInt() else 0, Color.rgb(167, 139, 250))
                }
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
}
