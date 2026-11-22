package com.novay.sound

import com.novay.sound.util.TitleCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TitleCleanerTest {
    @Test fun resmiKlipEkiSilinirSanatciAyrilir() {
        assertEquals("Git" to "Blok3", TitleCleaner.clean("BLOK3 - GİT (Official Music Video)", "Blok3"))
    }

    @Test fun duetteSanatciSoldanAlinirBaslikDuzelir() {
        val (t, a) = TitleCleaner.clean("BLOK3 x POİZİ - ÇOK GÜZEL GÖZLERİN VAR", "Blok3")
        assertEquals("Çok Güzel Gözlerin Var", t)
        assertEquals("BLOK3 x POİZİ", a)
    }

    @Test fun solTarafSanatciDegilseAyrilmaz() {
        assertEquals("Tarkan - Kuzu Kuzu" to "Müzik Dünyası", TitleCleaner.clean("Tarkan - Kuzu Kuzu", "Müzik Dünyası"))
    }

    @Test fun belirsizBuyukHarfeDokunulmaz() {
        assertEquals("KIRGINIM", TitleCleaner.clean("KIRGINIM", "Blok3").first)
    }

    @Test fun ingilizceBuyukHarfDuzelir() {
        assertEquals("Love Me", TitleCleaner.clean("LOVE ME", "Someone").first)
    }

    @Test fun koseliParantezEkiSilinir() {
        assertEquals("Ah Bu Ben", TitleCleaner.clean("Ah Bu Ben [Lyrics]", "X").first)
    }

    @Test fun normalBaslikDegismez() {
        assertEquals("Kuzu Kuzu" to "Tarkan", TitleCleaner.clean("Kuzu Kuzu", "Tarkan"))
    }

    @Test fun kopyaAnahtariAyniSarkiyiEslestirir() {
        assertEquals(TitleCleaner.key("BLOK3 - GİT (Official Video)", "Blok3"), TitleCleaner.key("Git", "Blok3"))
        assertNotEquals(TitleCleaner.key("Git", "Blok3"), TitleCleaner.key("Kayıp Kalp", "Blok3"))
    }
}
