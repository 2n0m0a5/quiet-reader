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
