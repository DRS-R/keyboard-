package com.onyx.keyboard.spell

import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import com.onyx.keyboard.data.Prefs
import com.onyx.keyboard.engine.OnyxEngine

/**
 * OnyxSpellCheckerService — المصحح اللغوي النظامي (M3 Legend)
 * يجعل نواة Onyx C++ (خوارزمية 51) مدققاً إملائياً على مستوى الجهاز كله:
 * يظهر في إعدادات النظام ← لغات وإدخال ← التدقيق الإملائي، ويخدم كل التطبيقات.
 * صلاحيات صفرية: BIND_TEXT_SERVICE تفويض نظامي، والنواة محلية بالكامل.
 */
class OnyxSpellCheckerService : SpellCheckerService() {

    override fun onCreate() {
        super.onCreate()
        // تأكد أن النواة محمّلة حتى لو لم تُشغَّل لوحة الإدخال بعد
        val prefs = Prefs(this)
        OnyxEngine.loadAll(this, prefs.active.learnedTsv, prefs.correctionLevel - 1)
    }

    override fun createSession(): Session = OnyxSpellSession()

    private class OnyxSpellSession : Session() {

        override fun onCreate() {
            // لغة الجلسة متاحة عبر getLocale() عند كل استدعاء
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val word = textInfo?.text?.trim().orEmpty()
            if (word.isEmpty()) return SuggestionsInfo(0, arrayOf())
            val lang = (locale ?: "ar").substringBefore('_')
            return try {
                val r = OnyxEngine.spellCheck(word, lang)
                if (r.correct) {
                    SuggestionsInfo(0, arrayOf())
                } else {
                    val attrs = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                    val info = SuggestionsInfo(attrs, r.fixes.take(5).toTypedArray())
                    if (textInfo != null) {
                        info.setCookieAndSequence(textInfo.cookie, textInfo.sequence)
                    }
                    info
                }
            } catch (e: Exception) {
                SuggestionsInfo(0, arrayOf())
            }
        }
    }
}
