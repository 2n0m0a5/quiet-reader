package app.quietreader.reader

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import app.quietreader.ui.LocalColors
import app.quietreader.ui.LocalHaptics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/*
 * The page curl, like paper: the bottom corner of the page follows your fingertip, the page
 * folds along the line halfway between the corner and your finger, and its back shows on the
 * flap with a soft shadow. Let go past about 40% (or flick) and the page turns; otherwise it
 * settles back. The same as 0.4.2's curl, drawn natively.
 *
 * How: a picture of the current page is taken (PixelCopy), the real page is turned underneath
 * at once, and the picture is folded away on top. Going back is the same played in reverse:
 * the picture (the page you leave) is shown only where the returning page hasn't landed yet.
 */

/** The fold for a page of [size] whose bottom-right corner was pulled to [p]. */
internal class Fold(size: Size, p: Offset) {
    val w = size.width
    val h = size.height
    private val corner = Offset(w, h)
    private val d = corner - p
    private val len = hypot(d.x, d.y)
    val active = len > 1f
    val n = if (active) Offset(d.x / len, d.y / len) else Offset(1f, 0f)
    val m = (corner + p) / 2f

    private fun side(x: Offset) = (x - m).x * n.x + (x - m).y * n.y
    private fun reflect(x: Offset): Offset { val s = side(x); return Offset(x.x - 2 * s * n.x, x.y - 2 * s * n.y) }

    /** Clips the page rectangle to one side of the fold: true = the corner's side (the part that turns). */
    fun piece(cornerSide: Boolean): List<Offset> {
        val rect = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))
        val keep = { x: Offset -> if (cornerSide) side(x) > 0 else side(x) <= 0 }
        val out = mutableListOf<Offset>()
        for (i in rect.indices) {
            val a = rect[i]; val b = rect[(i + 1) % rect.size]
            val ka = keep(a); val kb = keep(b)
            if (ka) out += a
            if (ka != kb) {
                val sa = side(a); val sb = side(b)
                val t = sa / (sa - sb)
                out += Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        return out
    }

    fun flap(): List<Offset> = piece(true).map(::reflect)
}

internal fun pathOf(points: List<Offset>) = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

