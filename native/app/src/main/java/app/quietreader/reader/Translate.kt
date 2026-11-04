package app.quietreader.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Translation, like the web app's (src/reader/translate.ts): Google's free endpoint, and MyMemory
 * when Google refuses. Only the text you chose to translate is sent; nothing else leaves the phone.
 */
object Translate {
    val LANGUAGES = listOf("en", "ru", "uz", "es", "de", "fr", "pt", "tr", "it", "uk", "kk", "pl", "ar", "hi", "zh-CN", "ja", "ko")
    fun name(code: String): String = Locale.forLanguageTag(code).getDisplayName(Locale.ENGLISH).ifBlank { code }
    fun defaultTarget(): String = Locale.getDefault().language.let { lang -> LANGUAGES.firstOrNull { it.startsWith(lang) } ?: "en" }

    data class Result(val service: String, val text: String, val source: String, val dictionary: List<Pair<String, List<String>>>)

    suspend fun translate(text: String, target: String): Result = withContext(Dispatchers.IO) {
        val q = text.take(1500)
        try { google(q, target) } catch (e: Exception) { myMemory(q, target) }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("answered ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun google(q: String, target: String): Result {
        val json = JSONObject(get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${enc(target)}&dj=1&dt=t&dt=bd&q=${enc(q)}"))
        val sentences = json.optJSONArray("sentences")
        val text = (0 until (sentences?.length() ?: 0)).joinToString("") { sentences!!.getJSONObject(it).optString("trans") }.trim()
        val dict = json.optJSONArray("dict")?.let { arr ->
            (0 until arr.length()).map { i ->
                val d = arr.getJSONObject(i)
                val terms = d.optJSONArray("terms")
                d.optString("pos") to (0 until (terms?.length() ?: 0)).map { terms!!.optString(it) }.take(6)
            }
        } ?: emptyList()
        if (text.isBlank()) throw IllegalStateException("empty")
        return Result("Google", text, json.optString("src"), dict)
    }

    private fun myMemory(q: String, target: String): Result {
        val json = JSONObject(get("https://api.mymemory.translated.net/get?q=${enc(q)}&langpair=${enc("auto|$target")}"))
        val text = json.optJSONObject("responseData")?.optString("translatedText").orEmpty()
        if (text.isBlank()) throw IllegalStateException("No translation came back. Check the internet connection.")
        return Result("MyMemory", text, "", emptyList())
    }
}
