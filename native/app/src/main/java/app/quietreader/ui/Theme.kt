package app.quietreader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.quietreader.R

/*
 * Radio: a 1960s radio, after Dieter Rams's Braun. Warm grey casing, keys that press down, a
 * dotted speaker grille, numbers like a readout, and colour only where it says something:
 * orange for "you are here", green for "finished". The same tokens as the web app
 * (src/styles/styles.css, [data-style="radio"]); one CSS pixel there is one dp here.
 */
@Immutable
data class QuietColors(
    val bg: Color,          // casing
    val panel: Color,       // keys and cards
    val panelDeep: Color,   // the panel under the keys
    val text: Color,        // graphite
    val muted: Color,
    val line: Color,
    val keyEdge: Color,
    val accent: Color,      // "now": the open tab, your place
    val accentSoft: Color,
    val accentStrong: Color, // orange as text
    val onAccent: Color,
    val done: Color,        // finished
    val danger: Color,
    val readerBg: Color,
    val readerText: Color,
    val isDark: Boolean,
)

val DayColors = QuietColors(
    bg = Color(0xFFE8E6E1), panel = Color(0xFFF1EFEA), panelDeep = Color(0xFFDAD7D0),
    text = Color(0xFF24231F), muted = Color(0xFF5D5B55), line = Color(0xFFC9C5BC),
    keyEdge = Color(0xFFBDB9AF), accent = Color(0xFFC9541F), accentSoft = Color(0xFFF3DCCF),
    accentStrong = Color(0xFF9C4318), onAccent = Color.White, done = Color(0xFF4E7D34),
    danger = Color(0xFFA33A16), readerBg = Color(0xFFF2F0EB), readerText = Color(0xFF24231F),
    isDark = false,
)

val NightColors = QuietColors(
    bg = Color(0xFF1F1E1B), panel = Color(0xFF2A2925), panelDeep = Color(0xFF171613),
    text = Color(0xFFE8E5DE), muted = Color(0xFFA6A197), line = Color(0xFF3B3934),
    keyEdge = Color(0xFF46433D), accent = Color(0xFFE0763F), accentSoft = Color(0xFF3D2A20),
    accentStrong = Color(0xFFF09A68), onAccent = Color(0xFF1C1B18), done = Color(0xFF8CB874),
    danger = Color(0xFFF0956D), readerBg = Color(0xFF1C1B18), readerText = Color(0xFFE4E0D7),
    isDark = true,
)

val BlackColors = NightColors.copy(
    bg = Color.Black, panel = Color(0xFF141412), panelDeep = Color.Black, readerBg = Color.Black,
)

val LocalColors = staticCompositionLocalOf { DayColors }

/** Glides every colour to the new theme instead of jumping. */
@Composable
fun animateColors(target: QuietColors, spec: AnimationSpec<Color> = tween(260)): QuietColors {
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "theme").value
    return QuietColors(
        bg = a(target.bg), panel = a(target.panel), panelDeep = a(target.panelDeep),
        text = a(target.text), muted = a(target.muted), line = a(target.line),
        keyEdge = a(target.keyEdge), accent = a(target.accent), accentSoft = a(target.accentSoft),
        accentStrong = a(target.accentStrong), onAccent = a(target.onAccent), done = a(target.done),
        danger = a(target.danger), readerBg = a(target.readerBg), readerText = a(target.readerText),
        isDark = target.isDark,
    )
}

// Fonts, all under the SIL Open Font License and bundled, so they work offline.
// Archivo and Crimson Pro are variable fonts: Compose sets the weight axis from FontWeight.
val Archivo = FontFamily(
    Font(R.font.archivo, FontWeight.Normal),
    Font(R.font.archivo, FontWeight.Medium),
    Font(R.font.archivo, FontWeight.SemiBold),
    Font(R.font.archivo, FontWeight.Bold),
)
val DmMono = FontFamily(
    Font(R.font.dm_mono, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)
val CrimsonPro = FontFamily(
    Font(R.font.crimson_pro, FontWeight.Normal),
    Font(R.font.crimson_pro, FontWeight.SemiBold),
    Font(R.font.crimson_pro_italic, FontWeight.Normal, FontStyle.Italic),
)

/** The type scale, from the web app's sizes. */
object Type {
    val screenTitle = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.01).em)
    val brand = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.01).em)
    val pageTitle = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.01).em)
    val emptyTitle = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, letterSpacing = (-0.01).em)
    val section = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.01).em)
    val label = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val rowTitle = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val body = TextStyle(fontFamily = Archivo, fontSize = 14.sp, lineHeight = 22.sp)
    val bodyLarge = TextStyle(fontFamily = Archivo, fontSize = 15.sp, lineHeight = 24.sp)
    val hint = TextStyle(fontFamily = Archivo, fontSize = 12.sp, lineHeight = 18.sp)
    val key = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val tab = TextStyle(fontFamily = Archivo, fontSize = 12.sp)
    val overline = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.14.em)
    val mono = TextStyle(fontFamily = DmMono, fontSize = 13.sp)
    val monoLarge = TextStyle(fontFamily = DmMono, fontSize = 18.sp)
    val reading = TextStyle(fontFamily = CrimsonPro, fontSize = 20.sp, lineHeight = 31.sp)
}
