package app.quietreader.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/**
 * Opens something that must be closed (a book, a PDF renderer) while this part of the screen is
 * shown, and closes it when it leaves (or when [key] changes) — never earlier.
 *
 * (0.5.0 L2 closed the book the moment it finished opening: its cleanup read the *current*
 * value instead of the one it opened, so EPUB pages said "webpage not available" and PDF pages
 * stayed blank. Here each opening closes exactly what it opened.)
 */
@Composable
fun <T : Any> rememberOpened(key: Any?, open: suspend () -> T?, close: (T) -> Unit, onFail: (Throwable?) -> Unit = {}): T? {
    var value by remember(key) { mutableStateOf<T?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(key) {
        var opened: T? = null
        var gone = false
        val job = scope.launch {
            val result = runCatching { open() }
            val v = result.getOrNull()
            if (gone) { v?.let(close); return@launch }
            if (v == null) onFail(result.exceptionOrNull())
            opened = v
            value = v
        }
        onDispose {
            gone = true
            job.cancel()
            opened?.let(close)
        }
    }
    return value
}
