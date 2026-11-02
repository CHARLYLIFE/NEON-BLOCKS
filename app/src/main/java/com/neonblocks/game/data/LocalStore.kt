package com.neonblocks.game.data

import android.content.Context

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("neon_blocks_local", Context.MODE_PRIVATE)

    fun loadGame(): GameState? = runCatching {
        prefs.getString("saved_game", null)?.let(StateCodec::decode)
    }.getOrNull()

    fun saveGame(state: GameState) {
        prefs.edit().putString("saved_game", StateCodec.encode(state)).apply()
    }

    fun clearSavedGame() { prefs.edit().remove("saved_game").apply() }

    fun best(mode: GameMode): Int = prefs.getInt("best_${mode.name}", 0)
    fun saveBest(mode: GameMode, value: Int) {
        if (value > best(mode)) prefs.edit().putInt("best_${mode.name}", value).apply()
    }

    fun restoreBest(mode: GameMode, value: Int) {
        prefs.edit().putInt("best_${mode.name}", value.coerceAtLeast(0)).apply()
    }

    fun soundEnabled(): Boolean = prefs.getBoolean("sound", true)
    fun setSoundEnabled(value: Boolean) { prefs.edit().putBoolean("sound", value).apply() }
    fun hapticsEnabled(): Boolean = prefs.getBoolean("haptics", true)
    fun setHapticsEnabled(value: Boolean) { prefs.edit().putBoolean("haptics", value).apply() }
    fun musicEnabled(): Boolean = prefs.getBoolean("music", false)
    fun setMusicEnabled(value: Boolean) { prefs.edit().putBoolean("music", value).apply() }
    fun reducedEffects(): Boolean = prefs.getBoolean("reduced_effects", false)
    fun setReducedEffects(value: Boolean) { prefs.edit().putBoolean("reduced_effects", value).apply() }

    fun stats(): PlayerStats = PlayerStats(
        gamesPlayed = prefs.getInt("stats_games", 0),
        totalScore = prefs.getLong("stats_total_score", 0L),
        totalLines = prefs.getInt("stats_total_lines", 0),
        bestCombo = prefs.getInt("stats_best_combo", 0)
    )

    fun recordGame(state: GameState) {
        val lastId = prefs.getLong("last_recorded_game", -1L)
        if (lastId == state.id) return
        prefs.edit()
            .putLong("last_recorded_game", state.id)
            .putInt("stats_games", stats().gamesPlayed + 1)
            .putLong("stats_total_score", stats().totalScore + state.score)
            .putInt("stats_total_lines", stats().totalLines + state.lines)
            .putInt("stats_best_combo", maxOf(stats().bestCombo, state.bestCombo))
            .apply()
    }
}
