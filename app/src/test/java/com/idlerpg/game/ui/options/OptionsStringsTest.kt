package com.idlerpg.game.ui.options

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards `strings_options.xml` against resource pitfalls that only show up on the device.
 *
 * aapt collapses whitespace in string resources: a real line break inside `<string>` becomes a
 * single space. The import error dialog lost its line structure that way ("Ungültiger Spielstand
 * Inventar, Eintrag 2: „Axt“ Der Mindestschaden …"). Line breaks must be written as `\n`.
 */
class OptionsStringsTest {

    // Gradle runs unit tests with the module directory (app/) as working directory.
    private val stringsFile = File("src/main/res/values/strings_options.xml")

    private fun strings(): Map<String, String> {
        assertTrue("not found: ${stringsFile.absolutePath}", stringsFile.isFile)
        val text = stringsFile.readText(Charsets.UTF_8)
        return STRING_REGEX.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun noRawLineBreaksInStrings() {
        val broken = strings().filterValues { '\n' in it || '\r' in it }.keys
        assertTrue("Echte Zeilenumbrüche (aapt macht daraus Leerzeichen), \\n verwenden: $broken", broken.isEmpty())
    }

    @Test
    fun multiLineErrorUsesEscapedLineBreaks() {
        val invalidAt = strings().getValue("options_error_invalid_at")
        assertTrue(invalidAt, invalidAt.split("\\n").size == 3)
    }

    @Test
    fun englishCoversAllTranslatableKeys() {
        val en = File("src/main/res/values-en/strings_options.xml")
        assertTrue(en.isFile)
        val enKeys = STRING_REGEX.findAll(en.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toSet()
        val deKeys = Regex("""<string\s+name="([^"]+)"([^>]*)>""").findAll(stringsFile.readText(Charsets.UTF_8))
            .filter { "translatable=\"false\"" !in it.groupValues[2] }
            .map { it.groupValues[1] }.toSet()
        assertTrue("missing in values-en: ${deKeys - enKeys}", enKeys.containsAll(deKeys))
        assertTrue("extra in values-en: ${enKeys - deKeys}", deKeys.containsAll(enKeys))
    }

    private companion object {
        val STRING_REGEX = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    }
}
