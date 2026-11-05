package app.quietreader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Opening Quiet. Android's splash shows the knob; the moment it ends, this takes over with the
 * same knob in the same place (so the hand-over can't be seen). Then, in under a second:
 *
 *   1. the knob turns up a little, like switching a radio on; its orange dot lights, with a click
 *   2. "quiet" settles in under it
 *   3. knob and word shrink and fly to their place in the top bar, the knob turning back down
 *      (quiet), while the library rises in behind them and the casing colour fades away
 *
 * Reduced motion: a short fade.
 */

private val Casing = Color(0xFFE8E6E1) // the splash's background (res/values/colors.xml, casing)

@Composable
fun Intro(state: AppState, onDone: () -> Unit) {
    val c = LocalColors.current
    val reduced = LocalReducedMotion.current
    val haptics = LocalHaptics.current
    val turn = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val fly = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // The app behind follows the flight (it rises in as the knob leaves).
        launch { androidx.compose.runtime.snapshotFlow { fly.value }.collect { state.introProgress = it } }
        if (reduced) { fly.animateTo(1f, tween(220)); onDone(); return@LaunchedEffect }
        delay(40)
        launch { word.animateTo(1f, tween(320, delayMillis = 140)) }
        launch { delay(150); glow.animateTo(1f, tween(200)) }
        // Up, with the knob's own weight: a little past, then settling (on a fixed timeline, so
        // the whole opening stays under a second).
        val up = launch { turn.animateTo(48f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
        delay(280)
        haptics?.tick() // the detent, as the dot lights
        delay(70)
        up.cancel()
        // Away to the top bar, turning back down as it lands.
        launch { turn.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 240f)) }
        launch { glow.animateTo(0f, tween(240)) }
        fly.animateTo(1f, spring(dampingRatio = 0.94f, stiffness = 240f, visibilityThreshold = 0.002f))
        onDone()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val p = fly.value
        // The casing colour (the splash) gives way to the app.
        Box(Modifier.fillMaxSize().background(Casing.copy(alpha = (1f - p * 1.6f).coerceIn(0f, 1f))))

        val startKnob = with(density) { 130.dp.toPx() }
        val knobTo: Rect? = state.coverBounds["brand-knob"]
        val wordTo: Rect? = state.coverBounds["brand-word"]
        val from = Offset(w / 2f, h / 2f)
        val knobCenter = knobTo?.center ?: from
        val knobSize = knobTo?.width ?: 0f
        val cx = from.x + (knobCenter.x - from.x) * p
        val cy = from.y + (knobCenter.y - from.y) * p
        val size = startKnob + (knobSize - startKnob) * p
        val fade = if (knobTo == null) 1f - p else 1f
        val knobDp = with(density) { startKnob.toDp() }
        // The splash knob is the light (Day) one; in Night it turns into the dark one on the way.
        Box(
            Modifier.size(knobDp).graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                val s = size / startKnob
                scaleX = s; scaleY = s
                translationX = cx - startKnob / 2f
                translationY = cy - startKnob / 2f
                alpha = fade
            },
        ) {
            KnobMark(Modifier.fillMaxSize(), turn = turn.value, glow = glow.value, colors = DayColors)
            if (c.isDark) KnobMark(Modifier.fillMaxSize().graphicsLayer { alpha = p }, turn = turn.value, glow = glow.value, colors = c)
        }

        var wordSize by remember { mutableStateOf(IntSize.Zero) }
        val startScale = 1.7f
        val wordFrom = Offset(w / 2f, h / 2f + startKnob * 0.62f)
        val wordCenter = wordTo?.center ?: wordFrom
        val wx = wordFrom.x + (wordCenter.x - wordFrom.x) * p
        val wy = wordFrom.y + (wordCenter.y - wordFrom.y) * p
        val ws = startScale + (1f - startScale) * p
        Text(
            "quiet",
            style = Type.brand.copy(color = if (p > 0.5f) c.text else DayColors.text),
            modifier = Modifier.onSizeChanged { wordSize = it }.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                scaleX = ws; scaleY = ws
                translationX = wx - wordSize.width / 2f
                translationY = wy - wordSize.height / 2f + (1f - word.value) * 18f
                alpha = word.value * fade
            },
        )
    }
}
