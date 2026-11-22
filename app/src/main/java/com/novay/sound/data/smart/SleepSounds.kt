package com.novay.sound.data.smart

/** Uyku sesleri: YouTube'daki uzun (saatlerce) kayıtlardan çalınır, tekrar modunda. */
enum class SleepSound(val label: String, val emoji: String, val query: String) {
    RAIN("Yağmur", "🌧️", "rain sounds for sleeping 10 hours"),
    OCEAN("Dalgalar", "🌊", "ocean waves sounds for sleep 10 hours"),
    WHITE("Beyaz gürültü", "📻", "white noise 10 hours"),
    BROWN("Kahverengi gürültü", "🟤", "brown noise 10 hours"),
    FIRE("Şömine", "🔥", "fireplace crackling sounds 10 hours"),
    FOREST("Orman", "🌲", "forest night sounds 10 hours"),
    FAN("Vantilatör", "🌀", "fan noise for sleeping 10 hours"),
    STORM("Fırtına", "⛈️", "thunderstorm sounds for sleeping 10 hours")
}
