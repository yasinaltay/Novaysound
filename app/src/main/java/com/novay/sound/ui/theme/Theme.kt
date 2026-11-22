package com.novay.sound.ui.theme

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- Marka renkleri (logo gradyanı) ----
val BrandCyan = Color(0xFF00E5FF)
val BrandViolet = Color(0xFF7C4DFF)
val BrandGradient = Brush.linearGradient(listOf(BrandCyan, BrandViolet))

fun defaultAccent(dark: Boolean): Color = if (dark) BrandCyan else Color(0xFF5B4BFF)

/** Vurgu rengini temaya göre okunur hale getir. */
fun adaptAccent(c: Color, dark: Boolean): Color = when {
    dark && c.luminance() < 0.18f -> lerp(c, Color.White, 0.35f)
    !dark && c.luminance() > 0.40f -> lerp(c, Color.Black, 0.40f)
    else -> c
}

private fun darkScheme(accent: Color, pureBlack: Boolean): ColorScheme {
    val bg = if (pureBlack) Color.Black else Color(0xFF0E0F13)
    val tint = { a: Float -> accent.copy(alpha = a).compositeOver(bg) }
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    return darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = tint(0.30f), onPrimaryContainer = Color.White,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = tint(0.22f), onSecondaryContainer = Color.White,
        tertiary = BrandViolet,
        background = bg, onBackground = Color.White,
        surface = bg, onSurface = Color.White,
        surfaceVariant = Color(0xFF1C1D22), onSurfaceVariant = Color(0xFFB4B6BE),
        surfaceContainerLowest = bg,
        surfaceContainerLow = if (pureBlack) Color(0xFF0A0A0B) else Color(0xFF131419),
        surfaceContainer = if (pureBlack) Color(0xFF101012) else Color(0xFF17181E),
        surfaceContainerHigh = if (pureBlack) Color(0xFF18181B) else Color(0xFF1E1F26),
        surfaceContainerHighest = if (pureBlack) Color(0xFF222226) else Color(0xFF282A32),
        outline = Color(0xFF3B3D45), outlineVariant = Color(0xFF26272D)
    )
}

private fun lightScheme(accent: Color): ColorScheme {
    val bg = Color(0xFFF6F7FB)
    val tint = { a: Float -> accent.copy(alpha = a).compositeOver(Color.White) }
    return lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = tint(0.18f), onPrimaryContainer = Color(0xFF101114),
        secondary = accent, onSecondary = Color.White,
        secondaryContainer = tint(0.16f), onSecondaryContainer = Color(0xFF101114),
        tertiary = BrandViolet,
        background = bg, onBackground = Color(0xFF111318),
        surface = bg, onSurface = Color(0xFF111318),
        surfaceVariant = Color(0xFFE6E8EF), onSurfaceVariant = Color(0xFF5B606E),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F2F7),
        surfaceContainer = Color(0xFFECEEF4),
        surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color(0xFFE3E5EC),
        outline = Color(0xFFC4C7D0), outlineVariant = Color(0xFFE0E2E9)
    )
}

private val NovayTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun NovayTheme(
    darkTheme: Boolean,
    pureBlack: Boolean = true,
    accent: Color = defaultAccent(darkTheme),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(accent, pureBlack) else lightScheme(accent),
        typography = NovayTypography,
        content = content
    )
}

/**
 * "Novay Sound" yazısı: koyu temada açık (parlak) cyan→lila, açık temada koyu petrol→mor.
 * Böylece iki temada da zeminle yeterli kontrast olur.
 */
@Composable
fun brandTitleStyle(): TextStyle {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = if (dark) listOf(Color(0xFF6FF3FF), Color(0xFFB9A4FF)) else listOf(Color(0xFF00788C), Color(0xFF4B2BC8))
    return TextStyle(
        brush = Brush.linearGradient(colors),
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp
    )
}

/** Kapak görselinden vurgu rengi (temaya uyarlanmış, yumuşak geçişli). */
@Composable
fun rememberArtworkAccent(url: String?, enabled: Boolean, dark: Boolean): State<Color> {
    val context = LocalContext.current
    var raw by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, enabled) {
        raw = if (enabled && url != null) extractColor(context, url) else null
    }
    val target = raw?.let { adaptAccent(it, dark) } ?: defaultAccent(dark)
    return animateColorAsState(target, tween(700), label = "accent")
}

private suspend fun extractColor(context: Context, url: String): Color? =
    withContext(Dispatchers.Default) {
        runCatching {
            val request = ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build()
            val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
                ?.image?.toBitmap() ?: return@runCatching null
            val p = Palette.from(bitmap).generate()
            (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb?.let { Color(it) }
        }.getOrNull()
    }
