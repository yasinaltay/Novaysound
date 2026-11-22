package com.novay.sound.data.share

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.novay.sound.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Serializable private data class SharedPlaylist(val n: String, val s: List<List<String>>)

/** Çalma listesini "novaysound://playlist?d=…" linkine ve QR koda çevirir (sıkıştırılmış). */
object PlaylistCodec {
    const val PREFIX = "novaysound://playlist?d="
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(name: String, songs: List<Song>): String {
        val data = SharedPlaylist(name, songs.filter { !it.isLocal && !it.isRadio }.map { listOf(it.id, it.title, it.artist) })
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.encodeToString(SharedPlaylist.serializer(), data).toByteArray()) }
        }.toByteArray()
        return PREFIX + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decode(text: String): Pair<String, List<Song>>? = runCatching {
        val d = text.substringAfter(PREFIX, "").takeWhile { !it.isWhitespace() }.ifBlank { return null }
        val raw = GZIPInputStream(Base64.decode(d, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).inputStream())
            .use { it.readBytes() }.decodeToString()
        val p = json.decodeFromString(SharedPlaylist.serializer(), raw)
        p.n to p.s.filter { it.size >= 3 }.map {
            Song(id = it[0], title = it[1], artist = it[2], thumbnailUrl = "https://i.ytimg.com/vi/${it[0]}/hqdefault.jpg", durationSec = 0)
        }
    }.getOrNull()

    /** QR kod (link çok uzunsa null: QR'a sığmaz, sadece link paylaşılır). */
    fun qr(text: String, size: Int = 768): Bitmap? = runCatching {
        if (text.length > 2600) return null
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 1))
        Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        }
    }.getOrNull()
}
