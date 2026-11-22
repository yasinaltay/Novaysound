package com.novay.sound.util

import java.util.Locale

/**
 * "BLOK3 - GİT (Official Music Video)" -> ("Git", "Blok3")
 * Temkinli: tireden sonrası sadece sol taraf sanatçıyla eşleşiyorsa ayrılır.
 */
object TitleCleaner {
    private val junk = Regex(
        """\s*[(\[【][^)\]】]*?(official|video|klip|lyric|lyrics|audio|visuali[sz]er|\bhd\b|4k|\bmv\b|music|resmi|sözleri|prod\.?)[^)\]】]*[)\]】]""",
        RegexOption.IGNORE_CASE
    )
    private val trailing = Regex("""\s+[|•]\s+.*$""")
    private val dash = Regex("""\s+[-–—]\s+""")
    private val tr = Locale.forLanguageTag("tr")
    private val small = setOf("x", "ft.", "ft", "feat.", "feat", "vs.", "&", "ve")

    fun clean(title: String, artist: String): Pair<String, String> {
        var t = title.replace(junk, "").replace(trailing, "").trim()
        var a = artist
        val parts = t.split(dash, limit = 2)
        if (parts.size == 2 && artist.isNotBlank()) {
            val left = parts[0].trim()
            val right = parts[1].trim()
            if (left.isNotEmpty() && right.isNotEmpty() && norm(left).contains(norm(artist))) {
                a = if (left.equals(artist, ignoreCase = true)) artist else pretty(left)
                t = right
            }
        }
        t = t.trim('"', '“', '”', '\'', ' ')
        t = pretty(t)
        return (t.ifBlank { title }) to (a.ifBlank { artist })
    }

    /** Aramada aynı şarkıyı yakalamak için anahtar. */
    fun key(title: String, artist: String): String {
        val (t, a) = clean(title, artist)
        return norm(t) + "|" + norm(a).take(12)
    }

    private fun norm(s: String) = s.lowercase(tr).filter { it.isLetterOrDigit() }

    /** TAMAMI BÜYÜK yazılmışsa düzgün yazıma çevir (Türkçe harfler korunur). */
    private fun pretty(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.length < 3 || letters.any { it.isLowerCase() }) return s
        val turkish = s.any { it in "ÇĞİÖŞÜ" }
        if (!turkish && 'I' in s) return s // İngilizce mi Türkçe mi belirsiz: dokunma
        val loc = if (turkish) tr else Locale.ENGLISH
        return s.lowercase(loc).split(' ').joinToString(" ") { w ->
            if (w in small) w else w.replaceFirstChar { it.titlecase(loc) }
        }
    }
}
