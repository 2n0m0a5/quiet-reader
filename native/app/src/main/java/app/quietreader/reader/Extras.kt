package app.quietreader.reader

import app.quietreader.ui.asHeading
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.quietreader.LocalQuiet
import app.quietreader.Ruler
import app.quietreader.ui.IconKey
import app.quietreader.ui.Icons
import app.quietreader.ui.LocalColors
import app.quietreader.ui.Type
import kotlin.math.roundToInt

/**
 * The reading ruler: Line marks the line you're on with a soft band; Focus dims the page above
 * and below it. To move it, hold a finger on it for a moment (anywhere along it, either hand),
 * then drag. A quick touch or a scroll that starts on it goes to the book as usual
 * (MainActivity.dispatchTouchEvent decides, before the book sees the touch).
 */
@Composable
fun RulerLayer() {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    if (settings.ruler == Ruler.Off) return
    val c = LocalColors.current
    val grip = quiet.ruler
    var y by remember { mutableFloatStateOf(settings.rulerY / 100f) }
    var top by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }
    val band = (30f * settings.bookSize / 100f * settings.lineHeight / 17f).dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val margin = with(density) { 10.dp.toPx() }
    val half = with(density) { band.toPx() } / 2
    // Where the band is, for the window's touch handling.
    grip.band = (top + height * y - half - margin)..(top + height * y + half + margin)
    grip.onMove = { dy -> y = (y + dy / height).coerceIn(0.05f, 0.95f) }
    grip.onDrop = { settings.rulerY = (y * 100).roundToInt() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { grip.band = null; grip.grabbed = false } }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { top = it.positionInWindow().y; height = it.size.height.toFloat().coerceAtLeast(1f) }) {
        val center = size.height * y
        if (settings.ruler == Ruler.Focus) {
            val dim = c.readerBg.copy(alpha = 0.72f)
            drawRect(dim, size = androidx.compose.ui.geometry.Size(size.width, center - half))
            drawRect(dim, topLeft = Offset(0f, center + half), size = androidx.compose.ui.geometry.Size(size.width, size.height - center - half))
        } else {
            drawRect(c.accent.copy(alpha = if (grip.grabbed) 0.2f else 0.10f), topLeft = Offset(0f, center - half), size = androidx.compose.ui.geometry.Size(size.width, half * 2))
        }
        drawLine(c.accent.copy(alpha = if (grip.grabbed) 0.9f else 0.5f), Offset(0f, center + half), Offset(size.width, center + half), (if (grip.grabbed) 2.5f else 1.5f).dp.toPx())
        if (grip.grabbed) drawLine(c.accent.copy(alpha = 0.9f), Offset(0f, center - half), Offset(size.width, center - half), 2.5f.dp.toPx())
    }
}

/** A footnote, in a sheet over the page; the page doesn't move. */
@Composable
fun FootnoteSheet(html: String, onClose: () -> Unit) {
    val c = LocalColors.current
    val text = remember(html) { runCatching { AnnotatedString.fromHtml(html) }.getOrElse { AnnotatedString(html) } }
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Note", style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
            Spacer(Modifier.height(10.dp))
            Text(text, style = Type.reading.copy(color = c.readerText, fontSize = Type.bodyLarge.fontSize, lineHeight = Type.bodyLarge.lineHeight))
        }
    }
}

/** A picture from the book, full screen: pinch to zoom, drag to move, double-tap to zoom in or back. */
@Composable
fun ImageViewer(image: ImageBitmap, onClose: () -> Unit) {
    BackHandler { onClose() }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, move, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        pan = if (scale == 1f) Offset.Zero else pan + move
    }
    Box(Modifier.fillMaxSize().background(Color(0xF2141412))) {
        Image(
            image, "Picture", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; pan = Offset.Zero } else scale = 2.5f }) }
                .transformable(transform)
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y },
        )
        IconKey(Icons.Close, "Close", onClose, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(12.dp))
    }
}
