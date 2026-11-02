package com.neonblocks.game.data

data class Cell(val row: Int, val col: Int)

data class Piece(
    val name: String,
    val cells: List<Cell>,
    val color: Int
) {
    val width: Int get() = (cells.maxOfOrNull { it.col } ?: 0) + 1
    val height: Int get() = (cells.maxOfOrNull { it.row } ?: 0) + 1
}

enum class GameMode(val title: String, val subtitle: String) {
    CLASSIC("CLÁSICO", "Resiste todo lo que puedas"),
    CHALLENGE("DESAFÍO", "Alcanza 2.000 puntos"),
    TIME_ATTACK("CONTRARRELOJ", "90 segundos para sumar"),
    DAILY("DIARIO", "El reto cambia cada día")
}

data class MoveSnapshot(
    val board: List<Int>,
    val pieces: List<Piece?>,
    val score: Int,
    val best: Int,
    val lines: Int,
    val combo: Int,
    val bestCombo: Int,
    val moves: Int,
    val remainingSeconds: Int,
    val batchIndex: Int,
    val newRecord: Boolean,
    val gameOver: Boolean,
    val won: Boolean
)

data class GameState(
    val id: Long,
    val mode: GameMode,
    val board: List<Int>,
    val pieces: List<Piece?>,
    val score: Int = 0,
    val best: Int = 0,
    val lines: Int = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val moves: Int = 0,
    val remainingSeconds: Int = 90,
    val seed: Long,
    val batchIndex: Int = 0,
    val gameOver: Boolean = false,
    val won: Boolean = false,
    val paused: Boolean = false,
    val newRecord: Boolean = false,
    val undoUsed: Boolean = false,
    val snapshot: MoveSnapshot? = null,
    val statsCounted: Boolean = false
) {
    val canUndo: Boolean get() = snapshot != null && !undoUsed
}

data class PlayerStats(
    val gamesPlayed: Int = 0,
    val totalScore: Long = 0,
    val totalLines: Int = 0,
    val bestCombo: Int = 0
)
