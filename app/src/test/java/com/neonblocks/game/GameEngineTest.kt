package com.neonblocks.game

import com.neonblocks.game.data.Cell
import com.neonblocks.game.data.GameMode
import com.neonblocks.game.data.Piece
import com.neonblocks.game.game.GameEngine
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    private val engine = GameEngine()

    @Test fun boardIsTenByTenAndCatalogIsVaried() {
        val game = engine.newGame(GameMode.CLASSIC, 0, 1234L)
        assertEquals(100, game.board.size)
        assertEquals(3, game.pieces.size)
        assertTrue(GameEngine.shapeCatalogSize() >= 20)
    }

    @Test fun rejectsOverlapAndOutOfBounds() {
        val board = MutableList(100) { -1 }
        board[0] = 2
        val piece = Piece("pair", listOf(Cell(0, 0), Cell(0, 1)), 0)
        assertFalse(GameEngine.isValidPosition(board, piece, 0, 0))
        assertFalse(GameEngine.isValidPosition(board, piece, 9, 9))
        assertTrue(GameEngine.isValidPosition(board, piece, 5, 5))
    }

    @Test fun placingCompletesAndClearsAFullRow() {
        var game = engine.newGame(GameMode.CLASSIC, 0, 91L)
        val board = MutableList(100) { -1 }
        for (c in 2..9) board[c] = 1
        val piece = Piece("pair", listOf(Cell(0, 0), Cell(0, 1)), 3)
        game = game.copy(board = board, pieces = listOf(piece, null, null))
        val next = engine.place(game, 0, 0, 0)
        assertEquals(1, next.lines)
        assertEquals(120, next.score)
        assertTrue((0..9).all { next.board[it] == -1 })
    }

    @Test fun clearsIntersectingRowAndColumnOnlyOncePerCell() {
        val board = MutableList(100) { -1 }
        for (c in 0..9) if (c != 5) board[5 * 10 + c] = 2
        for (r in 0..9) if (r != 5) board[r * 10 + 5] = 2
        val piece = Piece("dot", listOf(Cell(0, 0)), 1)
        val game = engine.newGame(GameMode.CLASSIC, 0, 2L).copy(board = board, pieces = listOf(piece, null, null))
        val next = engine.place(game, 0, 5, 5)
        assertEquals(2, next.lines)
        assertTrue((0..9).all { next.board[5 * 10 + it] == -1 })
        assertTrue((0..9).all { next.board[it * 10 + 5] == -1 })
    }

    @Test fun undoRestoresBoardScoreAndPiecesOnce() {
        val game = engine.newGame(GameMode.CLASSIC, 0, 4L)
        val first = game.pieces.indexOfFirst { it != null }
        val piece = game.pieces[first]!!
        val next = engine.place(game, first, 0, 0)
        assertEquals(game.score, engine.undo(next).score)
        assertEquals(game.best, engine.undo(next).best)
        assertEquals(game.board, engine.undo(next).board)
        assertEquals(game.pieces, engine.undo(next).pieces)
        assertFalse(engine.undo(next).canUndo)
        assertEquals(piece.cells.size * 10, next.score)
    }

    @Test fun undoAlsoRevertsARecordCreatedByThatMove() {
        val board = MutableList(100) { -1 }
        for (c in 2..9) board[c] = 1
        val pair = Piece("pair", listOf(Cell(0, 0), Cell(0, 1)), 2)
        val game = engine.newGame(GameMode.CLASSIC, 100, 13L).copy(
            board = board, pieces = listOf(pair, null, null), score = 90
        )
        val record = engine.place(game, 0, 0, 0)
        assertTrue(record.newRecord)
        assertEquals(210, record.best)
        val restored = engine.undo(record)
        assertEquals(100, restored.best)
        assertFalse(restored.newRecord)
    }

    @Test fun timerEndsAtZero() {
        val game = engine.newGame(GameMode.TIME_ATTACK, 0, 7L).copy(remainingSeconds = 1)
        val next = engine.tickSecond(game)
        assertEquals(0, next.remainingSeconds)
        assertTrue(next.gameOver)
    }

    @Test fun gameEndsWhenNewBatchHasNoLegalPlacement() {
        val board = MutableList(100) { 9 }
        for (r in 0 until 10) {
            board[r * 10 + r] = -1
            board[r * 10 + (r + 5) % 10] = -1
        }
        val dot = Piece("dot", listOf(Cell(0, 0)), 2)
        val square = Piece("square", listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(1, 1)), 3)
        val tee = Piece("tee", listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1)), 4)
        val prepared = engine.newGame(GameMode.CLASSIC, 0, 19L).copy(
            board = board,
            pieces = listOf(dot, square, tee)
        )
        val ended = engine.place(prepared, 0, 0, 0)
        assertTrue(ended.gameOver)
        assertFalse(engine.hasAnyMove(ended))
    }

    @Test fun challengeWinsAtTarget() {
        val game = engine.newGame(GameMode.CHALLENGE, 0, 8L)
        val piece = Piece("five", (0..4).map { Cell(0, it) }, 2)
        val prepared = game.copy(pieces = listOf(piece, null, null), score = 1_960)
        val next = engine.place(prepared, 0, 0, 0)
        assertTrue(next.won)
        assertTrue(next.gameOver)
    }
}
