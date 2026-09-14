package com.onyx.keyboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** ملف شخصي — ذاكرة تعلم معزولة لكل ملف (نظام المستخدم العادي) */
data class Profile(
    val id: String,
    var name: String,
    var avatar: String,
    var themeId: String,
    var templateId: String,
    var fontId: String,
    var fontScale: Float,
    var nightMode: Boolean,
    var warmth: Float,
    var learnedTsv: String,
    var keystrokes: Long,
    var fixes: Int,
    var words: Int,
    /** M3: توليد الثيم من ألوان خلفية النظام (Material You) */
    var materialYou: Boolean = false,
)

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("onyx-prefs-v1", Context.MODE_PRIVATE)

    /* ------- الملفات الشخصية ------- */
    var profiles: MutableList<Profile> = loadProfiles()
        private set

    var activeProfileId: String
        get() = sp.getString(KEY_ACTIVE, null) ?: profiles.firstOrNull()?.id ?: ""
        set(value) = sp.edit().putString(KEY_ACTIVE, value).apply()

    val active: Profile
        get() = profiles.firstOrNull { it.id == activeProfileId }
            ?: profiles.firstOrNull()
            ?: defaultProfile("ضيف").also { profiles.add(it); persistProfiles() }

    /* ------- إعدادات عامة ------- */
    var activeLang: String
        get() = sp.getString(KEY_LANG, "ar") ?: "ar"
        set(v) = sp.edit().putString(KEY_LANG, v).apply()

    var defaultLang: String
        get() = sp.getString(KEY_DEFAULT_LANG, "ar") ?: "ar"
        set(v) = sp.edit().putString(KEY_DEFAULT_LANG, v).apply()

    var glideEnabled: Boolean
        get() = sp.getBoolean(KEY_GLIDE, true)
        set(v) = sp.edit().putBoolean(KEY_GLIDE, v).apply()

    var autoLearn: Boolean
        get() = sp.getBoolean(KEY_LEARN, true)
        set(v) = sp.edit().putBoolean(KEY_LEARN, v).apply()

    var nextWordEnabled: Boolean
        get() = sp.getBoolean(KEY_NEXT, true)
        set(v) = sp.edit().putBoolean(KEY_NEXT, v).apply()

    var smartChipsEnabled: Boolean
        get() = sp.getBoolean(KEY_CHIPS, true)
        set(v) = sp.edit().putBoolean(KEY_CHIPS, v).apply()

    var engineerBadges: Boolean
        get() = sp.getBoolean(KEY_BADGES, true)
        set(v) = sp.edit().putBoolean(KEY_BADGES, v).apply()

    var arabiziEnabled: Boolean
        get() = sp.getBoolean(KEY_ARABIZI, true)
        set(v) = sp.edit().putBoolean(KEY_ARABIZI, v).apply()

    var hapticsEnabled: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(v) = sp.edit().putBoolean(KEY_HAPTICS, v).apply()

    var simpleMode: Boolean
        get() = sp.getBoolean(KEY_SIMPLE, true)
        set(v) = sp.edit().putBoolean(KEY_SIMPLE, v).apply()

    /** شدة التصحيح 0..2 (متحفظ/متوازن/سخي) — تنعكس على خوارزمية 45 */
    var correctionLevel: Int
        get() = sp.getInt(KEY_CORRECTION, 1)
        set(v) = sp.edit().putInt(KEY_CORRECTION, v).apply()

    var decorIndex: Int
        get() = sp.getInt(KEY_DECOR, 0)
        set(v) = sp.edit().putInt(KEY_DECOR, v).apply()

    /* ------- إعدادات M2 Nova ------- */
    /** أصوات المفاتيح — مغلقة افتراضياً احتراماً لهدوء المستخدم */
    var soundEnabled: Boolean
        get() = sp.getBoolean(KEY_SOUND, false)
        set(v) = sp.edit().putBoolean(KEY_SOUND, v).apply()

    var soundVolume: Float
        get() = sp.getFloat(KEY_SOUND_VOL, 0.5f)
        set(v) = sp.edit().putFloat(KEY_SOUND_VOL, v).apply()

    /** الإدخال الصوتي عبر جسر نظامي بصلاحيات صفرية */
    var voiceEnabled: Boolean
        get() = sp.getBoolean(KEY_VOICE, true)
        set(v) = sp.edit().putBoolean(KEY_VOICE, v).apply()

    /** الحافظة التاريخية الداخلية */
    var clipHistoryEnabled: Boolean
        get() = sp.getBoolean(KEY_CLIPHIST, true)
        set(v) = sp.edit().putBoolean(KEY_CLIPHIST, v).apply()

    /** الإيموجي السياقي (خوارزميتا 34-35) */
    var emojiContextEnabled: Boolean
        get() = sp.getBoolean(KEY_EMOJICTX, true)
        set(v) = sp.edit().putBoolean(KEY_EMOJICTX, v).apply()

    /** أحدث الإيموجي المستخدمة (عالمي لكل الملفات) */
    fun emojiRecents(): List<String> {
        val raw = sp.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { if (arr.isNull(it)) null else arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun pushEmojiRecent(emoji: String) {
        val cur = emojiRecents().toMutableList()
        cur.remove(emoji)
        cur.add(0, emoji)
        val trimmed = cur.take(24)
        val arr = JSONArray()
        for (e in trimmed) arr.put(e)
        sp.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    /* ------- إعدادات M3 Legend ------- */

    /** شريحة التصحيح الفوري بعد إتمام الكلمة (خوارزمية 51) */
    var spellChipEnabled: Boolean
        get() = sp.getBoolean(KEY_SPELLCHIP, true)
        set(v) = sp.edit().putBoolean(KEY_SPELLCHIP, v).apply()

    /** الوضع العائم — نافذة اللوحة نفسها بطاقة صغيرة (API 28+، صفر أذونات) */
    var floatingEnabled: Boolean
        get() = sp.getBoolean(KEY_FLOATING, false)
        set(v) = sp.edit().putBoolean(KEY_FLOATING, v).apply()

    /** آخر موضع للبطاقة العائمة */
    var floatX: Int
        get() = sp.getInt(KEY_FLOAT_X, -1)
        set(v) = sp.edit().putInt(KEY_FLOAT_X, v).apply()

    var floatY: Int
        get() = sp.getInt(KEY_FLOAT_Y, -1)
        set(v) = sp.edit().putInt(KEY_FLOAT_Y, v).apply()

    /** M3 Deep: حجم البطاقة العائمة — نسبة من عرض الشاشة (0.40..0.90) */
    var floatScale: Float
        get() = sp.getFloat(KEY_FLOAT_SCALE, 0.62f)
        set(v) = sp.edit().putFloat(KEY_FLOAT_SCALE, v.coerceIn(0.40f, 0.90f)).apply()

    /** M3 Deep: شريحة التصحيح القواعدي للجملة (خوارزمية 52) */
    var grammarChipEnabled: Boolean
        get() = sp.getBoolean(KEY_GRAMMAR, true)
        set(v) = sp.edit().putBoolean(KEY_GRAMMAR, v).apply()

    /** اليد الواحدة: 0 معطّل، 1 يمين، 2 يسار */
    var oneHanded: Int
        get() = sp.getInt(KEY_ONEHANDED, 0)
        set(v) = sp.edit().putInt(KEY_ONEHANDED, v.coerceIn(0, 2)).apply()

    /* ------- تعديل الملف الحالي ------- */
    fun updateActive(transform: (Profile) -> Unit) {
        val p = active
        transform(p)
        persistProfiles()
    }

    fun addProfile(name: String, avatar: String): Profile {
        val p = Profile(
            id = UUID.randomUUID().toString().take(8),
            name = name.ifEmpty { "ملف جديد" }, avatar = avatar,
            themeId = "onyx", templateId = "onyx", fontId = "system",
            fontScale = 1f, nightMode = false, warmth = 0.35f,
            learnedTsv = "", keystrokes = 0, fixes = 0, words = 0,
        )
        profiles.add(p)
        activeProfileId = p.id
        persistProfiles()
        return p
    }

    fun removeProfile(id: String) {
        if (profiles.size <= 1) return
        profiles.removeAll { it.id == id }
        if (activeProfileId == id) activeProfileId = profiles.first().id
        persistProfiles()
    }

    /* ------- تسلسل JSON ------- */
    private fun loadProfiles(): MutableList<Profile> {
        val raw = sp.getString(KEY_PROFILES, null) ?: return mutableListOf(
            defaultProfile("ضيف")
        )
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Profile>()
            for (i in 0 until arr.length()) out.add(parseProfile(arr.getJSONObject(i)))
            if (out.isEmpty()) out.add(defaultProfile("ضيف"))
            out
        } catch (e: Exception) {
            mutableListOf(defaultProfile("ضيف"))
        }
    }

    fun persistProfiles() {
        val arr = JSONArray()
        for (p in profiles) arr.put(toJson(p))
        sp.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun toJson(p: Profile) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("avatar", p.avatar)
        put("themeId", p.themeId); put("templateId", p.templateId); put("fontId", p.fontId)
        put("fontScale", p.fontScale.toDouble()); put("nightMode", p.nightMode)
        put("warmth", p.warmth.toDouble()); put("learnedTsv", p.learnedTsv)
        put("keystrokes", p.keystrokes); put("fixes", p.fixes); put("words", p.words)
        put("materialYou", p.materialYou)
    }

    private fun parseProfile(o: JSONObject) = Profile(
        id = o.optString("id", UUID.randomUUID().toString().take(8)),
        name = o.optString("name", "ضيف"),
        avatar = o.optString("avatar", "😈"),
        themeId = o.optString("themeId", "onyx"),
        templateId = o.optString("templateId", "onyx"),
        fontId = o.optString("fontId", "system"),
        fontScale = o.optDouble("fontScale", 1.0).toFloat(),
        nightMode = o.optBoolean("nightMode", false),
        warmth = o.optDouble("warmth", 0.35).toFloat(),
        learnedTsv = o.optString("learnedTsv", ""),
        keystrokes = o.optLong("keystrokes", 0),
        fixes = o.optInt("fixes", 0),
        words = o.optInt("words", 0),
        materialYou = o.optBoolean("materialYou", false),
    )

    companion object {
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "activeProfile"
        private const val KEY_LANG = "activeLang"
        private const val KEY_DEFAULT_LANG = "defaultLang"
        private const val KEY_GLIDE = "glideEnabled"
        private const val KEY_LEARN = "autoLearn"
        private const val KEY_NEXT = "nextWord"
        private const val KEY_CHIPS = "smartChips"
        private const val KEY_BADGES = "engineerBadges"
        private const val KEY_ARABIZI = "arabizi"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_SIMPLE = "simpleMode"
        private const val KEY_CORRECTION = "correctionLevel"
        private const val KEY_DECOR = "decorIndex"
        private const val KEY_SOUND = "soundEnabled"
        private const val KEY_SOUND_VOL = "soundVolume"
        private const val KEY_VOICE = "voiceEnabled"
        private const val KEY_CLIPHIST = "clipHistoryEnabled"
        private const val KEY_EMOJICTX = "emojiContextEnabled"
        private const val KEY_RECENTS = "emojiRecents"
        private const val KEY_SPELLCHIP = "spellChipEnabled"
        private const val KEY_FLOATING = "floatingEnabled"
        private const val KEY_FLOAT_X = "floatX"
        private const val KEY_FLOAT_Y = "floatY"
        private const val KEY_FLOAT_SCALE = "floatScale"
        private const val KEY_GRAMMAR = "grammarChipEnabled"
        private const val KEY_ONEHANDED = "oneHanded"

        val AVATARS = listOf("😈", "🦁", "🦅", "🐉", "🚀", "🌙", "⭐", "🦉", "🐺", "👑", "🔥", "💎", "🧠", "🤖", "🌸", "🦋")

        fun defaultProfile(name: String, avatar: String = "😈") = Profile(
            id = UUID.randomUUID().toString().take(8),
            name = name, avatar = avatar,
            themeId = "onyx", templateId = "onyx", fontId = "system",
            fontScale = 1f, nightMode = false, warmth = 0.35f,
            learnedTsv = "", keystrokes = 0, fixes = 0, words = 0,
        )
    }
}
