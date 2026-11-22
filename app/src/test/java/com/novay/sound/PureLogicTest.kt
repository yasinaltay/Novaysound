package com.novay.sound

import com.novay.sound.data.lyrics.LrcParser
import com.novay.sound.data.model.Song
import com.novay.sound.data.share.PlaylistCodec
import com.novay.sound.update.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test fun zamanlarVeSatirlarOkunur() {
        val lines = LrcParser.parse("[00:12.50]Merhaba\n[01:02.00][01:30.10]Nakarat\nzamansız satır")
        assertEquals(listOf(12_500L, 62_000L, 90_100L), lines.map { it.timeMs })
        assertEquals(listOf("Merhaba", "Nakarat", "Nakarat"), lines.map { it.text })
    }

    @Test fun bosMetinBosListe() = assertTrue(LrcParser.parse("").isEmpty())
}

class VersionsTest {
    @Test fun sayisalKarsilastirma() {
        assertTrue(Versions.isNewer("0.10.0", "0.9.3"))
        assertTrue(Versions.isNewer("v1.0", "0.9.9"))
        assertFalse(Versions.isNewer("0.9.1", "0.9.1"))
        assertFalse(Versions.isNewer("0.9.0", "0.9.1"))
    }
}

class PlaylistCodecTest {
    private val songs = listOf(
        Song("abc123", "Git", "Blok3", null, 159),
        Song("local:5", "Yerel", "Ben", null, 100),
        Song("radio:x", "Kral FM", "Canlı", null, 0, streamUrl = "https://x"),
        Song("def456", "Kayıp Kalp", "Blok3", null, 154)
    )

    @Test fun gidipGelir() {
        val link = PlaylistCodec.encode("Yol listesi", songs)
        assertTrue(link.startsWith(PlaylistCodec.PREFIX))
        val (name, back) = PlaylistCodec.decode("Bak bu listeye: $link 🎵")!!
        assertEquals("Yol listesi", name)
        // yerel dosya ve radyo paylaşılmaz
        assertEquals(listOf("abc123", "def456"), back.map { it.id })
        assertEquals("Kayıp Kalp", back[1].title)
    }

    @Test fun bozukLinkNull() = assertNull(PlaylistCodec.decode(PlaylistCodec.PREFIX + "bozukveri!!"))
}
