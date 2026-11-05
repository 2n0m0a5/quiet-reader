package app.quietreader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle

/** A tap target with Quiet's light click (every tap in the app vibrates the same way). */
fun Modifier.click(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier = composed {
    val haptics = LocalHaptics.current
    clickable(enabled = enabled, role = role) { haptics?.tick(); onClick() }
}

/** A title TalkBack can jump between (swipe by headings). */
fun Modifier.asHeading(): Modifier = semantics { heading() }

val KeyShape = RoundedCornerShape(8.dp)
val SmallShape = RoundedCornerShape(6.dp)
val CardShape = RoundedCornerShape(10.dp)

/** The speaker grille: a dot every 7 dp, graphite at 18%. */
fun Modifier.grille(colors: QuietColors): Modifier = drawBehind {
    val step = 7.dp.toPx()
    val r = 1.25.dp.toPx()
    val dot = colors.text.copy(alpha = 0.18f)
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(dot, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

/** A hairline along the bottom (or top) edge. */
fun Modifier.hairline(color: Color, top: Boolean = false): Modifier = drawBehind {
    val y = if (top) 0f else size.height - 1.dp.toPx() / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

enum class KeyKind { Normal, Primary, Danger }

/**
 * A key: it sits up on a 1 dp edge and presses down onto it, with a click from the
 * vibration motor. The building block of every button in Radio.
 */
@Composable
fun Key(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: KeyKind = KeyKind.Normal,
    enabled: Boolean = true,
    shape: RoundedCornerShape = KeyShape,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dy by animateDpAsState(if (pressed && enabled) 1.dp else 0.dp, tween(80), label = "key")
    val (face, edge, ink, under) = when (kind) {
        KeyKind.Normal -> listOf(c.panel, c.keyEdge, c.text, c.keyEdge)
        KeyKind.Primary -> listOf(c.text, c.text, c.bg, Color.Black)
        KeyKind.Danger -> listOf(c.panel, c.keyEdge, c.danger, c.keyEdge)
    }
    // propagateMinConstraints: a key given a width (weight, fillMaxWidth) fills it.
    Box(modifier.alpha(if (enabled) 1f else 0.45f), propagateMinConstraints = true) {
        // The edge under the key, showing 1 dp below it.
        Box(Modifier.matchParentSize().offset(y = 1.dp).background(under, shape))
        Row(
            Modifier
                .offset(y = dy)
                .background(face, shape)
                .border(1.dp, edge, shape)
                .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = contentDescription) {
                    haptics?.tick()
                    onClick()
                }
                .padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides ink, LocalTextStyle provides Type.key.copy(color = ink)) {
                content()
            }
        }
    }
}

/** A key with words on it. */
@Composable
fun TextKey(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: KeyKind = KeyKind.Normal, enabled: Boolean = true) =
    Key(onClick, modifier, kind, enabled) {
        // One line; shrinks a little rather than wrap when the key is narrow.
        BasicText(text, style = LocalTextStyle.current, maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = LocalTextStyle.current.fontSize, stepSize = 0.5.sp))
    }

/** A square key with an icon, like the top bar's Day/Night key. */
@Composable
fun IconKey(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: KeyKind = KeyKind.Normal, size: Dp = 44.dp) =
    Key(onClick, modifier, kind, padding = PaddingValues(0.dp), contentDescription = label) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(18.dp), tint = LocalContentColor.current)
        }
    }

/**
 * Choices: a recessed track; the chosen one is a raised key with an orange light. Wraps into
 * rows of [perRow] (the web app shows five themes as 3 + 2).
 */
