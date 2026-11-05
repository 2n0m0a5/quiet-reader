package app.quietreader.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.togetherWith

/*
 * Motion, Radio style: things glide and stop, they never bounce (damping ratio 1). The
 * stiffness values match Apple's spring "response" (stiffness = (2π / response)²) used by the
 * web app's src/ui/motion.ts: 0.35 s for moves, 0.2 s for presses.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

object Springs {
    const val MOVE = 322f   // response 0.35 s
    const val QUICK = 987f  // response 0.2 s
}

/** A glide for moving things; instant when the reader (or phone) asks for less motion. */
/** A small, lively spring for things that answer a tap (an icon's bounce, a check mark popping in). */
fun <T> pop(): androidx.compose.animation.core.SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 520f)

/**
 * Moving between screens side by side (tabs): the new one glides in a short way from the side
 * it lives on, and the old one drifts the other way as it fades. A short distance (8%) reads as
 * "next door", not as a new place, which keeps tabs feeling like one space.
 */
fun sideways(forward: Boolean, reduced: Boolean): androidx.compose.animation.ContentTransform {
    if (reduced) return androidx.compose.animation.fadeIn(snap()) togetherWith androidx.compose.animation.fadeOut(snap())
    val move = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Springs.MOVE)
    val sign = if (forward) 1 else -1
    return (androidx.compose.animation.slideInHorizontally(move) { sign * it / 12 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 30)))
        .togetherWith(androidx.compose.animation.slideOutHorizontally(move) { -sign * it / 12 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)))
}
@Composable
@ReadOnlyComposable
fun <T> glide(stiffness: Float = Springs.MOVE): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)

/** The vibration motor: a light click for keys, a firmer one to confirm. Off when the reader turns Vibration off. */
open class Haptics(context: Context, private val enabled: () -> Boolean) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    fun tick() = buzz(confirm = false)
    fun confirm() = buzz(confirm = true)

    private fun buzz(confirm: Boolean) {
        if (enabled()) perform(confirm)
    }

    /** Runs the motor. Tests replace this to count clicks. */
    protected open fun perform(confirm: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect(v, confirm)) }
    }

    /**
     * Android's own click when the phone says it has one; otherwise a short pulse of our own.
     * Many phones (Xiaomi among them) answer "unknown" for the click, and then play it so
     * faintly, or not at all, that taps seem to vibrate only sometimes.
     */
    private fun effect(v: Vibrator, confirm: Boolean): VibrationEffect {
        val click = if (confirm) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
        val supported = Build.VERSION.SDK_INT >= 30 && v.areAllEffectsSupported(click) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
        if (supported) return VibrationEffect.createPredefined(click)
        val amplitude = if (!v.hasAmplitudeControl()) VibrationEffect.DEFAULT_AMPLITUDE else if (confirm) 255 else 180
        return VibrationEffect.createOneShot(if (confirm) 28L else 14L, amplitude)
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

/**
 * The back swipe, followed as it happens (Android 14+ reports how far your finger has gone): the
 * screen moves with your finger, and when you let go it either finishes leaving or springs back
 * if you changed your mind. On older Android it simply finishes. [progress] is 0 (still) to 1 (gone).
 */
class BackSwipe {
    var progress by androidx.compose.runtime.mutableFloatStateOf(0f)
        internal set
    /** True right after a swipe finished, so the screen that leaves doesn't animate a second time. */
    var finishedBySwipe by androidx.compose.runtime.mutableStateOf(false)
        internal set
    val active get() = progress > 0f
    fun reset() { progress = 0f; finishedBySwipe = false }
}

@Composable
fun rememberBackSwipe(enabled: Boolean, onBack: () -> Unit): BackSwipe {
    val swipe = androidx.compose.runtime.remember { BackSwipe() }
    val latest = androidx.compose.runtime.rememberUpdatedState(onBack)
    val reduced = LocalReducedMotion.current
    androidx.activity.compose.PredictiveBackHandler(enabled) { events ->
        val anim = androidx.compose.animation.core.Animatable(0f)
        try {
            events.collect { e -> swipe.progress = e.progress; anim.snapTo(e.progress) }
            // Let go: finish the move from where the finger left it, then go back.
            if (swipe.progress > 0f && !reduced) {
                anim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)) { swipe.progress = value }
                swipe.finishedBySwipe = true
            }
            latest.value()
            swipe.progress = 0f
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Changed your mind: spring back into place.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                anim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Springs.MOVE)) { swipe.progress = value }
            }
            throw e
        }
    }
    return swipe
}
