package com.neonblocks.game.game

import com.neonblocks.game.data.Cell
import com.neonblocks.game.data.GameMode
import com.neonblocks.game.data.GameState
import com.neonblocks.game.data.MoveSnapshot
import com.neonblocks.game.data.Piece
import java.time.LocalDate
import kotlin.random.Random

/** Pure game rules: no Android or Compose dependencies. */
class GameEngine {
    companion object {
        const val BOARD_SIZE = 8
        const val CHALLENGE_TARGET = 2_000
        const val DAILY_TARGET = 1_500
        const val TIME_LIMIT_SECONDS = 90

        private val SHAPES: List<Pair<String, List<Cell>>> = listOf(
            "Punto" to listOf(Cell(0, 0)),
            "Doble" to listOf(Cell(0, 0), Cell(0, 1)),
            "Trío" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2)),
            "Línea 4" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3)),
            "Línea 5" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3), Cell(0, 4)),
            "Cuadro 2" to listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(1, 1)),
            "Cuadro 3" to (0..2).flatMap { r -> (0..2).map { c -> Cell(r, c) } },
            "L corta" to listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1)),
            "L espejo" to listOf(Cell(0, 1), Cell(1, 1), Cell(2, 0), Cell(2, 1)),
            "J corta" to listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(2, 0)),
            "T" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1)),
            "T larga" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1), Cell(2, 1)),
            "Z" to listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(1, 2)),
            "S" to listOf(Cell(0, 1), Cell(0, 2), Cell(1, 0), Cell(1, 1)),
            "Escalón" to listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1)),
            "Cruz" to listOf(Cell(0, 1), Cell(1, 0), Cell(1, 1), Cell(1, 2), Cell(2, 1)),
            "U" to listOf(Cell(0, 0), Cell(0, 2), Cell(1, 0), Cell(1, 1), Cell(1, 2)),
            "P" to listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(1, 1), Cell(2, 0)),
            "V" to listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1), Cell(2, 2)),
            "Rectángulo" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 0), Cell(1, 1), Cell(1, 2)),
            "Zigzag" to listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1), Cell(2, 2)),
            "Columna 3" to listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0)),
            "Columna 4" to listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0)),
            "Esquina" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 0)),
            "Gancho" to listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1), Cell(2, 2)),
            "Doble T" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 0), Cell(1, 2)),
            "Mini Z" to listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1)),
            "Mini L" to listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0)),
            "Mini T" to listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1))
        )

        fun shapeCatalogSize(): Int = SHAPES.size
        fun isValidPosition(board: List<Int>, piece: Piece, row: Int, col: Int): Boolean =
            canPlace(board, piece, row, col)

        private fun canPlace(board: List<Int>, piece: Piece, row: Int, col: Int): Boolean {
            if (board.size != BOARD_SIZE * BOARD_SIZE || piece.cells.isEmpty()) return false
            return piece.cells.all { cell ->
                val r = row + cell.row
                val c = col + cell.col
                r in 0 until BOARD_SIZE && c in 0 until BOARD_SIZE && board[r * BOARD_SIZE + c] == -1
            }
        }
    }

    fun newGame(mode: GameMode, best: Int, seed: Long = defaultSeed(mode)): GameState {
        val blank = List(BOARD_SIZE * BOARD_SIZE) { -1 }
        val base = GameState(
            id = System.nanoTime(), mode = mode, board = blank, pieces = emptyList(),
            best = best, seed = seed,
            remainingSeconds = TIME_LIMIT_SECONDS
        )
        return base.copy(pieces = generateBatch(base.board, seed, 0))
    }

    fun defaultSeed(mode: GameMode): Long = if (mode == GameMode.DAILY) {
        LocalDate.now().toEpochDay() * 31L + 2026L
    } else System.nanoTime()

    fun canPlace(state: GameState, pieceIndex: Int, row: Int, col: Int): Boolean {
        val piece = state.pieces.getOrNull(pieceIndex) ?: return false
        return canPlace(state.board, piece, row, col)
    }

    fun hasAnyMove(state: GameState): Boolean = state.pieces.filterNotNull().any { piece ->
        (0 until BOARD_SIZE).any { row -> (0 until BOARD_SIZE).any { col -> canPlace(state.board, piece, row, col) } }
    }

    fun place(state: GameState, pieceIndex: Int, row: Int, col: Int): GameState {
        if (state.gameOver || state.paused || !canPlace(state, pieceIndex, row, col)) return state
        val piece = state.pieces[pieceIndex] ?: return state
        val before = MoveSnapshot(
            board = state.board, pieces = state.pieces, score = state.score, best = state.best, lines = state.lines,
            combo = state.combo, bestCombo = state.bestCombo, moves = state.moves,
            remainingSeconds = state.remainingSeconds, batchIndex = state.batchIndex,
            newRecord = state.newRecord, gameOver = state.gameOver, won = state.won
        )
        val board = state.board.toMutableList()
        piece.cells.forEach { cell -> board[(row + cell.row) * BOARD_SIZE + col + cell.col] = piece.color }

        val fullRows = (0 until BOARD_SIZE).filter { r -> (0 until BOARD_SIZE).all { c -> board[r * BOARD_SIZE + c] != -1 } }
        val fullCols = (0 until BOARD_SIZE).filter { c -> (0 until BOARD_SIZE).all { r -> board[r * BOARD_SIZE + c] != -1 } }
        val clearedCount = fullRows.size + fullCols.size
        val cellsToClear = BooleanArray(BOARD_SIZE * BOARD_SIZE)
        fullRows.forEach { r -> (0 until BOARD_SIZE).forEach { c -> cellsToClear[r * BOARD_SIZE + c] = true } }
        fullCols.forEach { c -> (0 until BOARD_SIZE).forEach { r -> cellsToClear[r * BOARD_SIZE + c] = true } }
        cellsToClear.forEachIndexed { index, clear -> if (clear) board[index] = -1 }

        val nextCombo = if (clearedCount > 0) state.combo + 1 else 0
        val comboBonus = if (clearedCount > 0 && nextCombo > 1) nextCombo * 35 * clearedCount else 0
        val gained = piece.cells.size * 10 + clearedCount * 100 + comboBonus
        val nextScore = state.score + gained
        val nextBest = maxOf(state.best, nextScore)
        val mutablePieces = state.pieces.toMutableList()
        mutablePieces[pieceIndex] = null
        var nextBatch = state.batchIndex
        var nextPieces: List<Piece?> = mutablePieces
        if (mutablePieces.all { it == null }) {
            nextBatch += 1
            nextPieces = generateBatch(board, state.seed, nextBatch)
        }

        val target = when (state.mode) {
            GameMode.CHALLENGE -> CHALLENGE_TARGET
            GameMode.DAILY -> DAILY_TARGET
            else -> Int.MAX_VALUE
        }
        val won = nextScore >= target
        val candidate = state.copy(
            board = board,
            pieces = nextPieces,
            score = nextScore,
            best = nextBest,
            lines = state.lines + clearedCount,
            combo = nextCombo,
            bestCombo = maxOf(state.bestCombo, nextCombo),
            moves = state.moves + 1,
            batchIndex = nextBatch,
            gameOver = won,
            won = won,
            newRecord = state.newRecord || nextScore > state.best,
            snapshot = before
        )
        return if (won) candidate else candidate.copy(gameOver = !hasAnyMove(candidate))
    }

    fun undo(state: GameState): GameState {
        val snapshot = state.snapshot ?: return state
        if (state.undoUsed) return state
        return state.copy(
            board = snapshot.board,
            pieces = snapshot.pieces,
            score = snapshot.score,
            best = snapshot.best,
            lines = snapshot.lines,
            combo = snapshot.combo,
            bestCombo = snapshot.bestCombo,
            moves = snapshot.moves,
            remainingSeconds = snapshot.remainingSeconds,
            batchIndex = snapshot.batchIndex,
            newRecord = snapshot.newRecord,
            gameOver = snapshot.gameOver,
            won = snapshot.won,
            undoUsed = true,
            snapshot = null
        )
    }

    fun tickSecond(state: GameState): GameState {
        if (state.mode != GameMode.TIME_ATTACK || state.paused || state.gameOver) return state
        val next = (state.remainingSeconds - 1).coerceAtLeast(0)
        return state.copy(remainingSeconds = next, gameOver = next == 0)
    }

    private fun generateBatch(board: List<Int>, seed: Long, batch: Int): List<Piece?> {
        var result: List<Piece> = emptyList()
        repeat(24) { attempt ->
            val random = Random(seed xor (batch.toLong() * 0x9E3779B97F4A7C15UL.toLong()) xor attempt.toLong())
            // Generador más amable: predominan piezas de 1–4 bloques.
            // Las piezas grandes siguen existiendo, pero aparecen ocasionalmente.
            val easyShapes = SHAPES.filter { it.second.size <= 4 }
            val mediumShapes = SHAPES.filter { it.second.size in 5..6 }
            val largeShapes = SHAPES.filter { it.second.size >= 7 }
            result = List(3) {
                val roll = random.nextInt(100)
                val pool = when {
                    roll < 78 -> easyShapes
                    roll < 97 -> mediumShapes
                    else -> largeShapes
                }.ifEmpty { easyShapes }
                val (name, cells) = pool[random.nextInt(pool.size)]
                Piece(name, cells, random.nextInt(12))
            }
            if (result.any { piece ->
                    (0 until BOARD_SIZE).any { r -> (0 until BOARD_SIZE).any { c -> canPlace(board, piece, r, c) } }
                }) return result.map { it }
        }
        return result.map { it }
    }
}
