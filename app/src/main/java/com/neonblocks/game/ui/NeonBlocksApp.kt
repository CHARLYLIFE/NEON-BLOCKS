package com.neonblocks.game.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonblocks.game.data.Cell
import com.neonblocks.game.data.GameMode
import com.neonblocks.game.data.GameState
import com.neonblocks.game.data.LocalStore
import com.neonblocks.game.data.Piece
import com.neonblocks.game.data.PlayerStats
import com.neonblocks.game.game.GameEngine
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max

private val Bg = Color(0xFF080D18)
private val Panel = Color(0xFF111B2D)
private val Muted = Color(0xFF8191AA)
private val Cyan = Color(0xFF42E8FF)
private val Purple = Color(0xFFA56CFF)
private val Palette = listOf(
    Color(0xFF42E8FF), Color(0xFF9B72FF), Color(0xFFFF55C8), Color(0xFF74F2A4),
    Color(0xFFFFB454), Color(0xFFFFE16B), Color(0xFF5B8CFF), Color(0xFF45E0C3),
    Color(0xFFFF6B6B), Color(0xFFB8F34A), Color(0xFF7CDAFF), Color(0xFFFF8AD8)
)

private enum class Route { MENU, GAME, STATS, HOW_TO, SETTINGS }

@Composable
fun NeonBlocksApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context.applicationContext) }
    val engine = remember { GameEngine() }
    var route by remember { mutableStateOf(Route.MENU) }
    var game by remember { mutableStateOf(store.loadGame()) }
    var sound by remember { mutableStateOf(store.soundEnabled()) }
    var haptics by remember { mutableStateOf(store.hapticsEnabled()) }
    var music by remember { mutableStateOf(store.musicEnabled()) }
    var reducedEffects by remember { mutableStateOf(store.reducedEffects()) }
    var stats by remember { mutableStateOf(store.stats()) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 42) }
    DisposableEffect(tone) { onDispose { tone.release() } }
    val musicPlayer = remember(context) {
        MediaPlayer.create(context.applicationContext, com.neonblocks.game.R.raw.ambient_loop)?.apply {
            isLooping = true
            setVolume(.18f, .18f)
        }
    }
    DisposableEffect(musicPlayer) { onDispose { musicPlayer?.release() } }
    LaunchedEffect(route, game?.paused, game?.gameOver, music) {
        val shouldPlay = music && route == Route.GAME && game?.paused == false && game?.gameOver == false
        if (shouldPlay) {
            if (musicPlayer?.isPlaying == false) musicPlayer.start()
        } else if (musicPlayer?.isPlaying == true) {
            musicPlayer.pause()
        }
    }

    fun start(mode: GameMode) {
        game = engine.newGame(mode, store.best(mode))
        route = Route.GAME
    }

    LaunchedEffect(route, game?.mode, game?.paused, game?.gameOver) {
        while (route == Route.GAME && game?.mode == GameMode.TIME_ATTACK && game?.paused == false && game?.gameOver == false) {
            delay(1_000)
            game = game?.let(engine::tickSecond)
        }
    }
    LaunchedEffect(game?.id, game?.gameOver) {
        val current = game
        if (current != null && current.gameOver && !current.statsCounted) {
            store.recordGame(current)
            stats = store.stats()
            game = current.copy(statsCounted = true)
        }
    }
    LaunchedEffect(game) {
        val current = game
        if (current != null && !current.gameOver) store.saveGame(current)
        else if (current?.gameOver == true) store.clearSavedGame()
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg, Color(0xFF0D1426), Bg)))) {
        when (route) {
            Route.MENU -> MenuScreen(
                bestClassic = store.best(GameMode.CLASSIC),
                hasSaved = store.loadGame()?.gameOver == false,
                onStart = ::start,
                onContinue = { game = store.loadGame(); route = Route.GAME },
                onStats = { stats = store.stats(); route = Route.STATS },
                onSettings = { route = Route.SETTINGS },
                onHowTo = { route = Route.HOW_TO }
            )
            Route.GAME -> {
                val current = game
                if (current != null) GameScreen(
                    state = current,
                    engine = engine,
                    haptics = haptics,
                    onSelect = { if (sound) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 45) },
                    reducedEffects = reducedEffects,
                    onState = { next ->
                        val cleared = game?.let { next.lines > it.lines } == true
                        game = next
                        if (next.undoUsed && next.snapshot == null) store.restoreBest(next.mode, next.best)
                        else if (next.newRecord) store.saveBest(next.mode, next.score)
                        if (sound) tone.startTone(
                            if (cleared) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                            if (cleared) 135 else 65
                        )
                    },
                    onPause = { game = current.copy(paused = true) },
                    onMenu = { game = null; route = Route.MENU },
                    onRestart = { start(current.mode) },
                    onResume = { game = current.copy(paused = false) }
                ) else MenuScreen(
                    bestClassic = store.best(GameMode.CLASSIC), hasSaved = false,
                    onStart = ::start, onContinue = {}, onStats = { route = Route.STATS },
                    onSettings = { route = Route.SETTINGS }, onHowTo = { route = Route.HOW_TO }
                )
            }
            Route.STATS -> StatsScreen(stats, GameMode.entries.associateWith { store.best(it) }, onBack = { route = Route.MENU })
            Route.HOW_TO -> HowToScreen(onBack = { route = Route.MENU })
            Route.SETTINGS -> SettingsScreen(
                sound = sound, haptics = haptics, music = music, reducedEffects = reducedEffects,
                onSound = { sound = it; store.setSoundEnabled(it) },
                onHaptics = { haptics = it; store.setHapticsEnabled(it) },
                onMusic = { music = it; store.setMusicEnabled(it) },
                onEffects = { reducedEffects = it; store.setReducedEffects(it) },
                onBack = { route = Route.MENU }
            )
        }
    }
}

