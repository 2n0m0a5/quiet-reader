package app.quietreader.reader

import app.quietreader.data.ReadingLog
import app.quietreader.data.dayKey
import java.util.Calendar

/**
 * Counts reading time while a book is open, like the web app's tracker: only while Quiet is on
 * screen and you've touched it or turned a page in the last 5 minutes (a phone left on the table
 * doesn't count). Pages turned forward count, in "pages" of about 250 words; a jump (contents, a
 * link, the scale) isn't reading. Saved every 30 seconds and when the book closes.
 */
class Tracker(private val log: ReadingLog, private val bookId: String, private val lastTouch: () -> Long) {
    private var lastActivity = System.currentTimeMillis()
    private var lastPage: Int? = null
    private var seconds = 0.0
    private var pages = 0.0
    private var hours = DoubleArray(24)
    private var firstSave = true
    private var lastSave = System.currentTimeMillis()
    var sessionSeconds = 0.0; private set

    /** The place moved to [page] (Readium position or PDF page); [webPages] is how many 250-word pages one is. */
    fun moved(page: Int, webPages: Double) {
        lastActivity = System.currentTimeMillis()
        val last = lastPage
        if (last != null && page > last && page - last <= 3) pages += (page - last) * webPages
        lastPage = page
    }

    /** Every 5 seconds while the book is open. Returns true when it saved. */
    fun tick(visible: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val active = maxOf(lastActivity, lastTouch())
        if (visible && now - active < IDLE) {
            seconds += TICK / 1000.0
            sessionSeconds += TICK / 1000.0
            hours[Calendar.getInstance().get(Calendar.HOUR_OF_DAY)] += TICK / 1000.0
        }
        return if (now - lastSave >= SAVE_EVERY) { save(); true } else false
    }

    fun save() {
        lastSave = System.currentTimeMillis()
        if (seconds == 0.0 && pages == 0.0) return
        val newSitting = firstSave && seconds > 0
        if (newSitting) firstSave = false
        log.add(dayKey(), bookId, seconds, pages, hours, newSitting)
        seconds = 0.0; pages = 0.0; hours = DoubleArray(24)
    }

    companion object {
        const val TICK = 5_000L
        const val IDLE = 5 * 60_000L
        const val SAVE_EVERY = 30_000L
    }
}
