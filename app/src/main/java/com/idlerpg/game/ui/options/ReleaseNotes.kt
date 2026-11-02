package com.idlerpg.game.ui.options

import android.content.Context
import java.io.IOException

/**
 * Release notes of the installed version. One plain-text file per version and language in
 * `assets/release-notes/<versionName>.<de|en>.txt` (stored in git, see CLAUDE.md "Release Notes").
 */
object ReleaseNotes {

    const val DIRECTORY = "release-notes"

    fun fileName(versionName: String, language: String): String = "$DIRECTORY/$versionName.$language.txt"

    /** Candidate languages: the UI language first, then German, then English (no duplicates). */
    fun languageOrder(language: String): List<String> = listOf(language, "de", "en").distinct()

    /** Notes for [versionName] in [language] (falls back to another language), or null if there are none. */
    fun load(context: Context, versionName: String, language: String): String? {
        for (lang in languageOrder(language)) {
            val text = try {
                context.assets.open(fileName(versionName, lang)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: IOException) {
                continue
            }
            if (text.isNotBlank()) return text.trim()
        }
        return null
    }
}