@Composable
private fun MenuScreen(
    bestClassic: Int, hasSaved: Boolean, onStart: (GameMode) -> Unit, onContinue: () -> Unit,
    onStats: () -> Unit, onSettings: () -> Unit, onHowTo: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        LogoMark(Modifier.size(92.dp))
        Spacer(Modifier.height(18.dp))
        Text("NEON BLOCKS", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("PIENSA. COLOCA. BRILLA.", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.1.sp)
        Spacer(Modifier.height(25.dp))
        NeonPanel(Modifier.fillMaxWidth()) {
            Text("RÉCORD CLÁSICO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text("${bestClassic}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("Tu mejor partida", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        if (hasSaved) {
            NeonButton("CONTINUAR PARTIDA", Modifier.fillMaxWidth(), onClick = onContinue)
            Spacer(Modifier.height(10.dp))
        }
        Text("ELIGE TU MODO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(10.dp))
        ModeCard(GameMode.CLASSIC, "∞", Color(0xFF42E8FF)) { onStart(GameMode.CLASSIC) }
        ModeCard(GameMode.CHALLENGE, "◎", Color(0xFFA56CFF)) { onStart(GameMode.CHALLENGE) }
        ModeCard(GameMode.TIME_ATTACK, "◷", Color(0xFFFFB454)) { onStart(GameMode.TIME_ATTACK) }
        ModeCard(GameMode.DAILY, "✦", Color(0xFFFF55C8)) { onStart(GameMode.DAILY) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallMenuButton("ESTADÍSTICAS", onStats, Modifier.weight(1f))
            SmallMenuButton("CÓMO JUGAR", onHowTo, Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        SmallMenuButton("CONFIGURACIÓN", onSettings, Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Text("SIN CONEXIÓN · SIN ANUNCIOS · SIN SEGUIMIENTO", color = Muted, fontSize = 9.sp, letterSpacing = 0.8.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = size.width * .07f
        val block = (size.width - gap * 3) / 2f
        val colors = listOf(Cyan, Purple, Color(0xFFFF55C8), Color(0xFF74F2A4))
        for (i in 0..3) {
            val x = if (i % 2 == 0) gap else gap * 2 + block
            val y = if (i < 2) gap else gap * 2 + block
            drawRoundRect(colors[i].copy(alpha = .25f), Offset(x - 5, y - 5), Size(block + 10, block + 10), CornerRadius(block * .22f))
            drawRoundRect(Brush.linearGradient(listOf(colors[i], colors[i].copy(alpha = .55f))), Offset(x, y), Size(block, block), CornerRadius(block * .2f))
            drawRoundRect(Color.White.copy(alpha = .35f), Offset(x + block * .15f, y + block * .12f), Size(block * .7f, block * .12f), CornerRadius(8f))
        }
    }
}

@Composable
private fun ModeCard(mode: GameMode, symbol: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(Panel).border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Text(symbol, color = accent, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.sp)
            Text(mode.subtitle, color = Muted, fontSize = 11.sp)
        }
        Text("›", color = accent, fontSize = 29.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SmallMenuButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(45.dp), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF2A3A55))) {
        Text(label, color = Color(0xFFDCE9F8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp, maxLines = 1)
    }
}

@Composable
private fun GameScreen(
    state: GameState, engine: GameEngine, haptics: Boolean, onSelect: () -> Unit, reducedEffects: Boolean,
    onState: (GameState) -> Unit, onPause: () -> Unit, onMenu: () -> Unit,
    onRestart: () -> Unit, onResume: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selected by remember(state.id) { mutableIntStateOf(-1) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var boardRect by remember { mutableStateOf(Rect.Zero) }
    val trayCoordinates = remember { mutableStateListOf<LayoutCoordinates?>(null, null, null) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var showClear by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Arrastra una ficha o tócala y luego toca el tablero") }
    var previousLines by remember(state.id) { mutableIntStateOf(state.lines) }

    LaunchedEffect(state.lines) {
        if (state.lines > previousLines) {
            showClear = true
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(if (reducedEffects) 400 else 900)
            showClear = false
        }
        previousLines = state.lines
    }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootCoordinates = it }) {
        val boardSize = minOf(maxWidth - 28.dp, maxHeight * .47f, 430.dp).coerceAtLeast(220.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NEON BLOCKS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text(state.mode.title, color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                }
                TinyAction("↶", enabled = state.canUndo) { onState(engine.undo(state)); selected = -1 }
                Spacer(Modifier.width(7.dp))
                TinyAction("Ⅱ", enabled = true, onClick = onPause)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreCard("PUNTOS", state.score.toString(), Modifier.weight(1.15f), Cyan)
                ScoreCard("RÉCORD", state.best.toString(), Modifier.weight(1f), Purple)
                val centerLabel = when (state.mode) {
                    GameMode.TIME_ATTACK -> "TIEMPO"
                    GameMode.CHALLENGE, GameMode.DAILY -> "OBJETIVO"
                    else -> "LÍNEAS"
                }
                val centerValue = when (state.mode) {
                    GameMode.TIME_ATTACK -> "${state.remainingSeconds}s"
                    GameMode.CHALLENGE -> "${state.score}/${GameEngine.CHALLENGE_TARGET}"
                    GameMode.DAILY -> "${state.score}/${GameEngine.DAILY_TARGET}"
                    else -> state.lines.toString()
                }
                ScoreCard(centerLabel, centerValue, Modifier.weight(1f), Color(0xFFFFB454))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("NIVEL ${state.score / 500 + 1}  ·  " + if (state.combo > 1) "COMBO ×${state.combo}" else "MOV. ${state.moves}", color = if (state.combo > 1) Color(0xFFFFB454) else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("MEJOR ×${state.bestCombo}", color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier.size(boardSize).align(Alignment.CenterHorizontally)
                    .onGloballyPositioned { coords ->
                        val root = rootCoordinates
                        if (root != null) {
                            val p = root.localPositionOf(coords, Offset.Zero)
                            boardRect = Rect(p.x, p.y, p.x + coords.size.width, p.y + coords.size.height)
                        }
                    }
                    .shadow(16.dp, RoundedCornerShape(15.dp))
                    .border(1.dp, Color(0xFF2A3B5B), RoundedCornerShape(15.dp))
            ) {
                BoardCanvas(
                    state = state,
                    preview = if (dragIndex >= 0) state.pieces.getOrNull(dragIndex) else null,
                    previewOrigin = if (dragIndex >= 0 && boardRect.width > 0f) {
                        val cellPx = boardRect.width / GameEngine.BOARD_SIZE
                        val piece = state.pieces.getOrNull(dragIndex)
                        val row = floor((dragPosition.y - boardRect.top - ((piece?.height ?: 1) * cellPx / 2f)) / cellPx).toInt()
                        val col = floor((dragPosition.x - boardRect.left - ((piece?.width ?: 1) * cellPx / 2f)) / cellPx).toInt()
                        CellOrigin(row, col)
                    } else null,
                    previewValid = dragIndex >= 0 && boardRect.width > 0f && run {
                        val cellPx = boardRect.width / GameEngine.BOARD_SIZE
                        val piece = state.pieces.getOrNull(dragIndex)
                        val row = floor((dragPosition.y - boardRect.top - ((piece?.height ?: 1) * cellPx / 2f)) / cellPx).toInt()
                        val col = floor((dragPosition.x - boardRect.left - ((piece?.width ?: 1) * cellPx / 2f)) / cellPx).toInt()
                        engine.canPlace(state, dragIndex, row, col)
                    },
                    onTap = { point ->
                        if (selected >= 0 && state.pieces.getOrNull(selected) != null) {
                            val piece = state.pieces[selected]!!
                            val row = point.y.toInt() - piece.height / 2
                            val col = point.x.toInt() - piece.width / 2
                            val next = engine.place(state, selected, row, col)
                            if (next.moves != state.moves) {
                                onState(next); selected = -1; statusMessage = "Ficha colocada"
                                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else statusMessage = "Esa posición no está disponible"
                        } else statusMessage = "Selecciona una ficha primero"
                    }
                )
                if (showClear && !reducedEffects) ParticleBurst(Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(showClear && !reducedEffects) {
                Text("✦ LÍNEA ELIMINADA ✦", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            }
            Text(statusMessage, color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TUS FICHAS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text("${state.pieces.count { it != null }} DISPONIBLES", color = Muted, fontSize = 9.sp)
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                state.pieces.forEachIndexed { index, piece ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .onGloballyPositioned { trayCoordinates[index] = it }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected == index) Color(0xFF1D3150) else Panel)
                            .border(1.dp, if (selected == index) Cyan else Color(0xFF263650), RoundedCornerShape(14.dp))
                            .clickable(enabled = piece != null) {
                                selected = if (selected == index) -1 else index
                                statusMessage = if (selected >= 0) "Ahora toca una casilla del tablero" else "Ficha deseleccionada"
                                onSelect()
                            }
                            .pointerInput(index, piece, boardRect, state.id) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        if (piece != null) {
                                            dragIndex = index
                                            val root = rootCoordinates
                                            val coords = trayCoordinates[index]
                                            dragPosition = if (root != null && coords != null) root.localPositionOf(coords, start) else Offset.Zero
                                            selected = -1
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragPosition += amount
                                    },
                                    onDragEnd = {
                                        val active = state.pieces.getOrNull(dragIndex)
                                        if (active != null && boardRect.width > 0f && dragPosition.x in boardRect.left..boardRect.right && dragPosition.y in boardRect.top..boardRect.bottom) {
                                            val cellPx = boardRect.width / GameEngine.BOARD_SIZE
                                            val row = floor((dragPosition.y - boardRect.top - active.height * cellPx / 2f) / cellPx).toInt()
                                            val col = floor((dragPosition.x - boardRect.left - active.width * cellPx / 2f) / cellPx).toInt()
                                            val next = engine.place(state, dragIndex, row, col)
                                            if (next.moves > state.moves) {
                                                onState(next); statusMessage = "¡Buena colocación!"
                                                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            } else statusMessage = "No cabe ahí. Prueba otra posición."
                                        } else statusMessage = "Suelta la ficha dentro del tablero"
                                        dragIndex = -1
                                    },
                                    onDragCancel = { dragIndex = -1 }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (piece != null) PiecePreview(piece, Modifier.fillMaxSize().padding(10.dp))
                        else Text("✓", color = Color(0xFF40516B), fontSize = 24.sp)
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text("ARRASTRA UNA FICHA · O SELECCIÓNALA Y TOCA EL TABLERO", color = Muted, fontSize = 8.sp, letterSpacing = .45.sp, textAlign = TextAlign.Center)
        }

        if (dragIndex >= 0 && state.pieces.getOrNull(dragIndex) != null && boardRect.width > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val piece = state.pieces[dragIndex] ?: return@Canvas
                val cell = boardRect.width / GameEngine.BOARD_SIZE.toFloat() * .72f
                val w = piece.width * cell
                val h = piece.height * cell
                val left = dragPosition.x - w / 2f
                val top = dragPosition.y - h / 2f
                piece.cells.forEach { c ->
                    drawNeonBlock(Offset(left + c.col * cell, top + c.row * cell), Size(cell * .9f, cell * .9f), Palette[piece.color])
                }
            }
        }

        if (state.paused && confirmRestart) {
            OverlayDialog(
                title = "¿REINICIAR PARTIDA?",
                body = "Perderás el progreso de esta partida. El récord histórico se conservará.",
                primary = "SÍ, REINICIAR",
                onPrimary = { confirmRestart = false; onRestart() },
                secondary = "CANCELAR",
                onSecondary = { confirmRestart = false }
            )
        } else if (state.paused) {
            OverlayDialog("PARTIDA EN PAUSA", "Tu tablero está guardado automáticamente.",
                primary = "CONTINUAR", onPrimary = onResume,
                secondary = "REINICIAR", onSecondary = { confirmRestart = true },
                tertiary = "MENÚ PRINCIPAL", onTertiary = onMenu)
        } else if (state.gameOver) {
            OverlayDialog(
                title = if (state.won) "¡DESAFÍO COMPLETADO!" else "FIN DE LA PARTIDA",
                body = "Puntuación ${state.score}  ·  ${state.lines} líneas  ·  Combo ×${state.bestCombo}" + if (state.newRecord) "\n✦ NUEVO RÉCORD ✦" else "",
                primary = "JUGAR OTRA VEZ", onPrimary = onRestart,
                secondary = "MENÚ PRINCIPAL", onSecondary = onMenu
            )
        }
    }
}

private data class CellOrigin(val row: Int, val col: Int)

@Composable
private fun ScoreCard(label: String, value: String, modifier: Modifier, accent: Color) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, accent.copy(alpha = .18f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 8.dp)) {
        Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
        Text(value, color = accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun TinyAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(37.dp).clip(CircleShape).background(Panel).border(1.dp, Color(0xFF2A3A55), CircleShape)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) Color.White else Muted.copy(alpha = .35f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoardCanvas(
    state: GameState, preview: Piece?, previewOrigin: CellOrigin?, previewValid: Boolean, onTap: (Offset) -> Unit
) {
    Canvas(Modifier.fillMaxSize().pointerInput(state, preview, onTap) {
        val cellWidth = size.width / GameEngine.BOARD_SIZE.toFloat()
        val cellHeight = size.height / GameEngine.BOARD_SIZE.toFloat()
        detectTapGestures { tapped -> onTap(Offset(tapped.x / cellWidth, tapped.y / cellHeight)) }
    }) {
        val cell = size.width / GameEngine.BOARD_SIZE.toFloat()
        drawRoundRect(Color(0xFF101A2C), size = size, cornerRadius = CornerRadius(cell * .19f))
        for (r in 0 until GameEngine.BOARD_SIZE) for (c in 0 until GameEngine.BOARD_SIZE) {
            val left = c * cell + cell * .055f
            val top = r * cell + cell * .055f
            val rectSize = Size(cell * .89f, cell * .89f)
            val color = state.board[r * GameEngine.BOARD_SIZE + c]
            if (color == -1) {
                drawRoundRect(Color(0xFF17243A), Offset(left, top), rectSize, CornerRadius(cell * .13f))
                drawRoundRect(Color(0xFF263750).copy(alpha = .6f), Offset(left, top), rectSize, CornerRadius(cell * .13f), style = Stroke(width = 1f))
            } else {
                drawNeonBlock(Offset(left, top), rectSize, Palette[color.coerceIn(Palette.indices)])
            }
        }
        if (preview != null && previewOrigin != null) {
            val shade = if (previewValid) Color(0xFF72F4A7) else Color(0xFFFF5B78)
            preview.cells.forEach { p ->
                val r = previewOrigin.row + p.row
                val c = previewOrigin.col + p.col
                if (r in 0..9 && c in 0..9) {
                    drawRoundRect(shade.copy(alpha = .48f), Offset(c * cell + cell * .07f, r * cell + cell * .07f), Size(cell * .86f, cell * .86f), CornerRadius(cell * .12f))
                    drawRoundRect(shade, Offset(c * cell + cell * .07f, r * cell + cell * .07f), Size(cell * .86f, cell * .86f), CornerRadius(cell * .12f), style = Stroke(width = 2.2f))
                }
            }
        }
    }
}

@Composable
private fun PiecePreview(piece: Piece, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = minOf(size.width / (piece.width + .35f), size.height / (piece.height + .35f))
        val totalW = piece.width * unit
        val totalH = piece.height * unit
        val left = (size.width - totalW) / 2f
        val top = (size.height - totalH) / 2f
        piece.cells.forEach { cell ->
            drawNeonBlock(Offset(left + cell.col * unit, top + cell.row * unit), Size(unit * .88f, unit * .88f), Palette[piece.color])
        }
    }
}

private fun DrawScope.drawNeonBlock(topLeft: Offset, blockSize: Size, color: Color) {
    val radius = CornerRadius(blockSize.minDimension * .20f)
    drawRoundRect(color.copy(alpha = .18f), Offset(topLeft.x - 3f, topLeft.y - 3f), Size(blockSize.width + 6f, blockSize.height + 6f), CornerRadius(radius.x + 3f))
    drawRoundRect(Brush.linearGradient(listOf(color.copy(alpha = .82f), color, lerp(color, Color.Black, .28f)), start = topLeft, end = Offset(topLeft.x + blockSize.width, topLeft.y + blockSize.height)), topLeft, blockSize, radius)
    drawRoundRect(Color.White.copy(alpha = .36f), Offset(topLeft.x + blockSize.width * .15f, topLeft.y + blockSize.height * .12f), Size(blockSize.width * .68f, blockSize.height * .12f), CornerRadius(blockSize.height * .08f))
    drawRoundRect(Color.White.copy(alpha = .14f), topLeft, blockSize, radius, style = Stroke(width = max(1f, blockSize.minDimension * .025f)))
}

@Composable
private fun ParticleBurst(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 720))
    }
    Canvas(modifier) {
        val t = progress.value
        val colors = listOf(Cyan, Purple, Color(0xFFFF55C8), Color(0xFF74F2A4), Color(0xFFFFB454))
        repeat(28) { i ->
            val angle = i * 137.5 * Math.PI / 180.0
            val distance = size.minDimension * (.06f + (i % 6) * .035f) * t * 3.0f
            val x = size.width / 2f + (cos(angle) * distance).toFloat()
            val y = size.height / 2f + (sin(angle) * distance).toFloat()
            val radius = (2.2f + (i % 3) * 1.1f) * (1f - t * .35f)
            drawCircle(colors[i % colors.size].copy(alpha = (1f - t).coerceIn(0f, 1f)), radius = radius, center = Offset(x, y))
        }
    }
}

@Composable
private fun StatsScreen(stats: PlayerStats, bests: Map<GameMode, Int>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        TopBar("ESTADÍSTICAS", onBack)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("PARTIDAS", stats.gamesPlayed.toString(), Modifier.weight(1f))
            StatTile("LÍNEAS", stats.totalLines.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        StatTile("PUNTOS ACUMULADOS", stats.totalScore.toString(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        StatTile("MEJOR COMBO", "×${stats.bestCombo}", Modifier.fillMaxWidth())
        Spacer(Modifier.height(22.dp))
        Text("RÉCORDS POR MODO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(9.dp))
        GameMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mode.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text((bests[mode] ?: 0).toString(), color = Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            HorizontalDivider(color = Color(0xFF263650))
        }
        Spacer(Modifier.height(22.dp))
        Text("LOGROS DESBLOQUEABLES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        val maxRecord = bests.values.maxOrNull() ?: 0
        AchievementTile("Primera limpieza", "Elimina tu primera línea", stats.totalLines >= 1)
        AchievementTile("Centenario", "Consigue 100 puntos en una partida", maxRecord >= 100)
        AchievementTile("Combo experto", "Alcanza un combo de ×3", stats.bestCombo >= 3)
        AchievementTile("Constancia", "Completa 10 partidas", stats.gamesPlayed >= 10)
        AchievementTile("Coleccionista", "Acumula 10.000 puntos", stats.totalScore >= 10_000)
        AchievementTile("Leyenda clásica", "Supera los 5.000 puntos en Clásico", (bests[GameMode.CLASSIC] ?: 0) >= 5_000)
    }
}

@Composable
private fun AchievementTile(title: String, detail: String, unlocked: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(13.dp))
            .background(if (unlocked) Color(0xFF152B35) else Panel)
            .border(1.dp, if (unlocked) Color(0xFF317D7A) else Color(0xFF263650), RoundedCornerShape(13.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (unlocked) "✦" else "◇", color = if (unlocked) Cyan else Muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (unlocked) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
        Text(if (unlocked) "LOGRADO" else "BLOQUEADO", color = if (unlocked) Color(0xFF74F2A4) else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    NeonPanel(modifier) {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        Text(value, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun HowToScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        TopBar("CÓMO JUGAR", onBack)
        Spacer(Modifier.height(18.dp))
        HowStep("01", "ELIGE", "Toca una de las tres fichas de abajo o arrástrala directamente.")
        HowStep("02", "COLOCA", "Acomoda la ficha en cualquier espacio libre del tablero 8 × 8. No hay gravedad ni caída automática.")
        HowStep("03", "COMPLETA", "Llena una fila horizontal o una columna vertical para eliminarla y liberar espacio.")
        HowStep("04", "COMBINA", "Limpia varias líneas en una jugada y encadena limpiezas para aumentar tu combo.")
        HowStep("05", "SOBREVIVE", "Cada vez que uses las tres fichas recibirás otras tres. La partida termina cuando ninguna pueda colocarse.")
        Spacer(Modifier.height(16.dp))
        NeonPanel(Modifier.fillMaxWidth()) {
            Text("PUNTUACIÓN", color = Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text("10 puntos por bloque · 100 por línea · bonificación por líneas múltiples y combos.", color = Color.White, fontSize = 12.sp)
            Text("Puedes deshacer un movimiento por partida. El tablero se guarda automáticamente.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HowStep(number: String, title: String, description: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(number, color = Cyan, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .9.sp)
            Text(description, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    sound: Boolean, haptics: Boolean, music: Boolean, reducedEffects: Boolean,
    onSound: (Boolean) -> Unit, onHaptics: (Boolean) -> Unit, onMusic: (Boolean) -> Unit,
    onEffects: (Boolean) -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        TopBar("CONFIGURACIÓN", onBack)
        Spacer(Modifier.height(20.dp))
        SettingRow("Sonido", "Tonos al seleccionar, colocar y limpiar líneas", sound, onSound)
        SettingRow("Vibración", "Respuesta háptica al limpiar líneas", haptics, onHaptics)
        SettingRow("Música ambiental", "Paisaje sonoro original en bucle durante la partida", music, onMusic)
        SettingRow("Efectos reducidos", "Menos destellos y animaciones", reducedEffects, onEffects)
        Spacer(Modifier.height(18.dp))
        Text("NEON BLOCKS funciona sin conexión y no solicita permisos sensibles.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
    HorizontalDivider(color = Color(0xFF263650))
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TinyAction("‹", enabled = true, onClick = onBack)
        Spacer(Modifier.width(13.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun NeonPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF17243A), Color(0xFF10192A))))
        .border(1.dp, Color(0xFF2B3D5A), RoundedCornerShape(17.dp)).padding(16.dp), content = content)
}

@Composable
private fun NeonButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color(0xFF06101C))) {
        Text(text, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp, fontSize = 12.sp)
    }
}

@Composable
private fun OverlayDialog(
    title: String, body: String, primary: String, onPrimary: () -> Unit,
    secondary: String, onSecondary: () -> Unit, tertiary: String? = null, onTertiary: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.88f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF111B2D))
            .border(1.dp, Color(0xFF3A5479), RoundedCornerShape(24.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(Modifier.size(54.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(7.dp))
            Text(body, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
            Spacer(Modifier.height(18.dp))
            NeonButton(primary, Modifier.fillMaxWidth(), onPrimary)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth().height(47.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF3A5479))
            ) {
                Text(secondary, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            }
            if (tertiary != null && onTertiary != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onTertiary) { Text(tertiary, color = Muted, fontSize = 11.sp) }
            }
        }
    }
}

