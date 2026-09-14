package com.onyx.keyboard.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.onyx.keyboard.R

/**
 * SoundKit — أصوات المفاتيح المركّبة (M2: دمج الصوت)
 * نغمات WAV مولّدة رقمياً في res/raw — بلا أصول خارجية وبلا إنترنت.
 */
class SoundKit(context: Context) {

    enum class Sound { TAP, SPACE, DELETE, ENTER }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<Sound, Int>()
    /** شدة الصوت 0..1 — تُربط بإعداد المستخدم */
    var volume: Float = 0.5f
    /** تشغيل/إيقاف عام */
    var enabled: Boolean = false

    init {
        ids[Sound.TAP] = pool.load(context, R.raw.snd_tap, 1)
        ids[Sound.SPACE] = pool.load(context, R.raw.snd_space, 1)
        ids[Sound.DELETE] = pool.load(context, R.raw.snd_delete, 1)
        ids[Sound.ENTER] = pool.load(context, R.raw.snd_enter, 1)
    }

    fun play(kind: Sound) {
        if (!enabled) return
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return
        ids[kind]?.let { pool.play(it, v, v, 1, 0, 1f) }
    }

    fun release() {
        pool.release()
    }
}
