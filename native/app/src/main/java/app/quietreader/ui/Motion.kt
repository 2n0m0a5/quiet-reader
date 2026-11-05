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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

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
