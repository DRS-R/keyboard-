package com.onyx.keyboard.model

import android.content.res.AssetManager
import android.graphics.Color
import org.json.JSONObject

/** مفتاح تخطيطي — ترحيل حرفي من engine/layouts.ts */
data class KeyDef(
    val char: String? = null,
    val label: String? = null,
    val icon: String? = null,
    val alts: List<String> = emptyList(),
    val w: Float = 1f,
    val action: String? = null,
    val accent: Boolean = false,
)

/** ثيم Material 3 — 14 ثيماً من نسخة الويب */
data class KbTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bg: Int,
    val surface: Int,
    val key: Int,
    val keyText: Int,
    val keyAlt: Int,
    val accent: Int,
    val accentText: Int,
    val trail: Int,
)

/** قالب تصميم — 8 قوالب (نصف قطر/تباعد/ظل/حدود) */
data class KbTemplate(
    val id: String,
    val name: String,
    val radius: Int,
    val gap: Int,
    val shadow: String,
    val glass: Boolean,
    val outline: Boolean,
)

data class EmojiGroupUi(val name: String, val emojis: List<String>)
data class LanguageUi(val id: String, val name: String, val flag: String, val locale: String)
data class FontOptionUi(val id: String, val name: String, val families: String)

/**
 * أصول المحرك — تُولَّد من نسخة الويب v0.4 عبر scripts/export_onyx_assets.mjs
 * وتُحمَّل مرة واحدة إلى الذاكرة (org.json مدمج في الأندرويد).
 */
class OnyxAssets(assets: AssetManager) {

    val layerOrder = listOf("ar", "en", "fr", "es", "tr", "de", "ur", "fa", "ru", "symbols", "tashkeel")
    val layouts = HashMap<String, List<List<KeyDef>>>()
    val themes = ArrayList<KbTheme>()
    val templates = ArrayList<KbTemplate>()
    val emojiGroups = ArrayList<EmojiGroupUi>()
    val languages = ArrayList<LanguageUi>()
    val fonts = ArrayList<FontOptionUi>()
    val decorSnippets = ArrayList<String>()
    val kaomoji = HashMap<String, List<String>>()
    /** وسوم الإيموجي للبحث السياقي — إيموجي → وسوم صغيرة (M2) */
    val emojiTags = HashMap<String, String>()

    init {
        val json = assets.open("onyx/ui.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)

        val layoutsJson = root.getJSONObject("layouts")
        for (layer in layerOrder) {
            if (!layoutsJson.has(layer)) continue
            val rowsJson = layoutsJson.getJSONArray(layer)
            val rows = ArrayList<List<KeyDef>>()
            for (r in 0 until rowsJson.length()) {
                val keysJson = rowsJson.getJSONArray(r)
                val keys = ArrayList<KeyDef>()
                for (k in 0 until keysJson.length()) {
                    keys.add(parseKey(keysJson.getJSONObject(k)))
                }
                rows.add(keys)
            }
            layouts[layer] = rows
        }

        val themesJson = root.getJSONArray("themes")
        for (i in 0 until themesJson.length()) {
            val t = themesJson.getJSONObject(i)
            themes.add(
                KbTheme(
                    id = t.getString("id"), name = t.getString("name"), dark = t.getBoolean("dark"),
                    bg = Color.parseColor(t.getString("bg")),
                    surface = Color.parseColor(t.getString("surface")),
                    key = Color.parseColor(t.getString("key")),
                    keyText = Color.parseColor(t.getString("keyText")),
                    keyAlt = Color.parseColor(t.getString("keyAlt")),
                    accent = Color.parseColor(t.getString("accent")),
                    accentText = Color.parseColor(t.getString("accentText")),
                    trail = Color.parseColor(t.getString("trail")),
                )
            )
        }

        val tplJson = root.getJSONArray("templates")
        for (i in 0 until tplJson.length()) {
            val t = tplJson.getJSONObject(i)
            templates.add(
                KbTemplate(
                    id = t.getString("id"), name = t.getString("name"),
                    radius = t.getInt("radius"), gap = t.getInt("gap"),
                    shadow = t.optString("shadow", "none"),
                    glass = t.optBoolean("glass", false),
                    outline = t.optBoolean("outline", false),
                )
            )
        }

        val egJson = root.getJSONArray("emojiGroups")
        for (i in 0 until egJson.length()) {
            val g = egJson.getJSONObject(i)
            val list = ArrayList<String>()
            val arr = g.getJSONArray("emojis")
            for (j in 0 until arr.length()) list.add(arr.getString(j))
            emojiGroups.add(EmojiGroupUi(g.getString("name"), list))
        }

        val langJson = root.getJSONArray("languages")
        for (i in 0 until langJson.length()) {
            val l = langJson.getJSONObject(i)
            languages.add(LanguageUi(l.getString("id"), l.getString("name"), l.getString("flag"), l.getString("locale")))
        }

        val fontJson = root.getJSONArray("fontOptions")
        for (i in 0 until fontJson.length()) {
            val f = fontJson.getJSONObject(i)
            fonts.add(FontOptionUi(f.getString("id"), f.getString("name"), f.getString("families")))
        }

        val snJson = root.getJSONArray("decorSnippets")
        for (i in 0 until snJson.length()) decorSnippets.add(snJson.getString(i))

        val kmJson = root.getJSONArray("kaomoji")
        for (i in 0 until kmJson.length()) {
            val g = kmJson.getJSONObject(i)
            val list = ArrayList<String>()
            val arr = g.getJSONArray("items")
            for (j in 0 until arr.length()) list.add(arr.getString(j))
            kaomoji[g.getString("mood")] = list
        }

        // M2: وسوم الإيموجي — نفس الملف الذي يحمّله نواة C++ للخوارزمية 35
        try {
            assets.open("onyx/emoji_tags.tsv").bufferedReader().use { r ->
                var line = r.readLine()
                while (line != null) {
                    val tab = line.indexOf('\t')
                    if (tab > 0) {
                        emojiTags[line.substring(0, tab)] = line.substring(tab + 1).lowercase()
                    }
                    line = r.readLine()
                }
            }
        } catch (e: Exception) {
            // الوسوم اختيارية — اللوحة تعمل بدونها
        }
    }

    /** بحث إيموجي بالوسوم (عربي/إنجليزي) — M2 */
    fun searchEmojis(query: String, limit: Int = 40): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for ((e, tags) in emojiTags) {
            if (e.contains(q) || tags.split(' ').any { it.contains(q) }) {
                out.add(e)
                if (out.size >= limit) break
            }
        }
        return out
    }

    private fun parseKey(o: JSONObject): KeyDef = KeyDef(
        char = if (o.has("char")) o.getString("char") else null,
        label = if (o.has("label")) o.getString("label") else null,
        icon = if (o.has("icon")) o.getString("icon") else null,
        alts = if (o.has("alts")) {
            val a = o.getJSONArray("alts")
            (0 until a.length()).map { a.getString(it) }
        } else emptyList(),
        w = if (o.has("w")) o.getDouble("w").toFloat() else 1f,
        action = if (o.has("action")) o.getString("action") else null,
        accent = o.optBoolean("accent", false),
    )

    fun themeById(id: String): KbTheme = themes.firstOrNull { it.id == id } ?: themes.first()
    fun templateById(id: String): KbTemplate = templates.firstOrNull { it.id == id } ?: templates.first()
    fun fontById(id: String): FontOptionUi = fonts.firstOrNull { it.id == id } ?: fonts.first()
    fun langIndex(id: String): Int = languages.indexOfFirst { it.id == id }.coerceAtLeast(0)
}
