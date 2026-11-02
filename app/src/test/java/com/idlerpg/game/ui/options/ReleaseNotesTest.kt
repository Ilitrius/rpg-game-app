package com.idlerpg.game.ui.options

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    /** `val appVersionName = "…"` from app/build.gradle.kts (unit tests run in the app module directory). */
    private fun currentVersionName(): String {
        val gradle = File("build.gradle.kts").readText()
        return Regex("""val appVersionName = "([^"]+)"""").find(gradle)!!.groupValues[1]
    }

    @Test
    fun fileNameFollowsConvention() {
        assertEquals("release-notes/0.1.0-rc18.de.txt", ReleaseNotes.fileName("0.1.0-rc18", "de"))
    }

    @Test
    fun languageOrderFallsBackWithoutDuplicates() {
        assertEquals(listOf("fr", "de", "en"), ReleaseNotes.languageOrder("fr"))
        assertEquals(listOf("en", "de"), ReleaseNotes.languageOrder("en"))
        assertEquals(listOf("de", "en"), ReleaseNotes.languageOrder("de"))
    }

    /** Every release needs release notes in German and English (see CLAUDE.md, "Release Notes"). */
    @Test
    fun currentVersionHasReleaseNotesInBothLanguages() {
        val version = currentVersionName()
        for (lang in listOf("de", "en")) {
            val file = File("src/main/assets/${ReleaseNotes.fileName(version, lang)}")
            assertTrue("Release notes missing for $version ($lang): ${file.path}", file.isFile)
            assertTrue("Release notes are empty for $version ($lang)", file.readText().isNotBlank())
        }
    }
}
