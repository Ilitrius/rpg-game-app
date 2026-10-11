package com.idlerpg.game.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.gameDataStore by preferencesDataStore(name = "game_state")
private val STATE_KEY = stringPreferencesKey("state_json")

/** Single source of truth for [GameState], persisted as JSON in DataStore. */
class GameRepository(private val context: Context) {

    val state: Flow<GameState> = context.gameDataStore.data.map { prefs ->
        prefs[STATE_KEY]?.let { runCatching { GameJson.decode(it) }.getOrNull() } ?: GameState()
    }

    suspend fun current(): GameState = state.first()

    suspend fun update(transform: (GameState) -> GameState) {
        context.gameDataStore.edit { prefs ->
            val old = prefs[STATE_KEY]?.let { runCatching { GameJson.decode(it) }.getOrNull() } ?: GameState()
            prefs[STATE_KEY] = GameJson.encode(transform(old))
        }
    }

    suspend fun replace(newState: GameState) = update { newState }
}

/** JSON codec shared by persistence and import/export. */
object GameJson {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    fun decode(text: String): GameState = json.decodeFromString(GameState.serializer(), text)
}
