package com.neonblocks.game.data

import org.json.JSONArray
import org.json.JSONObject

object StateCodec {
    fun encode(state: GameState): String {
        val json = JSONObject()
        json.put("id", state.id).put("mode", state.mode.name)
            .put("board", JSONArray(state.board))
            .put("pieces", JSONArray().apply { state.pieces.forEach { put(it?.let(::pieceJson) ?: JSONObject.NULL) } })
            .put("score", state.score).put("best", state.best).put("lines", state.lines)
            .put("combo", state.combo).put("bestCombo", state.bestCombo).put("moves", state.moves)
            .put("remainingSeconds", state.remainingSeconds).put("seed", state.seed)
            .put("batchIndex", state.batchIndex).put("gameOver", state.gameOver)
            .put("won", state.won).put("paused", state.paused).put("newRecord", state.newRecord)
            .put("undoUsed", state.undoUsed).put("statsCounted", state.statsCounted)
        state.snapshot?.let { json.put("snapshot", snapshotJson(it)) }
        return json.toString()
    }

    fun decode(raw: String): GameState {
        val json = JSONObject(raw)
        val boardJson = json.getJSONArray("board")
        // Si el usuario actualiza desde la versión 10×10, descarta solo la partida
        // incompatible; LocalStore ya captura este error y conserva récord/estadísticas.
        require(boardJson.length() == 8 * 8) { "Saved board uses an incompatible grid size" }
        val board = List(boardJson.length()) { boardJson.getInt(it) }
        val trayJson = json.getJSONArray("pieces")
        val pieces = List(trayJson.length()) { index ->
            if (trayJson.isNull(index)) null else pieceFromJson(trayJson.getJSONObject(index))
        }
        val snapshot = json.optJSONObject("snapshot")?.let(::snapshotFromJson)
        return GameState(
            id = json.getLong("id"), mode = GameMode.valueOf(json.getString("mode")),
            board = board, pieces = pieces, score = json.optInt("score"), best = json.optInt("best"),
            lines = json.optInt("lines"), combo = json.optInt("combo"), bestCombo = json.optInt("bestCombo"),
            moves = json.optInt("moves"), remainingSeconds = json.optInt("remainingSeconds", 90),
            seed = json.getLong("seed"), batchIndex = json.optInt("batchIndex"),
            gameOver = json.optBoolean("gameOver"), won = json.optBoolean("won"),
            paused = false, newRecord = json.optBoolean("newRecord"), undoUsed = json.optBoolean("undoUsed"),
            snapshot = snapshot, statsCounted = json.optBoolean("statsCounted")
        )
    }

    private fun pieceJson(piece: Piece) = JSONObject().put("name", piece.name).put("color", piece.color)
        .put("cells", JSONArray().apply { piece.cells.forEach { put(JSONArray().put(it.row).put(it.col)) } })

    private fun pieceFromJson(json: JSONObject): Piece {
        val cellsJson = json.getJSONArray("cells")
        val cells = List(cellsJson.length()) { i ->
            val cell = cellsJson.getJSONArray(i)
            Cell(cell.getInt(0), cell.getInt(1))
        }
        return Piece(json.getString("name"), cells, json.getInt("color"))
    }

    private fun snapshotJson(snapshot: MoveSnapshot) = JSONObject()
        .put("board", JSONArray(snapshot.board))
        .put("pieces", JSONArray().apply { snapshot.pieces.forEach { put(it?.let(::pieceJson) ?: JSONObject.NULL) } })
        .put("score", snapshot.score).put("best", snapshot.best).put("lines", snapshot.lines).put("combo", snapshot.combo)
        .put("bestCombo", snapshot.bestCombo).put("moves", snapshot.moves)
        .put("remainingSeconds", snapshot.remainingSeconds).put("batchIndex", snapshot.batchIndex)
        .put("newRecord", snapshot.newRecord).put("gameOver", snapshot.gameOver).put("won", snapshot.won)

    private fun snapshotFromJson(json: JSONObject): MoveSnapshot {
        val boardJson = json.getJSONArray("board")
        require(boardJson.length() == 8 * 8) { "Saved snapshot uses an incompatible grid size" }
        val trayJson = json.getJSONArray("pieces")
        return MoveSnapshot(
            board = List(boardJson.length()) { boardJson.getInt(it) },
            pieces = List(trayJson.length()) { i -> if (trayJson.isNull(i)) null else pieceFromJson(trayJson.getJSONObject(i)) },
            score = json.optInt("score"), best = json.optInt("best"), lines = json.optInt("lines"), combo = json.optInt("combo"),
            bestCombo = json.optInt("bestCombo"), moves = json.optInt("moves"),
            remainingSeconds = json.optInt("remainingSeconds", 90), batchIndex = json.optInt("batchIndex"),
            newRecord = json.optBoolean("newRecord"), gameOver = json.optBoolean("gameOver"),
            won = json.optBoolean("won")
        )
    }
}
