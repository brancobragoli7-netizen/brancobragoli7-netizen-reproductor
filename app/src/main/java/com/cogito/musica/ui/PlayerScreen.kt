package com.cogito.musica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.cogito.musica.AudioViz
import com.cogito.musica.PlayerUi
import com.cogito.musica.Lyrics
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun PlayerScreen(
    ui: PlayerUi,
    viz: AudioViz,
    frame: State<Long>,
    hasMic: Boolean,
    onRequestMic: () -> Unit,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    lyrics: Lyrics?,
    onSaveLyrics: (String) -> Unit,
    onDeleteLyrics: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var modeIndex by rememberSaveable { mutableIntStateOf(0) }
    val mode = VizMode.entries[modeIndex % VizMode.entries.size]
    val now = ui.now
    var showEq by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(Unit) { detectTapGestures { } } // no deja pasar toques a la lista de atrás
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Barra superior (también se puede deslizar hacia abajo para cerrar)
        Row(
            Modifier.fillMaxWidth().pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 120.dp.toPx()) onClose() },
                ) { _, dy -> total += dy }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Cerrar", Modifier.tap(onClick = onClose).padding(vertical = 10.dp), Dim)
            Spacer(Modifier.weight(1f))
            Label("Ecualizador", Modifier.tap { showEq = true }.padding(vertical = 10.dp), Dim)
            Spacer(Modifier.weight(1f))
            Label(mode.label, Modifier.tap { modeIndex++ }.padding(vertical = 10.dp), Dim)
        }

        Spacer(Modifier.height(12.dp))

        Crossfade(
            targetState = mode,
            animationSpec = tween(420, easing = EaseInOut),
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = "mode",
        ) { m ->
            if (m == VizMode.LYRICS) {
                LyricsPanel(
                    lyrics = lyrics,
                    position = ui.position,
                    duration = ui.duration,
                    onSave = onSaveLyrics,
                    onDelete = onDeleteLyrics,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().tap { if (hasMic) modeIndex++ else onRequestMic() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasMic) {
                        BigVisualizer(viz, frame, m, Modifier.fillMaxSize())
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T(
                                "Las animaciones necesitan el permiso de audio.\nNo se graba ni se envía nada.",
                                16.sp, Dim, italic = true, maxLines = 4, align = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            RectButton("Activar animaciones", onClick = onRequestMic)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        AnimatedContent(
            targetState = now,
            contentKey = { it?.id },
            transitionSpec = {
                (slideInVertically(tween(460, easing = EaseOut)) { h -> h / 3 } + fadeIn(tween(380, delayMillis = 60)))
                    .togetherWith(slideOutVertically(tween(260, easing = EaseInOut)) { h -> -h / 5 } + fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "title",
        ) { n ->
            Column(Modifier.fillMaxWidth()) {
                T(n?.title ?: "", 30.sp, italic = true, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                T(n?.artist ?: "", 17.sp, Dim)
                if (!n?.album.isNullOrEmpty()) T(n!!.album, 14.sp, Faint, italic = true)
            }
        }

        Spacer(Modifier.height(14.dp))
        // Línea que late con los graves
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            frame.value
            drawRect(Faint, size = size)
            drawRect(White, size = Size(size.width * viz.bass.coerceIn(0f, 1f), size.height))
        }
        Spacer(Modifier.height(18.dp))

        SeekBar(ui.position, ui.duration, onSeek)

        Spacer(Modifier.height(22.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(76.dp).tap(onClick = onShuffle).padding(vertical = 12.dp)) {
                val c by animateColorAsState(if (ui.shuffle) White else Faint, tween(300), label = "sh")
                Label("Aleat.", color = c)
            }
            Box(Modifier.size(52.dp).tap(onClick = onPrev), contentAlignment = Alignment.Center) {
                SkipGlyph(false, Modifier.size(18.dp))
            }
            BigPlayButton(ui.isPlaying, onToggle)
            Box(Modifier.size(52.dp).tap(onClick = onNext), contentAlignment = Alignment.Center) {
                SkipGlyph(true, Modifier.size(18.dp))
            }
            Box(
                Modifier.width(76.dp).tap(onClick = onRepeat).padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val (text, on) = when (ui.repeat) {
                    Player.REPEAT_MODE_ALL -> "Rep. todo" to true
                    Player.REPEAT_MODE_ONE -> "Rep. una" to true
                    else -> "Repetir" to false
                }
                val c by animateColorAsState(if (on) White else Faint, tween(300), label = "rp")
                AnimatedContent(text, transitionSpec = { riseTransition() }, label = "rep") { tx -> Label(tx, color = c) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    AnimatedVisibility(
        visible = showEq,
        enter = slideInHorizontally(tween(440, easing = EaseOut)) { it / 3 } + fadeIn(tween(300)),
        exit = slideOutHorizontally(tween(300, easing = EaseInOut)) { it / 3 } + fadeOut(tween(200)),
    ) {
        EqualizerScreen(onBack = { showEq = false })
    }
    }
}

/** Botón cuadrado de play: el blanco crece desde el centro al apretarlo y el ícono se invierte. */
@Composable
private fun BigPlayButton(playing: Boolean, onToggle: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(
        if (pressed) 1f else 0f,
        tween(if (pressed) 160 else 420, easing = EaseOut),
        label = "play",
    )
    Box(
        Modifier
            .size(72.dp)
            .graphicsLayer { val k = 0.06f * p; scaleX = 1f - k; scaleY = 1f - k }
            .border(1.dp, White)
            .clickable(interactionSource = source, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().graphicsLayer { scaleX = p; scaleY = p }.background(White))
        PlayPauseGlyph(playing, Modifier.size(22.dp), color = lerp(White, Black, p))
    }
}
