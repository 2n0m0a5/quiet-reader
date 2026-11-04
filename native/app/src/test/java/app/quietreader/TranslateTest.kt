package app.quietreader

import app.quietreader.reader.Translate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Translation answers are read right, and refusals are never shown as a translation (the L4 bug). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranslateTest {
    @Test fun readsGoogle() {
        val r = Translate.parseGoogle("""{"sentences":[{"trans":"Весь наш десант ","orig":"Our whole landing party "},{"trans":"собрался.","orig":"had gathered."}],"src":"en"}""")
        assertEquals("Весь наш десант собрался.", r.text)
        assertEquals("en", r.source)
    }

    @Test fun readsGoogleDictionaryEndpoint() {
        assertEquals("привет мир", Translate.parseGoogleDict("""[["привет мир","en"]]""").text)
        assertEquals("привет", Translate.parseGoogleDict("""["привет"]""").text)
    }

    @Test fun myMemoryRefusalsAreNotTranslations() {
        val invalid = """{"responseData":{"translatedText":"'AUTO' IS AN INVALID SOURCE LANGUAGE . EXAMPLE: LANGPAIR=EN|IT USING 2 LETTER ISO OR RFC3066 LIKE ZH-CN. ALMOST ALL LANGUAGES SUPPORTED BUT SOME MAY HAVE NO CONTENT","match":0},"responseStatus":"403"}"""
        val quota = """{"responseData":{"translatedText":"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY."},"quotaFinished":null,"responseStatus":200}"""
        for (body in listOf(invalid, quota)) {
            val failed = runCatching { Translate.parseMyMemory(body) }.isFailure
            assertTrue("refusal must fail: $body", failed)
        }
        assertEquals("Весь наш десант", Translate.parseMyMemory("""{"responseData":{"translatedText":"Весь наш десант"},"responseStatus":200}""").text)
    }

    @Test fun guessesTheSourceFromTheLetters() {
        assertEquals("en", Translate.guessLanguage("Our whole landing party had gathered"))
        assertEquals("ru", Translate.guessLanguage("Весь наш десант собрался"))
        assertEquals("uz", Translate.guessLanguage("Ўзбекистон қишлоғи"))
        assertEquals("ja", Translate.guessLanguage("日本語のテキスト"))
    }

    @Test fun googleTranslateAddress() {
        assertTrue(Translate.webUrl("hello world", "ru").startsWith("https://translate.google.com/?sl=auto&tl=ru"))
    }
}
