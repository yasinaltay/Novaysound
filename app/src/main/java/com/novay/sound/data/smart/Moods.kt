package com.novay.sound.data.smart

/** Ruh hali / aktivite listeleri: YouTube Music'te aranıp karıştırılarak çalınır. */
enum class Mood(val label: String, val emoji: String, val query: String) {
    SPORT("Spor", "💪", "türkçe spor motivasyon şarkıları"),
    FOCUS("Odak", "🎧", "lofi odaklanma çalışma müziği"),
    ROAD("Yol", "🚗", "yolculuk şarkıları türkçe"),
    CALM("Sakin", "🌙", "sakin akustik türkçe şarkılar"),
    PARTY("Parti", "🎉", "türkçe parti şarkıları"),
    ROMANTIC("Romantik", "❤️", "romantik türkçe aşk şarkıları"),
    SAD("Hüzünlü", "🌧️", "hüzünlü türkçe şarkılar"),
    RAP("Türkçe Rap", "🎤", "türkçe rap hit")
}