@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, perRow: Int = 4) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    Column(
        modifier
            .fillMaxWidth()
            .background(c.panelDeep, KeyShape)
            .selectableGroup()
            .padding(3.dp),
    ) {
        options.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (value, label) ->
                    val on = value == selected
                    val face by animateColorAsState(if (on) c.panel else c.panelDeep, glide(), label = "choice")
                    val edge by animateColorAsState(if (on) c.keyEdge else c.panelDeep, glide(), label = "choice-edge")
                    Box(
                        Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 38.dp)
                            .drawBehind {
                                if (on) drawRoundRect(c.keyEdge, topLeft = Offset(0f, 1.dp.toPx()), size = size,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                            }
                            .background(face, SmallShape)
                            .border(1.dp, edge, SmallShape)
                            // "Selected" for TalkBack, one of a group.
                            .selectable(selected = on, role = Role.RadioButton) {
                                if (!on) { haptics?.tick(); onSelect(value) }
                            }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (on) {
                                Box(Modifier.size(6.dp).background(c.accent, RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(6.dp))
                            }
                            // One line; shrinks a little rather than wrap at large text sizes.
                            BasicText(
                                label,
                                style = Type.hint.copy(
                                    color = if (on) c.text else c.muted,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = Type.key.fontSize,
                                ),
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = Type.key.fontSize, stepSize = 0.5.sp),
                            )
                        }
                    }
                }
                // Keep keys the same width in a short last row.
                repeat(perRow - row.size) { if (options.size > perRow) Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A dial: a recessed track with a tuning scale under it and a key you slide, with an orange
 * line on it like the needle. It moves in [step]s and clicks at each one. [marks] get a longer
 * tick (for example the default, 100%).
 */
@Composable
fun Dial(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    marks: List<Float> = emptyList(),
) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val steps = ((range.endInclusive - range.start) / step).roundToInt()
    fun snapTo(x: Float, width: Float, thumb: Float) {
        val f = ((x - thumb / 2) / (width - thumb)).coerceIn(0f, 1f)
        val v = (range.start + (f * steps).roundToInt() * step).coerceIn(range.start, range.endInclusive)
        if (abs(v - current) > step / 4) { haptics?.tick(); change(v) }
    }
    val thumbW = 22.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps - 1)
                setProgress { v -> change(((v - range.start) / step).roundToInt() * step + range.start); true }
            }
            .pointerInput(range, step) {
                detectTapGestures { snapTo(it.x, size.width.toFloat(), thumbW.toPx()) }
            }
            .pointerInput(range, step) {
                detectHorizontalDragGestures(onDragStart = { snapTo(it.x, size.width.toFloat(), thumbW.toPx()) }) { ch, _ ->
                    ch.consume()
                    snapTo(ch.position.x, size.width.toFloat(), thumbW.toPx())
                }
            },
    ) {
        val travel = maxWidth - thumbW
        val f = (value - range.start) / (range.endInclusive - range.start)
        val x by animateDpAsState(travel * f, glide(Springs.QUICK), label = "dial")
        // The track and the tuning scale under it.
        Canvas(Modifier.fillMaxSize()) {
            val half = thumbW.toPx() / 2
            val top = 10.dp.toPx()
            drawRoundRect(c.panelDeep, Offset(half, top), androidx.compose.ui.geometry.Size(size.width - 2 * half, 6.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            for (i in 0..steps) {
                val v = range.start + i * step
                val tx = half + (size.width - 2 * half) * i / steps
                val long = marks.any { abs(it - v) < step / 4 }
                drawLine(if (long) c.text else c.keyEdge, Offset(tx, 24.dp.toPx()), Offset(tx, (if (long) 34 else 29).dp.toPx()),
                    (if (long) 1.5f else 1f).dp.toPx())
            }
        }
        // The key, with its orange needle.
        Box(Modifier.offset(x = x, y = 1.dp).size(thumbW, 24.dp)) {
            Box(Modifier.fillMaxSize().offset(y = 1.dp).background(c.keyEdge, SmallShape))
            Box(Modifier.fillMaxSize().background(c.panel, SmallShape).border(1.dp, c.keyEdge, SmallShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(2.dp, 12.dp).background(c.accent, RoundedCornerShape(1.dp)))
            }
        }
    }
}

/** On/off: a recessed track with a key that slides; orange when on. */
@Composable
fun Switch(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, standalone: Boolean = true) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val x by animateDpAsState(if (checked) 18.dp else 0.dp, glide(Springs.QUICK), label = "switch")
    val track by animateColorAsState(if (checked) c.accent else c.panelDeep, glide(), label = "track")
    Box(
        modifier
            .size(width = 42.dp, height = 24.dp)
            .background(track, RoundedCornerShape(12.dp))
            .border(1.dp, if (checked) c.accent else c.keyEdge, RoundedCornerShape(12.dp))
            .then(if (standalone) Modifier.semantics { contentDescription = label }.toggleable(checked, role = Role.Switch) { on -> haptics?.tick(); onChange(on) } else Modifier)
            .padding(2.dp),
    ) {
        Box(Modifier.offset(x = x).size(20.dp).background(c.panel, RoundedCornerShape(10.dp)).border(1.dp, c.keyEdge, RoundedCornerShape(10.dp)))
    }
}

