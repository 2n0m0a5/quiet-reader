package app.quietreader.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Translation: Google's free endpoint (two addresses), then MyMemory. Only the text you chose to
 * translate is sent; nothing else leaves the phone. If all of them refuse, the sheet offers to
 * open Google Translate itself with the text.
 *
 * (0.5.0 L4: Google refused the phone's plain Java requests, and MyMemory was asked to detect the
 * language, which it can't, so its error message was shown as the translation.)
 */
object Translate {
    val LANGUAGES = listOf("en", "ru", "uz", "es", "de", "fr", "pt", "tr", "it", "uk", "kk", "pl", "ar", "hi", "zh-CN", "ja", "ko")
    fun name(code: String): String =
        if (code == "zh-CN") "Chinese" else Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }
    fun defaultTarget(): String = Locale.getDefault().language.let { lang -> LANGUAGES.firstOrNull { it.startsWith(lang) } ?: "en" }

    data class Result(val service: String, val text: String, val source: String, val dictionary: List<Pair<String, List<String>>>)

    class Failed(message: String) : Exception(message)

    suspend fun translate(text: String, target: String): Result = withContext(Dispatchers.IO) {
        val q = text.trim().take(1500)
        val problems = mutableListOf<String>()
        for (attempt in listOf<() -> Result>(
            { googleSingle("https://translate.googleapis.com", q, target) },
            { googleSingle("https://translate.google.com", q, target) },
            { googleDict(q, target) },
            { myMemory(q, target) },
        )) {
            try { return@withContext attempt() } catch (e: Exception) { problems += e.message ?: "failed" }
        }
        throw Failed(
            if (problems.any { it.contains("Unable to resolve host") || it.contains("connect") }) "No internet connection."
            else "The free translators refused just now (they limit how often they answer). Try again in a while, or open Google Translate."
        )
    }

    /** The address for opening Google Translate itself with the text. */
    fun webUrl(text: String, target: String) =
        "https://translate.google.com/?sl=auto&tl=${enc(target)}&op=translate&text=${enc(text.take(1500))}"

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        // Like a browser: Google answers plain Java requests with "unusual traffic".
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
        c.setRequestProperty("Accept", "application/json, text/plain, */*")
        c.setRequestProperty("Accept-Language", "en")
        try {
            if (c.responseCode != 200) throw Failed("answered ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun googleSingle(host: String, q: String, target: String): Result =
        parseGoogle(get("$host/translate_a/single?client=gtx&sl=auto&tl=${enc(target)}&dj=1&dt=t&dt=bd&q=${enc(q)}"))

    private fun googleDict(q: String, target: String): Result =
        parseGoogleDict(get("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=${enc(target)}&q=${enc(q)}"))

    private fun myMemory(q: String, target: String): Result {
        val source = guessLanguage(q)
        if (source == target.substringBefore('-')) throw Failed("same language")
        return parseMyMemory(get("https://api.mymemory.translated.net/get?q=${enc(q.take(450))}&langpair=${enc("$source|$target")}"))
    }

    // ---------- Parsing (tested in TranslateTest) ----------

    fun parseGoogle(body: String): Result {
        val json = JSONObject(body)
        val sentences = json.optJSONArray("sentences") ?: throw Failed("no sentences")
        val text = (0 until sentences.length()).joinToString("") { sentences.getJSONObject(it).optString("trans") }.trim()
        if (text.isBlank()) throw Failed("empty")
        val dict = json.optJSONArray("dict")?.let { arr ->
            (0 until arr.length()).map { i ->
                val d = arr.getJSONObject(i)
                val terms = d.optJSONArray("terms")
                d.optString("pos") to (0 until (terms?.length() ?: 0)).map { terms!!.optString(it) }.take(6)
            }
        } ?: emptyList()
        return Result("Google", text, json.optString("src"), dict)
    }

    /** ["перевод"] or [["перевод","en"]] */
    fun parseGoogleDict(body: String): Result {
        val arr = JSONArray(body)
        val first = arr.opt(0)
        val (text, src) = when (first) {
            is JSONArray -> first.optString(0) to first.optString(1)
            is String -> first to ""
            else -> throw Failed("unexpected")
        }
        if (text.isBlank()) throw Failed("empty")
        return Result("Google", text.trim(), src, emptyList())
    }

    fun parseMyMemory(body: String): Result {
        val json = JSONObject(body)
        val text = json.optJSONObject("responseData")?.optString("translatedText").orEmpty()
        val status = json.opt("responseStatus")?.toString()?.toIntOrNull() ?: 200
        // MyMemory reports its limits and errors as the "translation"; never show those as one.
        if (status != 200 || text.isBlank() || text.startsWith("MYMEMORY WARNING") || text.contains("INVALID SOURCE LANGUAGE") ||
            text.contains("IS AN INVALID") || json.optBoolean("quotaFinished", false)) throw Failed("MyMemory refused")
        return Result("MyMemory", text, "", emptyList())
    }

    /** The source language, from the letters (MyMemory can't detect it). */
    fun guessLanguage(text: String): String {
        val counts = mutableMapOf<String, Int>()
        for (ch in text) {
            val lang = when (Character.UnicodeScript.of(ch.code)) {
                Character.UnicodeScript.CYRILLIC -> if (ch in "ўқғҳЎҚҒҲ") "uz" else if (ch in "іїєґІЇЄҐ") "uk" else "ru"
                Character.UnicodeScript.ARABIC -> "ar"
                Character.UnicodeScript.HAN -> "zh-CN"
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "ja"
                Character.UnicodeScript.HANGUL -> "ko"
                Character.UnicodeScript.DEVANAGARI -> "hi"
                Character.UnicodeScript.LATIN -> "en"
                else -> null
            } ?: continue
            counts[lang] = (counts[lang] ?: 0) + 1
        }
        // Kana with Han is Japanese; one Uzbek or Ukrainian letter in Cyrillic text decides it.
        if ((counts["ja"] ?: 0) > 0) return "ja"
        if ((counts["uz"] ?: 0) > 0) return "uz"
        if ((counts["uk"] ?: 0) > 0) return "uk"
        return counts.maxByOrNull { it.value }?.key ?: "en"
    }
}
