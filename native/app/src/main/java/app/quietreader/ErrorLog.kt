package app.quietreader

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recent errors and the last crash, kept on the phone only, for Report a problem (which you read
 * before you send it yourself). Nothing is sent anywhere by itself.
 */
object ErrorLog {
    private val recent = ArrayDeque<String>()
    private var crashFile: File? = null

    fun install(context: Context) {
        crashFile = File(context.filesDir, "last-crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                crashFile?.writeText("${stamp()} ${e.javaClass.simpleName}: ${e.message}\n" + e.stackTrace.take(12).joinToString("\n") { "  at $it" })
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun keep(what: String, e: Throwable? = null) = synchronized(recent) {
        recent.addLast("${stamp()} $what${e?.let { ": ${it.javaClass.simpleName} ${it.message}" } ?: ""}".take(300))
        while (recent.size > 15) recent.removeFirst()
    }

    fun recent(): List<String> = synchronized(recent) { recent.toList() }
    fun lastCrash(): String? = crashFile?.takeIf { it.exists() }?.readText()
    fun clearCrash() { crashFile?.delete() }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
