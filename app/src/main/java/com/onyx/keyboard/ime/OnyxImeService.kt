package com.onyx.keyboard.ime

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.onyx.keyboard.data.Prefs
import com.onyx.keyboard.engine.OnyxEngine
import com.onyx.keyboard.model.KeyDef
import com.onyx.keyboard.model.KbTemplate
import com.onyx.keyboard.model.KbTheme
import com.onyx.keyboard.model.OnyxAssets
import com.onyx.keyboard.ui.MaterialYou
import com.onyx.keyboard.ui.VoiceBridgeActivity
import java.util.Calendar

/**
 * OnyxImeService — خدمة الإدخال الحقيقية (InputMethodService)
 * M3 Legend: لوحة عائمة قابلة للسحب + يد واحدة + شريحة تصحيح فوري (51)
 * + Material You حي من خلفية النظام — كله بصلاحيات صفرية.
 */
class OnyxImeService : InputMethodService(), OnyxKeyboardView.Listener, SuggestionBar.Listener,
    EmojiPanelView.Listener, ClipboardPanelView.Listener {

    private lateinit var prefs: Prefs
    private lateinit var onyxAssets: OnyxAssets
    private lateinit var soundKit: SoundKit
    private lateinit var clipHistory: com.onyx.keyboard.data.ClipboardHistory
    private lateinit var bar: SuggestionBar
    private lateinit var emojiPanel: EmojiPanelView
    private lateinit var clipPanel: ClipboardPanelView
    private var kb: OnyxKeyboardView? = null
    private var container: LinearLayout? = null
    private var panelHost: FrameLayout? = null

    private var langIdx = 0
    private var layer = LAYER_LETTERS
    private var shift = false
    private val composing = StringBuilder()
    private var prevWord = ""
    private var prev2 = ""
    private var lastSpaceAt = 0L
    private var sessionStart = 0L

    private var keystrokes = 0L
    private var fixes = 0
    private var words = 0

    private var theme: KbTheme? = null
    private var template: KbTemplate? = null

    /* ================= M3 Legend ================= */
    private lateinit var rootFrame: FrameLayout
    private var floatingHeader: LinearLayout? = null
    private var edgeStrip: TextView? = null
    private val floatingCtl by lazy { FloatingController(this, prefs) }
    private var spellWrong: String? = null
    private var spellFixes: List<String> = emptyList()
    /** M3 Deep: ملاحظات المصحح القواعدي للجملة (52) */
    private var grammarIssues: List<OnyxEngine.GrammarIssue> = emptyList()
    private var wallpaperListener: android.app.WallpaperManager.OnColorsChangedListener? = null

    /** بث داخلي: الإعدادات غيّرت وضع النافذة — طبّق حياً */
    private var windowModeReceiver: BroadcastReceiver? = null

    /* ================= M2: الإدخال الصوتي — مستقبل بث محلي ================= */
    private var voiceReceiver: BroadcastReceiver? = null
    private val voiceTimeout = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        onyxAssets = OnyxAssets(this.assets)
        langIdx = onyxAssets.langIndex(prefs.activeLang)
        OnyxEngine.loadAll(this, prefs.active.learnedTsv, prefs.correctionLevel - 1)
        soundKit = SoundKit(this).apply {
            enabled = prefs.soundEnabled
            volume = prefs.soundVolume
        }
        clipHistory = com.onyx.keyboard.data.ClipboardHistory(this)
        // M2: التقاط النسخ أثناء حياة الخدمة (قراءة الحافظة مسموحة للوحة النشطة)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.addPrimaryClipChangedListener {
            if (!prefs.clipHistoryEnabled) return@addPrimaryClipChangedListener
            val text = cm.primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0)?.text?.toString() else null }
            if (!text.isNullOrEmpty()) clipHistory.onClip(text)
        }
        // M3: تطبيق وضع النافذة حياً عند تغييره من الإعدادات
        windowModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ACTION_APPLY_WINDOW_MODE) applyWindowMode()
            }
        }
        ContextCompat.registerReceiver(
            this, windowModeReceiver!!, IntentFilter(ACTION_APPLY_WINDOW_MODE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // M3: Material You — تحديث حي عند تغيير خلفية النظام
        wallpaperListener = MaterialYou.listen(this) {
            if (prefs.active.materialYou) refreshFromPrefs()
        }
    }

    override fun onCreateInputView(): View {
        val ctx = this
        // M3: جذر FrameLayout يسمح باليد الواحدة (تقلص وتمحيز) داخل النافذة الكاملة
        rootFrame = FrameLayout(ctx)
        container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        // M3: مقبض الوضع العائم — أعلى الحاوية، يظهر فقط عند التفعيل
        floatingHeader = floatingCtl.buildHeader {
            prefs.floatingEnabled = false
            applyWindowMode()
            soundKit.play(SoundKit.Sound.TAP)
        }.apply { visibility = View.GONE }
        bar = SuggestionBar(ctx).apply {
            listener = this@OnyxImeService
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52f).toInt()
            )
        }
        panelHost = FrameLayout(ctx).apply {
            visibility = View.GONE
            // ارتفاع ثابت: أبناء FrameLayout (اللوحات) تعتمد weight داخلياً — WRAP_CONTENT سيَهويها
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(248f).toInt()
            )
        }
        emojiPanel = EmojiPanelView(ctx).apply {
            listener = this@OnyxImeService
            assets = onyxAssets
            recentsProvider = { prefs.emojiRecents() }
            visibility = View.GONE
        }
        clipPanel = ClipboardPanelView(ctx).apply {
            listener = this@OnyxImeService
            history = clipHistory
            visibility = View.GONE
        }
        panelHost?.addView(emojiPanel)
        panelHost?.addView(clipPanel)
        kb = OnyxKeyboardView(ctx).apply {
            listener = this@OnyxImeService
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container?.addView(floatingHeader)
        container?.addView(bar)
        container?.addView(panelHost)
        container?.addView(kb)
        rootFrame.addView(
            container!!,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        // M3: شريط العودة من وضع اليد الواحدة — على الجهة الفارغة
        edgeStrip = TextView(ctx).apply {
            text = "⤶"
            textSize = 19f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setOnClickListener {
                prefs.oneHanded = 0
                applyWindowMode()
                soundKit.play(SoundKit.Sound.TAP)
            }
        }
        rootFrame.addView(
            edgeStrip,
            FrameLayout.LayoutParams(dp(26f).toInt(), FrameLayout.LayoutParams.MATCH_PARENT)
        )
        refreshFromPrefs()
        applyWindowMode()
        setRowsForState()
        updateSuggestions()
        return rootFrame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // قد يغيّر المستخدم الإعدادات/الملف من التطبيق
        OnyxEngine.nativeSetLearnedTsv(prefs.active.learnedTsv)
        OnyxEngine.nativeSetConfig(prefs.correctionLevel - 1)
        langIdx = onyxAssets.langIndex(prefs.activeLang)
        composing.clear()
        soundKit.enabled = prefs.soundEnabled
        soundKit.volume = prefs.soundVolume
        clearSpellChip()
        clearGrammarChip()
        refreshFromPrefs()
        applyWindowMode()
        setRowsForState()
        updateSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        persistStats()
        unregisterVoice()
        windowModeReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) { /* غير مسجل */ } }
        windowModeReceiver = null
        MaterialYou.stopListening(this, wallpaperListener)
        wallpaperListener = null
        soundKit.release()
        super.onDestroy()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /* ================= المظهر ================= */
    private fun refreshFromPrefs() {
        if (!::bar.isInitialized || !::emojiPanel.isInitialized || !::clipPanel.isInitialized) return
        val p = prefs.active
        var th = onyxAssets.themeById(p.themeId)
        // M3: Material You — توليد الثيم من خلفية النظام (API 31+)
        if (p.materialYou) {
            MaterialYou.buildTheme(this)?.let { generated -> th = generated }
        }
        val tpl = onyxAssets.templateById(p.templateId)
        theme = th
        template = tpl
        val night = if (p.nightMode && !p.materialYou) p.warmth else 0f
        kb?.theme = th
        kb?.template = tpl
        kb?.fontFamily = onyxAssets.fontById(p.fontId).families
        kb?.fontScale = p.fontScale
        kb?.nightWarmth = night
        kb?.haptics = prefs.hapticsEnabled
        kb?.glideEnabled = prefs.glideEnabled
        bar?.applyTheme(th, night)
        emojiPanel.applyTheme(th)
        clipPanel.applyTheme(th)
        // M3: تلوين شريط اليد الواحدة بالثيم
        edgeStrip?.apply {
            background = GradientDrawable().apply {
                setColor(th.keyAlt)
                cornerRadius = dp(14f)
            }
            setTextColor(th.accent)
        }
    }

    /* ================= M3: وضع النافذة (عائم / يد واحدة) ================= */
    private fun applyWindowMode() {
        if (!::rootFrame.isInitialized) return
        val floating = FloatingController.supported() && prefs.floatingEnabled
        if (floating) {
            // M3 Deep: إن تغيّر الحجم من الإعدادات أثناء العائم — تحديث حي للعرض
            if (!floatingCtl.active) floatingCtl.enter(rootFrame) else floatingCtl.refreshWidth()
        } else if (floatingCtl.active) {
            floatingCtl.exit(rootFrame)
        }
        floatingHeader?.visibility = if (floating) View.VISIBLE else View.GONE
        // اليد الواحدة لا تعمل مع العائم
        val oh = if (floating) 0 else prefs.oneHanded
        val dm = resources.displayMetrics
        val lp = container?.layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            when (oh) {
                1 -> { lp.width = (dm.widthPixels * 0.78f).toInt(); lp.gravity = Gravity.END }
                2 -> { lp.width = (dm.widthPixels * 0.78f).toInt(); lp.gravity = Gravity.START }
                else -> {
                    lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                    lp.gravity = Gravity.BOTTOM
                }
            }
            container?.layoutParams = lp
        }
        edgeStrip?.visibility = if (oh > 0) View.VISIBLE else View.GONE
        val esp = edgeStrip?.layoutParams as? FrameLayout.LayoutParams
        if (esp != null) {
            esp.gravity = if (oh == 1) Gravity.START else Gravity.END or Gravity.CENTER_VERTICAL
            if (oh == 1) esp.gravity = esp.gravity or Gravity.CENTER_VERTICAL
            edgeStrip?.layoutParams = esp
        }
        bar.setWindowState(floating, oh)
        bar.setSpellFix(spellWrong, spellFixes)
    }

    /* ================= الطبقات ================= */
    private fun setRowsForState() {
        val lang = onyxAssets.languages[langIdx]
        val rtlLetter = lang.id == "ar" || lang.id == "ur" || lang.id == "fa"
        val base: List<List<KeyDef>> = when (layer) {
            LAYER_LETTERS -> onyxAssets.layouts[lang.id] ?: onyxAssets.layouts["en"]!!
            LAYER_SYMBOLS -> onyxAssets.layouts["symbols"]!!
            else -> onyxAssets.layouts["tashkeel"]!!
        }
        val shifted = base.map { row -> row.map { applyShift(it) } }
        kb?.setRows(shifted + listOf(bottomRow()), mirrorRTL = layer == LAYER_LETTERS && rtlLetter)
        // M2: شريحة التشكيل — للغات RTL فقط
        bar.setTashkeelState(show = rtlLetter, active = layer == LAYER_TASHKEEL)
        // تسمية مفتاح الطبقة
        updateSuggestions()
    }

    private fun applyShift(k: KeyDef): KeyDef {
        if (!shift) return k
        val c = k.char ?: return k
        val up = c.uppercase()
        return if (up != c) k.copy(char = up) else k
    }

    private fun bottomRow(): List<KeyDef> = listOf(
        KeyDef(action = "shift", label = "⇧", w = 1.25f),
        KeyDef(action = "layer", label = if (layer == LAYER_LETTERS) "#+=" else "أ‌ب‌ج", w = 1.25f),
        KeyDef(action = "lang", label = onyxAssets.languages[langIdx].flag, w = 1f),
        KeyDef(action = "space", w = 4.2f),
        KeyDef(action = "period", char = ".", w = 1f),
        KeyDef(action = "backspace", label = "⌫", w = 1.4f),
        KeyDef(action = "enter", label = "⏎", w = 1.4f, accent = true),
    )

    /* ================= مستمع اللوحة ================= */
    override fun onChar(c: String) {
        val ic = currentInputConnection ?: return
        if (sessionStart == 0L) sessionStart = System.currentTimeMillis()
        keystrokes++
        // M3: بدأ إدخال كلمة جديدة — تُزال شريحة التصحيح القديمة
        if (composing.isEmpty() && (spellWrong != null || grammarIssues.isNotEmpty())) {
            clearSpellChip()
            clearGrammarChip()
        }
        composing.append(c)
        ic.setComposingText(composing.toString(), 1)
        soundKit.play(SoundKit.Sound.TAP)
        updateSuggestions()
    }

    override fun onAction(action: String, key: KeyDef?) {
        when (action) {
            "shift" -> { shift = !shift; setRowsForState() }
            "layer" -> {
                layer = when (layer) {
                    LAYER_LETTERS -> LAYER_SYMBOLS
                    LAYER_SYMBOLS -> LAYER_LETTERS
                    else -> LAYER_LETTERS
                }
                shift = false
                setRowsForState()
            }
            "lang" -> {
                langIdx = (langIdx + 1) % onyxAssets.languages.size
                prefs.activeLang = onyxAssets.languages[langIdx].id
                layer = LAYER_LETTERS
                shift = false
                setRowsForState()
            }
            "space" -> handleSpace()
            "period" -> {
                currentInputConnection?.commitText(".", 1)
                soundKit.play(SoundKit.Sound.TAP)
                afterSentenceEnd()
            }
            "backspace" -> handleBackspace()
            "enter" -> handleEnter()
        }
    }

    override fun onAlt(c: String) {
        val ic = currentInputConnection ?: return
        // البدائل تُدخل مباشرة (مثل ا→آ) فوق أو داخل الكلمة
        if (composing.isNotEmpty()) {
            composing.append(c)
            ic.setComposingText(composing.toString(), 1)
            updateSuggestions()
        } else {
            ic.commitText(c, 1)
        }
        soundKit.play(SoundKit.Sound.TAP)
    }

    override fun onGlide(seq: List<String>) {
        val ic = currentInputConnection ?: return
        if (seq.size < 2) return
        val lang = onyxAssets.languages[langIdx].id
        val word = OnyxEngine.nativeGlide(seq.toTypedArray(), lang, prevWord)
        if (word.isNotEmpty()) {
            ic.commitText("$word ", 1)
            prev2 = prevWord
            prevWord = word
            words++
            soundKit.play(SoundKit.Sound.SPACE)
            updateSuggestions()
        }
    }

    /* ================= اقتراحات وشرائح ================= */
    override fun onSuggestion(s: OnyxEngine.Sug) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            ic.setComposingText(s.word, 1)
            ic.finishComposingText()
        } else {
            ic.commitText(s.word, 1)
        }
        if (s.type == "fix") fixes++
        learnWord(s.word)
        prev2 = prevWord
        prevWord = s.word
        words++
        composing.clear()
        clearSpellChip()  // M3: كلمة جديدة أُدرجت — الشرائح القديمة تُغلق
        clearGrammarChip()
        ic.commitText(" ", 1)
        soundKit.play(SoundKit.Sound.TAP)
        afterSentenceEnd()
        // M3 Deep: بلا شريحة إملائية — افحص الجملة قواعدياً
        maybeGrammarChip()
        updateSuggestions()
    }

    override fun onChipText(text: String) {
        currentInputConnection?.commitText(text, 1)
        // M2: أي إدراج إيموجي (شريحة سياقية أو بحث) يصبح في الأحدث
        if (text.length <= 8 && looksEmoji(text)) prefs.pushEmojiRecent(text)
        soundKit.play(SoundKit.Sound.TAP)
        updateSuggestions()
    }

    private fun looksEmoji(s: String): Boolean = s.codePoints().anyMatch {
        (it in 0x1F000..0x1FAFF) || (it in 0x2600..0x27BF) || it == 0x2B50 || it == 0x2764
    }

    private fun learnWord(word: String) {
        if (!prefs.autoLearn) return
        val w = word.trim()
        if (w.length < 2) return
        OnyxEngine.nativeLearnWord(w)
        prefs.updateActive { it.learnedTsv = OnyxEngine.nativeGetLearnedTsv() }
    }

    private fun wpmEstimate(): Int {
        if (sessionStart == 0L || words == 0) return 30
        val mins = (System.currentTimeMillis() - sessionStart).coerceAtLeast(15000L) / 60000.0
        return (words / mins).toInt().coerceIn(5, 120)
    }

    private fun updateSuggestions() {
        val lang = onyxAssets.languages[langIdx].id
        if (composing.isEmpty()) {
            val chips = ArrayList<Pair<String, String>>()
            // M2: إيموجي سياقي من النص الأخير (خوارزميتا 34-35)
            if (prefs.smartChipsEnabled && prefs.emojiContextEnabled) {
                val recent = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString() ?: ""
                for (e in OnyxEngine.nativeEmojiSuggest(recent, lang).take(3)) {
                    chips.add(e to e)
                }
            }
            if (prefs.smartChipsEnabled) chips.addAll(smartChips())
            if (prefs.nextWordEnabled) {
                val next = OnyxEngine.parseSug(OnyxEngine.nativeNextWord(prevWord, prev2, lang))
                bar.showNextAndChips(next, chips.distinctBy { it.first })
            } else {
                bar.showIdle(chips.distinctBy { it.first })
            }
        } else {
            val sug = OnyxEngine.parseSug(
                OnyxEngine.nativeSuggest(composing.toString(), prevWord, lang, wpmEstimate(), "")
            )
            var hint: String? = null
            if (prefs.arabiziEnabled && lang == "ar" && OnyxEngine.nativeDetectScript(composing.toString()) == 2) {
                val ar = OnyxEngine.nativeArabizi(composing.toString())
                if (ar.length >= 2) hint = ar
            }
            // M2: إيموجي سياقي للكلمة الجارية (35)
            val ctxEmoji = if (prefs.emojiContextEnabled)
                OnyxEngine.nativeEmojiSuggest(composing.toString(), lang).take(3)
            else emptyList()
            bar.showSuggestions(sug, prefs.engineerBadges, hint, ctxEmoji)
        }
    }

    /** الشرائح الذكية: حافظة + استخراجات (36-40) + ترحيب (49) */
    private fun smartChips(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0)?.text?.toString() else null }
        if (!clip.isNullOrEmpty() && clip.length <= 220) {
            val ex = OnyxEngine.parseExtract(OnyxEngine.nativeExtract(clip))
            ex.url?.let { out.add("🔗 $it" to it) }
            ex.phone?.let { out.add("📞 $it" to it) }
            ex.email?.let { out.add("✉️ $it" to it) }
            ex.otp?.let { out.add("🔢 $it" to it) }
            ex.date?.let { out.add("📅 $it" to it) }
            out.add("📋 لصق" to clip)
        }
        out.add(Decor.greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) to
            Decor.greetingForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        return out.distinctBy { it.first }
    }

    /* ================= M3: المصحح اللغوي الفوري (51) ================= */
    private fun clearSpellChip() {
        spellWrong = null
        spellFixes = emptyList()
        if (::bar.isInitialized) bar.setSpellFix(null, emptyList())
    }

    /** فحص الكلمة المكتملة — تظهر شريحة التصحيح إن كانت خاطئة */
    private fun maybeSpellChip(word: String) {
        if (!prefs.spellChipEnabled || word.length < 2) { clearSpellChip(); return }
        val lang = onyxAssets.languages[langIdx].id
        val r = OnyxEngine.spellCheck(word, lang)
        if (!r.correct && r.fixes.isNotEmpty()) {
            spellWrong = word
            spellFixes = r.fixes
            bar.setSpellFix(word, r.fixes)
            updateSuggestions()
        } else {
            clearSpellChip()
        }
    }

    /** استبدال الكلمة الخاطئة الأخيرة بالتصحيح المختار — بفحص تطابق صارم قبل الحذف */
    private fun fixLastWord(wrong: String, fix: String) {
        val ic = currentInputConnection ?: return
        val before1 = ic.getTextBeforeCursor(wrong.length + 1, 0) ?: ""
        val before0 = ic.getTextBeforeCursor(wrong.length, 0) ?: ""
        val deleteN = when {
            before1 == "$wrong " -> wrong.length + 1
            before0 == wrong -> wrong.length
            else -> { clearSpellChip(); return }  // الكلمة لم تعد أمام المؤشر — نتجاهل بأمان
        }
        ic.deleteSurroundingText(deleteN, 0)
        ic.commitText("$fix ", 1)
        learnWord(fix)
        fixes++
        prev2 = prevWord
        prevWord = fix
        clearSpellChip()
        soundKit.play(SoundKit.Sound.TAP)
        updateSuggestions()
    }

    override fun onSpellFix(wrong: String, fix: String) = fixLastWord(wrong, fix)

    override fun onSpellDismiss() {
        clearSpellChip()
        soundKit.play(SoundKit.Sound.TAP)
        updateSuggestions()
    }

    /* ================= M3 Deep: المصحح القواعدي للجملة (52) ================= */
    private fun clearGrammarChip() {
        grammarIssues = emptyList()
        if (::bar.isInitialized) bar.setGrammar(emptyList())
    }

    /** فحص الجملة أمام المؤشر — تظهر شريحة «✎ قواعد» عند وجود ملاحظات */
    private fun maybeGrammarChip() {
        if (!prefs.grammarChipEnabled) { clearGrammarChip(); return }
        val before = currentInputConnection?.getTextBeforeCursor(GRAMMAR_WINDOW, 0)?.toString() ?: ""
        if (before.trim().length < 4) { clearGrammarChip(); return }
        val lang = onyxAssets.languages[langIdx].id
        val (fixed, issues) = OnyxEngine.grammarFix(before, lang)
        grammarIssues = if (issues.isNotEmpty() && fixed != before) issues else emptyList()
        bar.setGrammar(grammarIssues)
    }

    /** تطبيق التصحيح القواعدي — يُعاد الحساب لحظياً من النص الحالي (بلا حالة قديمة) */
    private fun applyGrammarFix() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(GRAMMAR_WINDOW, 0)?.toString() ?: ""
        if (before.trim().length < 4) { clearGrammarChip(); return }
        val lang = onyxAssets.languages[langIdx].id
        val (fixed, issues) = OnyxEngine.grammarFix(before, lang)
        if (issues.isEmpty() || fixed == before) { clearGrammarChip(); return }
        try {
            ic.beginBatchEdit()
            // نعد بنقاط الكود لمطابقة الحذف بدقة حتى مع الإيموجي (API 24+)
            ic.deleteSurroundingTextInCodePoints(Character.codePointCount(before, 0, before.length), 0)
            ic.commitText(fixed, 1)
            ic.endBatchEdit()
        } catch (e: Exception) {
            try { ic.endBatchEdit() } catch (e2: Exception) { /* لم يبدأ */ }
            clearGrammarChip()
            return
        }
        fixes += issues.size
        clearGrammarChip()
        soundKit.play(SoundKit.Sound.TAP)
        updateSuggestions()
    }

    override fun onGrammarApply() = applyGrammarFix()

    override fun onGrammarDismiss() {
        clearGrammarChip()
        soundKit.play(SoundKit.Sound.TAP)
        updateSuggestions()
    }

    override fun onFloatingToggle() {
        soundKit.play(SoundKit.Sound.TAP)
        prefs.floatingEnabled = !prefs.floatingEnabled
        applyWindowMode()
    }

    override fun onOneHandedCycle() {
        soundKit.play(SoundKit.Sound.TAP)
        prefs.oneHanded = (prefs.oneHanded + 1) % 3
        applyWindowMode()
    }

    /* ================= M2: اللوحات الداخلية ================= */
    private var activePanel: Int = PANEL_NONE

    private fun showPanel(which: Int) {
        activePanel = if (activePanel == which) PANEL_NONE else which
        emojiPanel.visibility = if (activePanel == PANEL_EMOJI) View.VISIBLE else View.GONE
        clipPanel.visibility = if (activePanel == PANEL_CLIP) View.VISIBLE else View.GONE
        panelHost?.visibility = if (activePanel == PANEL_NONE) View.GONE else View.VISIBLE
        if (activePanel == PANEL_CLIP) clipPanel.refresh()
        theme?.let { if (activePanel == PANEL_EMOJI) emojiPanel.applyTheme(it) }
    }

    override fun onEmoji() {
        soundKit.play(SoundKit.Sound.TAP)
        showPanel(PANEL_EMOJI)
    }

    override fun onClipboardPanel() {
        soundKit.play(SoundKit.Sound.TAP)
        showPanel(PANEL_CLIP)
    }

    override fun onPanelClosed() {
        showPanel(PANEL_NONE)
    }

    override fun onEmojiPicked(emoji: String) {
        currentInputConnection?.commitText(emoji, 1)
        prefs.pushEmojiRecent(emoji)
        soundKit.play(SoundKit.Sound.TAP)
        // تبقى اللوحة مفتوحة للإدراج المتتابع (سلوك لوحات النظام)
    }

    override fun onPaste(text: String) {
        currentInputConnection?.commitText(text, 1)
        soundKit.play(SoundKit.Sound.TAP)
        showPanel(PANEL_NONE)
    }

    /* ================= M2: شريحة التشكيل ================= */
    override fun onTashkeel() {
        layer = if (layer == LAYER_TASHKEEL) LAYER_LETTERS else LAYER_TASHKEEL
        shift = false
        soundKit.play(SoundKit.Sound.TAP)
        setRowsForState()
    }

    /* ================= M2: الإدخال الصوتي (صلاحيات صفرية) ================= */
    override fun onVoice() {
        soundKit.play(SoundKit.Sound.TAP)
        if (!prefs.voiceEnabled) {
            Toast.makeText(this, "الإدخال الصوتي معطّل من إعدادات التطبيق", Toast.LENGTH_SHORT).show()
            return
        }
        if (voiceReceiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val text = intent?.getStringExtra(VoiceBridgeActivity.EXTRA_TEXT) ?: ""
                    unregisterVoice()
                    if (text.isNotBlank()) {
                        currentInputConnection?.commitText("$text ", 1)
                        words++
                        updateSuggestions()
                    } else {
                        Toast.makeText(this@OnyxImeService,
                            "لا يوجد محرك تعرف كلام على الجهاز أو أُلغي الاستماع", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            voiceReceiver = r
            ContextCompat.registerReceiver(
                this, r, IntentFilter(VoiceBridgeActivity.ACTION_RESULT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            // مهلة أمان: إن لم يعُد النشاط لا نترك المستمع حياً
            voiceTimeout.postDelayed({ if (voiceReceiver != null) unregisterVoice() }, 120_000L)
        }
        val locale = onyxAssets.languages[langIdx].locale
        val intent = Intent(this, VoiceBridgeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("locale", locale)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            unregisterVoice()
            Toast.makeText(this, "تعذر بدء الاستماع", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unregisterVoice() {
        voiceReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { /* غير مسجل */ }
        }
        voiceReceiver = null
        voiceTimeout.removeCallbacksAndMessages(null)
    }

    /* ================= إجراءات التحرير ================= */
    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        if (composing.isNotEmpty()) {
            // إنهاء الكلمة كما كُتبت + تعلمها
            val word = composing.toString()
            ic.finishComposingText()
            learnWord(word)
            prev2 = prevWord
            prevWord = word
            words++
            composing.clear()
            // M3: فحص الكلمة المكتملة — شريحة تصحيح فوري إن لزم
            maybeSpellChip(word)
        } else {
            val before = ic.getTextBeforeCursor(2, 0) ?: ""
            if (lastSpaceAt > 0 && now - lastSpaceAt < 700 && before.endsWith(" ")) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceAt = 0
                soundKit.play(SoundKit.Sound.SPACE)
                afterSentenceEnd()
                // M3 Deep: نقطة مزدوجة = نهاية جملة — فرصة فحص قواعدي
                if (spellWrong == null) maybeGrammarChip() else clearGrammarChip()
                updateSuggestions()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpaceAt = now
        soundKit.play(SoundKit.Sound.SPACE)
        afterSentenceEnd()
        // M3 Deep: بلا شريحة إملائية — افحص الجملة قواعدياً (52)
        if (spellWrong == null) maybeGrammarChip() else clearGrammarChip()
        updateSuggestions()
    }

    /** خوارزمية 32: حرف كبير تلقائي بعد نهاية الجملة (للاتينية) */
    private fun afterSentenceEnd() {
        val lang = onyxAssets.languages[langIdx].id
        if (lang == "ar" || lang == "ur" || lang == "fa") return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(3, 0) ?: ""
        if (before.isEmpty() || before.endsWith(". ") || before.endsWith("! ") || before.endsWith("? ")) {
            shift = true
            setRowsForState()
        }
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        soundKit.play(SoundKit.Sound.DELETE)
        if (composing.isNotEmpty()) {
            // نزع آخر نقطة كود (بما في ذلك الأزواج البديلة للإيموجي)
            val last = composing.last()
            composing.deleteCharAt(composing.length - 1)
            if (Character.isLowSurrogate(last) && composing.isNotEmpty() && Character.isHighSurrogate(composing.last())) {
                composing.deleteCharAt(composing.length - 1)
            }
            ic.setComposingText(composing.toString(), 1)
            updateSuggestions()
        } else {
            ic.deleteSurroundingText(1, 0)
            prevWord = ""
            prev2 = ""
            updateSuggestions()
        }
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        soundKit.play(SoundKit.Sound.ENTER)
        if (composing.isNotEmpty()) {
            ic.finishComposingText()
            val committed = composing.toString()
            learnWord(committed)
            prev2 = prevWord
            prevWord = committed
            words++
            composing.clear()
            maybeSpellChip(committed)
        }
        val handled = sendDefaultEditorAction(true)
        if (!handled) ic.commitText("\n", 1)
        // M3 Deep: فحص قواعدي عند إتمام السطر
        if (spellWrong == null) maybeGrammarChip() else clearGrammarChip()
        updateSuggestions()
    }

    /* ================= الزخرفة ================= */
    override fun onDecor() {
        soundKit.play(SoundKit.Sound.TAP)
        val style = Decor.styleAt(prefs.decorIndex)
        prefs.decorIndex = prefs.decorIndex + 1
        val ic = currentInputConnection
        if (ic == null) return
        if (composing.isNotEmpty()) {
            val styled = style.apply(composing.toString())
            composing.clear()
            ic.commitText("$styled ", 1)
        } else {
            val before = ic.getTextBeforeCursor(48, 0) ?: ""
            val m = Regex("[\\p{L}\\p{N}]+\$").find(before)
            val word = m?.value
            if (word != null) {
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText("${style.apply(word)} ", 1)
            } else {
                ic.commitText("${style.apply("")} ", 1)
            }
        }
        updateSuggestions()
    }

    private fun persistStats() {
        val k = keystrokes; val f = fixes; val w = words
        if (k == 0L && f == 0 && w == 0) return
        prefs.updateActive {
            it.keystrokes += k
            it.fixes += f
            it.words += w
        }
        keystrokes = 0; fixes = 0; words = 0
    }

    companion object {
        private const val LAYER_LETTERS = 0
        private const val LAYER_SYMBOLS = 1
        private const val LAYER_TASHKEEL = 2
        private const val PANEL_NONE = 0
        private const val PANEL_EMOJI = 1
        private const val PANEL_CLIP = 2
        /** M3 Deep: نافذة القراءة للمصحح القواعدي قبل المؤشر */
        private const val GRAMMAR_WINDOW = 400
        /** بث داخلي من الإعدادات — إعادة تطبيق وضع النافذة */
        const val ACTION_APPLY_WINDOW_MODE = "com.onyx.keyboard.APPLY_WINDOW_MODE"
    }
}
