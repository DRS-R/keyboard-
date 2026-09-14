package com.onyx.keyboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** عنصر حافظة تاريخية — نص + وقت + تثبيت */
data class ClipEntry(val text: String, val at: Long, val pinned: Boolean)

/**
 * ClipboardHistory — حافظة تاريخية داخلية (M2 Nova)
 * تلتقط النسخ أثناء حياة خدمة الإدخال، وتخزن محلياً فقط (SharedPreferences)
 * بلا صلاحيات وبلا إنترنت: انسخ في أي تطبيق → يظهر هنا فوراً.
 * الحد 24 عنصراً · انتهاء تلقائي بعد 7 أيام · المثبت يدوياً يبقى.
 */
class ClipboardHistory(context: Context) {

    private val sp = context.getSharedPreferences("onyx-clipboard-v1", Context.MODE_PRIVATE)

    val entries: MutableList<ClipEntry> = load()

    companion object {
        private const val KEY_ITEMS = "items"
        private const val MAX = 24
        private const val WEEK_MS = 7L * 24 * 3600 * 1000
    }

    /** التقاط نص جديد من الحافظة — يُتجاهل إن كان آخر عنصر نفسه */
    fun onClip(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.length > 4000) return
        if (entries.isNotEmpty() && entries[0].text == t) return
        entries.removeAll { it.text == t }
        entries.add(0, ClipEntry(t, System.currentTimeMillis(), pinned = false))
        trimToCap()
        persist()
    }

    fun togglePin(index: Int) {
        if (index !in entries.indices) return
        val e = entries[index]
        entries[index] = e.copy(pinned = !e.pinned)
        persist()
    }

    fun remove(index: Int) {
        if (index !in entries.indices) return
        entries.removeAt(index)
        persist()
    }

    fun clearAll() {
        entries.clear()
        persist()
    }

    /** قصّ إلى الحد مع حماية المثبتين */
    private fun trimToCap() {
        if (entries.size <= MAX) return
        val pinned = entries.filter { it.pinned }.take(MAX / 2)
        val rest = entries.filter { !it.pinned }.take(MAX - pinned.size)
        entries.clear()
        entries.addAll(pinned + rest)
    }

    private fun load(): MutableList<ClipEntry> {
        val raw = sp.getString(KEY_ITEMS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ClipEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    ClipEntry(
                        text = o.getString("text"),
                        at = o.optLong("at", 0L),
                        pinned = o.optBoolean("pinned", false),
                    )
                )
            }
            // انتهاء تلقائي: غير المثبت بعد أسبوع يُسقط
            val now = System.currentTimeMillis()
            val list = out.distinctBy { it.text }
                .filter { it.pinned || now - it.at <= WEEK_MS }
                .toMutableList()
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("text", e.text)
                    .put("at", e.at)
                    .put("pinned", e.pinned)
            )
        }
        sp.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }
}
