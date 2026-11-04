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

enum class TextSize(val key: Int, val label: String, val scale: Float) {
    Small(90, "Small", 0.9f), Default(100, "Default", 1f), Large(115, "Large", 1.15f), Larger(130, "Larger", 1.3f);
}

enum class MotionMode(val key: String, val label: String) {
    System("system", "Follow phone"), Full("full", "Full"), Reduced("reduced", "Reduced");
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
    var textSize by pref("uiScale", TextSize.Default, TextSize.entries, { it.key.toString() })
    var motion by pref("motion", MotionMode.System, MotionMode.entries, { it.key })
    var rotation by pref("orientation", Rotation.Auto, Rotation.entries, { it.key })
    var haptics by boolPref("haptics", true)

    private fun <T> pref(name: String, default: T, all: List<T>, key: (T) -> String) =
        object : kotlin.properties.ReadWriteProperty<Any?, T> {
            private val state = mutableStateOf(prefs.getString(name, null)?.let { s -> all.firstOrNull { key(it) == s } } ?: default)
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = state.value
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
                state.value = value
                prefs.edit().putString(name, key(value)).apply()
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
