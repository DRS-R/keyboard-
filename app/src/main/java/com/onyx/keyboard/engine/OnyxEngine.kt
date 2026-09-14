package com.onyx.keyboard.engine

import android.content.Context

/**
 * OnyxEngine — الجسر الوحيد إلى نواة C++ (libonyx_engine.so عبر NDK)
 * كل الدوال الخارجية تناظر دوال onyx_jni.cpp حرفياً.
 */
object OnyxEngine {

    init {
        System.loadLibrary("onyx_engine")
    }

    @JvmStatic external fun nativeSetConfig(offset: Int)
    @JvmStatic external fun nativeLoadDict(lang: String, tsv: String)
    @JvmStatic external fun nativeLoadBigrams(lang: String, tsv: String)
    @JvmStatic external fun nativeLoadMistakes(lang: String, tsv: String)
    @JvmStatic external fun nativeLoadStarters(lang: String, txt: String)
    @JvmStatic external fun nativeSetLearnedTsv(tsv: String)
    @JvmStatic external fun nativeGetLearnedTsv(): String
    @JvmStatic external fun nativeLearnWord(word: String): Int
    @JvmStatic external fun nativeRemoveLearned(word: String): Int
    @JvmStatic external fun nativeResetLearned()
    @JvmStatic external fun nativeSuggest(word: String, prev: String, lang: String, wpm: Int, history: String): Array<String>
    @JvmStatic external fun nativeNextWord(prev: String, prev2: String, lang: String): Array<String>
    @JvmStatic external fun nativeGlide(seq: Array<String>, lang: String, prev: String): String
    @JvmStatic external fun nativeExtract(text: String): String
    @JvmStatic external fun nativeNormalize(text: String, lang: String): String
    @JvmStatic external fun nativeArabizi(word: String): String
    @JvmStatic external fun nativeLatinize(word: String): String
    @JvmStatic external fun nativeDetectScript(text: String): Int
    @JvmStatic external fun nativeCodeSwitch(text: String): Int

    // M2 Nova — المشاعر (34) والإيموجي السياقي (35)
    @JvmStatic external fun nativeLoadEmojiTags(tsv: String)
    @JvmStatic external fun nativeSentiment(text: String): Int
    @JvmStatic external fun nativeEmojiSuggest(text: String, lang: String): Array<String>

    // M3 Legend — المصحح اللغوي (51)
    @JvmStatic external fun nativeSpellCheck(word: String, lang: String): String
    @JvmStatic external fun nativeSpellCheckText(text: String, lang: String): Array<String>

    // M3 Deep — المصحح القواعدي للجمل (52)
    @JvmStatic external fun nativeGrammarFix(text: String, lang: String): Array<String>

    /** اقتراح مفكك */
    data class Sug(val word: String, val type: String, val algo: Int)

    /** شرائح ذكية مستخرجة (36-40) */
    data class Extract(val url: String?, val phone: String?, val email: String?, val otp: String?, val date: String?)

    /** نتيجة تدقيق كلمة (51) */
    data class Spell(val correct: Boolean, val fixes: List<String>)

    /** ملاحظة قواعدية من المصحح القواعدي للجمل (52) */
    data class GrammarIssue(val cat: String, val before: String, val after: String, val hint: String)

    private val engineLock = Any()

    /**
     * تدقيق كلمة مكتملة — محمي بقفل لأن خدمة المصحح النظامية تستدعيه
     * من خيط binder بينما تكتب الخدمة الرئيسية كلمات متعلمة من الخيط الأساسي.
     */
    fun spellCheck(word: String, lang: String): Spell = synchronized(engineLock) {
        val p = nativeSpellCheck(word, lang).split("\u0001")
        Spell(
            correct = p.firstOrNull() == "1",
            fixes = p.drop(1).filter { it.isNotBlank() },
        )
    }

    /** تدقيق نص كامل (سطر لكل كلمة خاطئة) — لخدمة المصحح النظامية */
    fun spellCheckText(text: String, lang: String): List<Pair<String, List<String>>> =
        synchronized(engineLock) {
            nativeSpellCheckText(text, lang).map { row ->
                val p = row.split("\u0001")
                p.first() to p.drop(1)
            }
        }

    /**
     * المصحح القواعدي للجمل (52) — يُرجع النص المصحح + قائمة الملاحظات المُطبّقة.
     * محمي بقفل مثل spellCheck لأنه قد يُستدعى من خيوط مختلفة.
     */
    fun grammarFix(text: String, lang: String): Pair<String, List<GrammarIssue>> =
        synchronized(engineLock) {
            val rows = nativeGrammarFix(text, lang)
            val fixed = rows.firstOrNull() ?: text
            val issues = rows.drop(1).map { r ->
                val p = r.split("\u0001")
                GrammarIssue(
                    cat = p.getOrElse(0) { "قواعد" },
                    before = p.getOrElse(1) { "" },
                    after = p.getOrElse(2) { "" },
                    hint = p.getOrElse(3) { "" },
                )
            }
            fixed to issues
        }

    fun parseSug(rows: Array<String>): List<Sug> = rows.map { r ->
        val p = r.split("\u0001")
        Sug(
            word = p.getOrElse(0) { "" },
            type = p.getOrElse(1) { "next" },
            algo = p.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
        )
    }

    fun parseExtract(line: String): Extract {
        val p = line.split("\u0001")
        fun f(i: Int): String? = p.getOrElse(i) { "" }.ifEmpty { null }
        return Extract(f(0), f(1), f(2), f(3), f(4))
    }

    private var loaded = false

    /** تحميل حزم اللغات التسع من الأصول — يُستدعى مرة واحدة */
    fun loadAll(context: Context, learnedTsv: String, correctionOffset: Int) {
        if (loaded) {
            nativeSetLearnedTsv(learnedTsv)
            nativeSetConfig(correctionOffset)
            return
        }
        val langs = listOf("ar", "en", "fr", "es", "tr", "de", "ur", "fa", "ru")
        for (lang in langs) {
            nativeLoadDict(lang, readAsset(context, "onyx/dicts/$lang.tsv"))
            nativeLoadBigrams(lang, readAsset(context, "onyx/langpacks/$lang.bigrams.tsv"))
            nativeLoadMistakes(lang, readAsset(context, "onyx/langpacks/$lang.mistakes.tsv"))
            nativeLoadStarters(lang, readAsset(context, "onyx/langpacks/$lang.starters.txt"))
        }
        nativeLoadEmojiTags(readAsset(context, "onyx/emoji_tags.tsv"))
        nativeSetLearnedTsv(learnedTsv)
        nativeSetConfig(correctionOffset)
        loaded = true
    }

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}
