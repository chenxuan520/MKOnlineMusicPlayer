package com.mkonline.player.data

/** 解析 LRC 歌词，支持 [mm:ss] / [mm:ss.xx] / [mm:ss.xxx]，一行多时间戳，忽略元信息行。 */
object LrcParser {

    private val tagRegex = Regex("""\[(ti|ar|al|by|offset|re|ve):[^\]]*]""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String?, tlyric: String? = null): List<LyricLine> {
        val merged = LinkedHashMap<Long, String>()
        parseInto(lrc, merged, isTranslation = false)
        if (!tlyric.isNullOrBlank()) {
            // 翻译行合并为 “原文 / 译文”
            val trans = LinkedHashMap<Long, String>()
            parseInto(tlyric, trans, isTranslation = true)
            if (trans.isNotEmpty()) {
                trans.forEach { (t, txt) ->
                    val origin = merged[t]
                    if (origin != null) merged[t] = "$origin / $txt"
                }
            }
        }
        return merged.entries
            .filter { it.value.isNotBlank() }
            .sortedBy { it.key }
            .map { LyricLine(it.key, it.value.trim()) }
    }

    private fun parseInto(lrc: String?, out: MutableMap<Long, String>, isTranslation: Boolean) {
        if (lrc.isNullOrBlank()) return
        lrc.lineSequence().forEach { raw ->
            val line = tagRegex.replace(raw, "")
            val stamps = timeRegex.findAll(line).toList()
            if (stamps.isEmpty()) return@forEach
            val text = timeRegex.replace(line, "").trim()
            if (text.isEmpty()) return@forEach
            stamps.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: return@forEach
                val sec = m.groupValues[2].toLongOrNull() ?: return@forEach
                val msPart = m.groupValues[3]
                val ms = when (msPart.length) {
                    1 -> (msPart.toLongOrNull() ?: 0) * 100
                    2 -> (msPart.toLongOrNull() ?: 0) * 10
                    else -> msPart.toLongOrNull() ?: 0
                }
                val t = (min * 60 + sec) * 1000 + ms
                if (!isTranslation || !out.containsKey(t)) out[t] = text
            }
        }
    }
}