/**
 * A whole row that switches a setting: tap anywhere on it. TalkBack reads it once, as
 * "title, hint, on/off, switch".
 */
@Composable
fun SwitchRow(
    checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp), tick: Boolean = true, content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    Row(
        // The padding is inside, so the whole row (edges too) is the tap area and the switch keeps its margin.
        modifier.fillMaxWidth().toggleable(checked, role = Role.Switch) { on -> if (tick) haptics?.tick(); onChange(on) }.padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange, "", standalone = false)
    }
}

/** The knob mark: a volume knob turned all the way down, with the orange zero dot. */
@Composable
fun KnobMark(modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Canvas(modifier) {
        val u = size.minDimension / 64f
        drawCircle(c.panel, 25 * u, Offset(32 * u, 32 * u))
        drawCircle(c.keyEdge, 25 * u, Offset(32 * u, 32 * u), style = Stroke(3 * u))
        drawLine(c.text, Offset(32 * u, 32 * u), Offset(19 * u, 45 * u), 6 * u, StrokeCap.Round)
        drawCircle(c.accent, 4 * u, Offset(11 * u, 53 * u))
    }
}

/** The app logo: the knob on a small casing tile. */
@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(size * 0.28f)
    Box(modifier.size(size).background(c.bg, shape).border(1.dp, c.keyEdge, shape), contentAlignment = Alignment.Center) {
        KnobMark(Modifier.size(size * 0.62f))
    }
}

/** A card of rows with hairlines between them. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(c.panel, CardShape)
            .border(1.dp, c.line, CardShape),
        content = content,
    )
}

@Composable
fun Divider() {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
}

/** The small mono tag that says which layer a part arrives in ("L3"). */
@Composable
fun LayerTag(layer: String) {
    val c = LocalColors.current
    Text(
        layer,
        style = Type.mono.copy(fontSize = Type.hint.fontSize, color = c.muted),
        modifier = Modifier.border(1.dp, c.line, SmallShape).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** The empty state: an icon tile, a title and a few words, then keys. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalColors.current
    Column(modifier.padding(horizontal = 28.dp).widthIn(max = 390.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).background(c.bg, RoundedCornerShape(20.dp)).border(1.dp, c.line, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(26.dp), tint = c.accent) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = Type.emptyTitle.copy(color = c.text, textAlign = TextAlign.Center))
        Spacer(Modifier.height(12.dp))
        Text(text, style = Type.body.copy(color = c.muted, textAlign = TextAlign.Center, lineHeight = Type.bodyLarge.lineHeight))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun Label(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) = Text(text, modifier, style = style.copy(color = color))

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier, contentAlignment = Alignment.Center, content = content)

/** A question before something that can't be undone: a card on a dimmed screen. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = LocalColors.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(c.bg, CardShape).border(1.dp, c.line, CardShape).padding(20.dp)) {
            Text(title, style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
            Spacer(Modifier.height(8.dp))
            Text(text, style = Type.body.copy(color = c.muted))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Cancel", onDismiss, Modifier.weight(1f))
                TextKey(confirm, onConfirm, Modifier.weight(1f), kind = KeyKind.Danger)
            }
        }
    }
}
