package app.quietreader

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val key: String, val label: String) {
    System("system", "System"), Day("light", "Day"), Night("dark", "Night"), Black("black", "Black");
}

/** App text size, in percent: 80–150 in steps of 5, 100 by default. */
object TextScale {
    const val MIN = 80
    const val MAX = 150
    const val STEP = 5
    const val DEFAULT = 100
}

enum class MotionMode(val key: String, val label: String) {
    System("system", "Follow phone"), Full("full", "Full"), Reduced("reduced", "Reduced");
}

/** The book's typeface: its own, or one of Quiet's (the same keys as the web app's `font`). */
enum class BookFont(val key: String, val label: String) {
    Original("original", "Book’s own"), Serif("serif", "Crimson Pro"), Sans("sans", "Archivo");
}

enum class Flow(val key: String, val label: String) {
    Scrolled("scrolled", "Scroll"), Paginated("paginated", "Pages");
}

/** Book text size, in percent (the web app's `fontSize`). */
object BookSize {
    const val MIN = 70
    const val MAX = 200
    const val STEP = 5
    const val DEFAULT = 100
}

enum class PageTurn(val key: String, val label: String) { Slide("slide", "Slide"), Curl("curl", "Curl"), None("none", "None") }

enum class Ruler(val key: String, val label: String) { Off("off", "Off"), Line("line", "Line"), Focus("focus", "Focus") }

enum class Margins(val key: String, val label: String, val readium: Double) {
    Narrow("narrow", "Narrow", 0.5), Normal("normal", "Normal", 1.0), Wide("wide", "Wide", 1.6);
}

enum class Align(val key: String, val label: String) { Start("start", "Left"), Justify("justify", "Justified") }

/** The keys of the reader bar (the web app's `readerKeys`). More is always last and not listed. */
enum class ReaderKey(val key: String, val label: String) {
    Contents("contents", "Contents"), DayNight("daynight", "Day/Night"), Light("light", "Light"),
    Text("text", "Text"), Layout("layout", "Layout"), GoTo("goto", "Go to"), Mode("mode", "Scroll/Pages"),
    Notes("notes", "Notes"), Search("search", "Search");

    companion object {
        val DEFAULT = listOf(Contents, DayNight, Light, Text)
    }
}

enum class Rotation(val key: String, val label: String) {
    Auto("auto", "Automatic"), Portrait("portrait", "Portrait"), Landscape("landscape", "Landscape");
}

/**
 * The reader's settings, kept in SharedPreferences under the same keys and values as the web
 * app's `quiet:settings`, so a backup can carry them across (L2). Each one is Compose state.
 */
