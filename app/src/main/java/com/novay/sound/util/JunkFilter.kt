package com.novay.sound.util

/** Öneri listelerinden elenecek "şarkı olmayan" sonuçlar: mix, derleme, slowed, karaoke, çok uzun/kısa videolar. */
object JunkFilter {
    private val JUNK = Regex(
        "(?i)(\\bmix\\b|mixtape|full album|tüm albüm|playlist|çalma listesi|\\b\\d+\\s*(saat|hour|hours)\\b|saatlik|karışık|" +
            "compilation|non-?stop|live stream|canlı yayın|podcast|slowed|reverb|sped up|speed up|\\b8d\\b|nightcore|" +
            "karaoke|instrumental|enstrümantal|altyapı|type beat|\\bcover\\b|tiktok|shorts|reaction|tepki)"
    )

    fun isJunk(title: String): Boolean = JUNK.containsMatchIn(title)

    /** Süre bilinmiyorsa (0) geçer; biliniyorsa 1–10 dk arası şarkı sayılır. */
    fun isSongLength(durationSec: Long) = durationSec == 0L || durationSec in 60..600
}
