package app.quietreader.reader

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
 * and below it. Drag the small key on the right to move it.
 */
@Composable
fun RulerLayer() {
    val settings = LocalQuiet.current.settings
    if (settings.ruler == Ruler.Off) return
    val c = LocalColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        val band = (30f * settings.bookSize / 100f * settings.lineHeight / 17f).dp
        var y by remember { mutableFloatStateOf(settings.rulerY / 100f) }
        Canvas(Modifier.fillMaxSize()) {
            val center = size.height * y
            val half = band.toPx() / 2
            if (settings.ruler == Ruler.Focus) {
                val dim = c.readerBg.copy(alpha = 0.72f)
                drawRect(dim, size = androidx.compose.ui.geometry.Size(size.width, center - half))
                drawRect(dim, topLeft = Offset(0f, center + half), size = androidx.compose.ui.geometry.Size(size.width, size.height - center - half))
            } else {
                drawRect(c.accent.copy(alpha = 0.10f), topLeft = Offset(0f, center - half), size = androidx.compose.ui.geometry.Size(size.width, half * 2))
                drawLine(c.accent.copy(alpha = 0.5f), Offset(0f, center + half), Offset(size.width, center + half), 1.5.dp.toPx())
            }
        }
        // The handle: the only part that takes touches.
        Box(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, (heightPx * y).roundToInt() - 22.dp.roundToPx()) }
                .padding(end = 4.dp).size(26.dp, 44.dp)
                .background(c.panel.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { settings.rulerY = (y * 100).roundToInt() }) { change, dy ->
                        change.consume()
                        y = (y + dy / heightPx).coerceIn(0.05f, 0.95f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text("≡", style = Type.body.copy(color = c.accent)) }
    }
}

/** A footnote, in a sheet over the page; the page doesn't move. */
@Composable
fun FootnoteSheet(html: String, onClose: () -> Unit) {
    val c = LocalColors.current
    val text = remember(html) { runCatching { AnnotatedString.fromHtml(html) }.getOrElse { AnnotatedString(html) } }
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Note", style = Type.section.copy(color = c.text))
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