/** Draws one frame: [page] is the picture; [forward] says which side of the fold it shows on. */
internal fun DrawScope.drawCurl(page: ImageBitmap, p: Offset, forward: Boolean, paper: Color) {
    val fold = Fold(size, p)
    if (!fold.active) {
        if (forward) drawImage(page, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        return
    }
    val pageSide = pathOf(fold.piece(cornerSide = !forward))
    clipPath(pageSide) { drawImage(page, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }

    // The shadow the flap casts on the page under it, along the fold.
    val shadowReach = 28f * density
    val under = pathOf(fold.piece(cornerSide = true))
    clipPath(under) {
        drawRect(Brush.linearGradient(
            listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
            start = fold.m, end = fold.m + fold.n * shadowReach,
        ))
    }
    // The flap: the back of the page, darker at the fold like a cylinder.
    val flap = fold.flap()
    if (flap.size >= 3) {
        val path = pathOf(flap)
        drawPath(path, paper)
        drawPath(path, Brush.linearGradient(
            listOf(Color.Black.copy(alpha = 0.18f), Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.06f)),
            start = fold.m, end = fold.m - fold.n * (size.width * 0.35f),
        ))
        drawPath(path, Color.Black.copy(alpha = 0.12f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    }
}

/** A picture of the window as it is now (the page as you see it). */
internal suspend fun capture(activity: Activity): ImageBitmap? {
    val view = activity.window.decorView
    if (view.width == 0 || view.height == 0) return null
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    val done = CompletableDeferred<Boolean>()
    runCatching {
        PixelCopy.request(activity.window, bitmap, { result -> done.complete(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
    }.onFailure { return null }
    return if (done.await()) bitmap.asImageBitmap() else null
}

/**
 * The layer over the page while Curl is on (page mode). It takes horizontal drags and edge taps
 * and turns pages with the curl; other taps go through to the page ([passTap]).
 */
@Composable
fun CurlLayer(state: ReaderState, passTap: (Offset) -> Unit) {
    val activity = LocalContext.current as Activity
    val haptics = LocalHaptics.current
    val paper = LocalColors.current.readerBg.let { Color(it.red * 0.96f, it.green * 0.95f, it.blue * 0.93f) }
    val scope = rememberCoroutineScope()
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    var forward by remember { mutableStateOf(true) }
    val px = remember { Animatable(0f) }
    val py = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }

    // The point the corner is pulled to, for a finger at x (forward: under the finger; back: mirrored).
    fun target(x: Float, w: Float) = if (forward) x else x * 2 - w

    suspend fun finish(turn: Boolean, w: Float, h: Float) {
        val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f)
        val endX = if (forward == turn) -w else w
        scope.launch { py.animateTo(if (forward == turn) h * 0.8f else h, spec) }
        px.animateTo(endX, spec)
        if (!turn) state.jump(!forward) // put the page back
        delay(60)
        picture = null
        busy = false
        if (turn) haptics?.tick()
    }

    /** A turn by tap or volume key: the corner sweeps across on its own. */
    suspend fun sweep(goForward: Boolean, w: Float, h: Float) {
        if (busy) return
        busy = true
        forward = goForward
        val shot = capture(activity) ?: run { busy = false; state.turn(goForward); return }
        if (!state.jump(goForward)) { busy = false; return }
        picture = shot
        if (goForward) { px.snapTo(w); py.snapTo(h * 0.96f); px.animateTo(-w, tween(520)) }
        else { px.snapTo(-w); py.snapTo(h * 0.8f); px.animateTo(w, tween(520)) }
        picture = null
        busy = false
    }
    state.curlTurn = { goForward -> scope.launch { sweep(goForward, activity.window.decorView.width.toFloat(), activity.window.decorView.height.toFloat()) } }

    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            val w = size.width.toFloat(); val h = size.height.toFloat()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var dragging = false
                var last = start
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    last = change.position
                    if (!dragging && abs(change.position.x - start.x) > viewConfiguration.touchSlop && !busy) {
                        dragging = true
                        busy = true
                        forward = change.position.x < start.x
                        scope.launch {
                            val shot = capture(activity)
                            if (shot == null || !state.jump(forward)) { busy = false; dragging = false; return@launch }
                            picture = shot
                            px.snapTo(target(last.x, w)); py.snapTo(last.y.coerceIn(h * 0.3f, h))
                        }
                    }
                    if (dragging) {
                        change.consume()
                        if (picture != null) scope.launch {
                            px.snapTo(target(change.position.x, w)); py.snapTo(change.position.y.coerceIn(h * 0.3f, h))
                        }
                    }
                    if (!change.pressed) break
                }
                if (dragging) {
                    val v = tracker.calculateVelocity().x
                    val travelled = (if (forward) start.x - last.x else last.x - start.x) / w
                    val turn = travelled > 0.4f || (if (forward) v < -800f else v > 800f)
                    scope.launch {
                        // Wait for the picture if the finger was very quick.
                        repeat(20) { if (picture == null && busy) delay(16) }
                        if (picture != null) finish(turn, w, h) else busy = false
                    }
                } else if (!busy && abs(last.x - start.x) < viewConfiguration.touchSlop) {
                    // A tap: edges turn the page, the middle goes through to the page.
                    when {
                        start.x < w * 0.28f -> scope.launch { sweep(false, w, h) }
                        start.x > w * 0.72f -> scope.launch { sweep(true, w, h) }
                        else -> passTap(start)
                    }
                }
            }
        },
    ) {
        val pic = picture ?: return@Canvas
        drawCurl(pic, Offset(px.value, py.value), forward, paper)
    }
}
