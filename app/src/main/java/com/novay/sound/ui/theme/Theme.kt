package com.novay.sound.ui.theme

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val DefaultAccent = Color(0xFF00E5FF)

/** AMOLED siyah tema; vurgu rengi çalan şarkının kapağından gelir. */
@Composable
fun NovayTheme(accent: Color = DefaultAccent, content: @Composable () -> Unit) {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val tint = { a: Float -> accent.copy(alpha = a).compositeOver(Color.Black) }
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = tint(0.30f),
        onPrimaryContainer = Color.White,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = tint(0.22f),
        onSecondaryContainer = Color.White,
        tertiary = accent,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = Color(0xFFB3B3B3),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF0F0F0F),
        surfaceContainerHigh = Color(0xFF171717),
        surfaceContainerHighest = Color(0xFF202020),
        outline = Color(0xFF3A3A3A),
        outlineVariant = Color(0xFF242424)
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

/** Kapak görselinden canlı vurgu rengi çıkarır, yumuşak geçişle değiştirir. */
@Composable
fun rememberArtworkAccent(url: String?, fallback: Color = DefaultAccent): State<Color> {
    val context = LocalContext.current
    var target by remember { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        target = url?.let { extractAccent(context, it) } ?: fallback
    }
    return animateColorAsState(target, tween(700), label = "accent")
}

private suspend fun extractAccent(context: Context, url: String): Color? =
    withContext(Dispatchers.Default) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url).size(128).allowHardware(false).build()
            val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
                ?.image?.toBitmap() ?: return@runCatching null
            val p = Palette.from(bitmap).generate()
            val swatch = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch
            swatch?.rgb?.let { visible(Color(it)) }
        }.getOrNull()
    }

/** Çok koyu renkleri siyah zeminde görünür olacak kadar aç. */
private fun visible(c: Color): Color =
    if (c.luminance() < 0.18f) lerp(c, Color.White, 0.35f) else c