class QuietSettings(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("quiet", Context.MODE_PRIVATE))

    var theme by pref("theme", ThemeMode.Day, ThemeMode.entries, { it.key })
    var textScale by intPref("uiScale", TextScale.DEFAULT, TextScale.MIN..TextScale.MAX)
    var motion by pref("motion", MotionMode.System, MotionMode.entries, { it.key })
    var rotation by pref("orientation", Rotation.Auto, Rotation.entries, { it.key })
    var haptics by boolPref("haptics", true)

    // Reading
    var bookFont by pref("font", BookFont.Serif, BookFont.entries, { it.key })
    var flow by pref("flow", Flow.Scrolled, Flow.entries, { it.key })
    var bookSize by intPref("fontSize", BookSize.DEFAULT, BookSize.MIN..BookSize.MAX)
    var lineHeight by intPref("lineHeight10", 17, 10..25)          // ×10: 17 = 1.7, the web default
    var margins by pref("margins", Margins.Normal, Margins.entries, { it.key })
    var align by pref("align", Align.Start, Align.entries, { it.key })
    var hyphenate by boolPref("hyphenate", false)
    var fontWeight by intPref("fontWeight", 400, 300..700)
    var paragraphSpacing by intPref("paragraphSpacing10", -1, -1..20) // ×10 em; -1 keeps the book's own
    var indent by intPref("indent10", -1, -1..30)                     // ×10 em; -1 keeps the book's own
    var brightness by intPref("brightness", -1, -1..100)              // -1 follows the phone
    var warmth by intPref("warmth", 0, 0..100)
    var pageTurn by pref("pageTurn", PageTurn.Slide, PageTurn.entries, { it.key })
    var bionic by boolPref("bionic", false)
    var ruler by pref("ruler", Ruler.Off, Ruler.entries, { it.key })
    var rulerY by intPref("rulerY100", 40, 5..95)                     // % of the screen height
    /** A font you added (its family), used instead of [bookFont]; null for Quiet's own fonts. */
    var customFont: String?
        get() = customFontState.value
        set(value) { customFontState.value = value; prefs.edit().putString("customFont", value).apply() }
    private val customFontState = mutableStateOf(prefs.getString("customFont", null))
    var translateTo: String
        get() = translateState.value
        set(value) { translateState.value = value; prefs.edit().putString("translateTo", value).apply() }
    private val translateState = mutableStateOf(prefs.getString("translateTo", null) ?: app.quietreader.reader.Translate.defaultTarget())
    /** The colour the last highlight got (the next one starts with it). */
    var lastColor: String
        get() = prefs.getString("lastColor", null) ?: "#f2c94c"
        set(value) { prefs.edit().putString("lastColor", value).apply() }
    var volumeKeys by boolPref("volumeKeys", true)
    var showClock by boolPref("showClock", true)
    var keepAwake by boolPref("keepAwake", true)
    var readerKeys: List<ReaderKey>
        get() = readerKeysState.value
        set(value) {
            val keys = value.distinct().take(4)
            readerKeysState.value = keys
            prefs.edit().putString("readerKeys", keys.joinToString(",") { it.key }).apply()
        }
    private val readerKeysState = mutableStateOf(
        prefs.getString("readerKeys", null)?.split(',')?.mapNotNull { k -> ReaderKey.entries.firstOrNull { it.key == k } }
            ?.takeIf { it.isNotEmpty() } ?: ReaderKey.DEFAULT
    )

    // This phone only
    var lastBackup: Long?
        get() = lastBackupState.value.takeIf { it > 0 }
        set(value) { lastBackupState.value = value ?: 0; prefs.edit().putLong("lastBackup", value ?: 0).apply() }
    private val lastBackupState = mutableStateOf(prefs.getLong("lastBackup", 0))

    /** The web app's settings from a restored backup, kept whole for the layers that use them. */
    var webSettings: String
        get() = prefs.getString("webSettings", "{}") ?: "{}"
        set(value) { prefs.edit().putString("webSettings", value).apply() }

    private fun <T> pref(name: String, default: T, all: List<T>, key: (T) -> String) =
        object : kotlin.properties.ReadWriteProperty<Any?, T> {
            private val state = mutableStateOf(prefs.getString(name, null)?.let { s -> all.firstOrNull { key(it) == s } } ?: default)
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = state.value
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
                state.value = value
                prefs.edit().putString(name, key(value)).apply()
            }
        }

    // Stored as a number; L0 kept it as text ("100"), which is read once and converted.
    private fun intPref(name: String, default: Int, valid: IntRange) =
        object : kotlin.properties.ReadWriteProperty<Any?, Int> {
            private val state = mutableStateOf(
                (runCatching { prefs.getInt(name, default) }.getOrNull() ?: prefs.getString(name, null)?.toIntOrNull() ?: default)
                    .coerceIn(valid)
            )
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = state.value
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Int) {
                state.value = value.coerceIn(valid)
                prefs.edit().remove(name).putInt(name, state.value).apply()
            }
        }

    private fun boolPref(name: String, default: Boolean) =
        object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
            private val state = mutableStateOf(prefs.getBoolean(name, default))
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = state.value
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) {
                state.value = value
                prefs.edit().putBoolean(name, value).apply()
            }
        }
}

/** True when the phone asks apps for less motion ("Remove animations", or Battery saver on some phones). */
fun phoneWantsLessMotion(context: Context): Boolean =
    AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
