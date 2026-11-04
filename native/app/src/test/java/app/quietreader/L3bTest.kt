package app.quietreader

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.quietreader.reader.Fold
import app.quietreader.reader.PageScripts
import app.quietreader.reader.drawCurl
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class L3bTest {
    @get:Rule val compose = createComposeRule()

    /** Pulling the corner halfway along the bottom folds the page down the line at 3/4 of the width. */
    @Test fun foldGeometry() {
        val fold = Fold(Size(400f, 800f), Offset(200f, 800f))
        assertTrue(fold.active)
        val turning = fold.piece(cornerSide = true)
        assertEquals(300f, turning.minOf { it.x }, 0.5f)
        val flap = fold.flap()
        assertEquals(200f, flap.minOf { it.x }, 0.5f)
        assertEquals(300f, flap.maxOf { it.x }, 0.5f)
        // No pull, no fold.
        assertTrue(!Fold(Size(400f, 800f), Offset(400f, 800f)).active)
    }

    /** A frame of the curl, to look at (build/outputs/roborazzi/curl-frame.png). */
    @Test fun curlFrame() {
        val bmp = android.graphics.Bitmap.createBitmap(400, 800, android.graphics.Bitmap.Config.ARGB_8888).apply {
            val canvas = android.graphics.Canvas(this)
            canvas.drawColor(android.graphics.Color.rgb(242, 240, 235))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(36, 35, 31) }
            for (y in 60 until 760 step 28) canvas.drawRect(30f, y.toFloat(), 370f, y + 10f, paint)
        }.asImageBitmap()
        compose.setContent {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFFDDE6EE)) // the next page, under
                drawCurl(bmp, Offset(size.width * 0.35f, size.height * 0.82f), forward = true, paper = Color(0xFFE9E6DF))
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/curl-frame.png")
    }

    /** The page script, written out for the browser check (scripts/check-page-script.cjs). */
    @Test fun writePageScript() {
        File("build/outputs").mkdirs()
        File("build/outputs/page-script-on.js").writeText(PageScripts.script(bionic = true, fontFamily = "My Font"))
        File("build/outputs/page-script-off.js").writeText(PageScripts.script(bionic = false, fontFamily = "My Font"))
    }

    @Test fun addingAFontReadsItsFamilyAndWeight() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quiet = Quiet(context, dbName = null)
        val file = File(context.cacheDir, "Literata-SemiBoldItalic.ttf").apply { writeBytes(ByteArray(100) { 1 }) }
        assertEquals("Literata", quiet.fonts.add(Uri.fromFile(file)))
        val font = quiet.fonts.all().single()
        assertEquals(600, font.weight)
        assertTrue(font.italic)
        val notAFont = File(context.cacheDir, "notes.txt").apply { writeText("x") }
        assertEquals(null, quiet.fonts.add(Uri.fromFile(notAFont)))
        quiet.fonts.remove("Literata")
        assertTrue(quiet.fonts.all().isEmpty())
    }
}
