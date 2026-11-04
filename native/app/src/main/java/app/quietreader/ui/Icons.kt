package app.quietreader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Lucide icons (ISC License), the same set as the web app, rebuilt from their SVG shapes:
 * a 24×24 box, 2-unit round strokes. Rects and circles are turned into paths.
 */
private fun lucide(name: String, vararg shapes: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in shapes) addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,$r" + "v${h - 2 * r}a$r,$r 0 0 1 -$r,$r" +
        "h-${w - 2 * r}a$r,$r 0 0 1 -$r,-$r" + "v-${h - 2 * r}a$r,$r 0 0 1 $r,-${r}z"

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r},${cy}a$r,$r 0 1 0 ${2 * r},0a$r,$r 0 1 0 -${2 * r},0z"

object Icons {
    val Library = lucide("library", "m16 6 4 14", "M12 6v14", "M8 8v12", "M4 4v16")
    val NotebookText = lucide(
        "notebook-text", "M2 6h4", "M2 10h4", "M2 14h4", "M2 18h4", rect(4f, 2f, 16f, 20f, 2f),
        "M9.5 8h5", "M9.5 12H16", "M9.5 16H14",
    )
    val NotebookPen = lucide(
        "notebook-pen", "M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4",
        "M2 6h4", "M2 10h4", "M2 14h4", "M2 18h4",
        "M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z",
    )
    val ChartColumn = lucide("chart-column", "M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3")
    val Settings = lucide(
        "settings",
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        circle(12f, 12f, 3f),
    )
    val Plus = lucide("plus", "M5 12h14", "M12 5v14")
    val Moon = lucide("moon", "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401")
    val Sun = lucide(
        "sun", circle(12f, 12f, 4f), "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41",
        "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41",
    )
    val ChevronRight = lucide("chevron-right", "m9 18 6-6-6-6")
    val ChevronLeft = lucide("chevron-left", "m15 18-6-6 6-6")
    val ListIcon = lucide("list", "M3 5h.01", "M3 12h.01", "M3 19h.01", "M8 5h13", "M8 12h13", "M8 19h13")
    val TypeIcon = lucide("type", "M12 4v16", "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2", "M9 20h6")
    val Trash = lucide("trash-2", "M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
    val Restore = lucide("rotate-ccw", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")
    val Download = lucide("download", "M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5")
    val Upload = lucide("upload", "M12 3v12", "m17 8-5-5-5 5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
    val BookOpen = lucide("book-open", "M12 5v16", "M20.001 19A2 2 0 0 0 22 17V5a2 2 0 0 0-1.999-2L16 3.002A5 5 0 0 0 12 5a5 5 0 0 0-4-2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 1.999 2H8a5 5 0 0 1 4 2 5 5 0 0 1 4-2z")
    val Check = lucide("check", "M20 6 9 17l-5-5")
    val Close = lucide("x", "M18 6 6 18", "m6 6 12 12")
    val Layout = lucide("layout-template", rect(3f, 3f, 18f, 7f, 1f), rect(3f, 14f, 9f, 7f, 1f), rect(16f, 14f, 5f, 7f, 1f))
    val Hash = lucide("hash", "M4 9h16", "M4 15h16", "M10 3 8 21", "M16 3 14 21")
    val More = lucide("ellipsis", circle(12f, 12f, 1f), circle(19f, 12f, 1f), circle(5f, 12f, 1f))
    val Bookmark = lucide("bookmark", "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z")
    val Search = lucide("search", "m21 21-4.34-4.34", circle(11f, 11f, 8f))
    val Info = lucide("info", circle(12f, 12f, 10f), "M12 16v-4", "M12 8h.01")
}
