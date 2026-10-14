package com.cogito.musica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
) {
    BackHandler(onBack = onClose)
    var modeIndex by rememberSaveable { mutableIntStateOf(0) }
    val mode = VizMode.entries[modeIndex % VizMode.entries.size]
    val now = ui.now

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
            Label("Cerrar", Modifier.clickable(onClick = onClose).padding(vertical = 10.dp), Dim)
            Spacer(Modifier.weight(1f))
            Label("Reproduciendo", color = Faint)
            Spacer(Modifier.weight(1f))
            Label(mode.label, Modifier.clickable { modeIndex++ }.padding(vertical = 10.dp), Dim)
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.fillMaxWidth().weight(1f).clickable { if (hasMic) modeIndex++ else onRequestMic() },
            contentAlignment = Alignment.Center,
        ) {
            if (hasMic) {
                BigVisualizer(viz, frame, mode, Modifier.fillMaxSize())
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

        Spacer(Modifier.height(24.dp))

        T(now?.title ?: "", 30.sp, italic = true, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        T(now?.artist ?: "", 17.sp, Dim)
        if (!now?.album.isNullOrEmpty()) T(now!!.album, 14.sp, Faint, italic = true)

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
            Box(Modifier.width(76.dp).clickable(onClick = onShuffle).padding(vertical = 12.dp)) {
                Label("Aleat.", color = if (ui.shuffle) White else Faint)
            }
            Box(Modifier.size(52.dp).clickable(onClick = onPrev), contentAlignment = Alignment.Center) {
                SkipGlyph(false, Modifier.size(18.dp))
            }
            Box(
                Modifier.size(72.dp).border(1.dp, White).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                PlayPauseGlyph(ui.isPlaying, Modifier.size(22.dp))
            }
            Box(Modifier.size(52.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
                SkipGlyph(true, Modifier.size(18.dp))
            }
            Box(
                Modifier.width(76.dp).clickable(onClick = onRepeat).padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val (text, on) = when (ui.repeat) {
                    Player.REPEAT_MODE_ALL -> "Rep. todo" to true
                    Player.REPEAT_MODE_ONE -> "Rep. una" to true
                    else -> "Repetir" to false
                }
                Label(text, color = if (on) White else Faint)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
