package com.onyx.keyboard.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent

/**
 * VoiceBridgeActivity — جسر إدخال صوتي بصلاحيات صفرية (M2 Nova)
 *
 * الفكرة: لا نطلب RECORD_AUDIO إطلاقاً. نفوّض نشاط التعرف في النظام
 * (Google/محرك الجهاز) عبر RecognizerIntent — هو يملك المايك أصلاً،
 * يعيد إلينا النص نتيجة نشاط ثم نبثها للوحة عبر بث موجّه بالحزمة.
 *
 * ثيم شفاف + excludeFromRecents → المستخدم يرى حوار النظام فقط.
 */
class VoiceBridgeActivity : Activity() {

    companion object {
        const val ACTION_RESULT = "com.onyx.keyboard.VOICE_RESULT"
        const val EXTRA_TEXT = "text"
        private const val REQ_VOICE = 701
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val locale = intent?.getStringExtra("locale")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            if (!locale.isNullOrEmpty()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            }
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        val emitted = try {
            startActivityForResult(intent, REQ_VOICE)
            true
        } catch (e: Exception) {
            false
        }
        if (!emitted) {
            // لا يوجد محرك تعرف كلام على الجهاز — نبث فارغاً ونغلق
            emit("")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val text = if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
        } else ""
        emit(text)
        finish()
    }

    private fun emit(text: String) {
        sendBroadcast(
            Intent(ACTION_RESULT)
                .setPackage(packageName)
                .putExtra(EXTRA_TEXT, text)
        )
    }
}
