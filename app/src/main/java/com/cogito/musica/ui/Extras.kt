package com.cogito.musica.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cogito.musica.AudioFx
import com.cogito.musica.Lyrics
import kotlin.math.roundToInt

// ───────────────────────── Ecualizador ─────────────────────────

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val st by AudioFx.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Label("← Volver", Modifier.tap(onClick = onBack).padding(vertical = 10.dp), Dim)
        Spacer(Modifier.height(10.dp))
        T("Ecualizador", 40.sp, italic = true)
        Spacer(Modifier.height(18.dp))

        if (!st.available) {
            T(
                "El ecualizador aparece cuando empieza a sonar algo.\nSi sigue sin aparecer, este teléfono no lo permite.",
                16.sp, Dim, italic = true, maxLines = 5,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RectButton(if (st.enabled) "Activado" else "Desactivado", filled = st.enabled) {
                AudioFx.setEnabled(!st.enabled)
            }
            RectButton("Plano") { AudioFx.reset() }
        }
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TextTab("Personal", active = st.preset < 0) { }
            st.presets.forEachIndexed { i, name ->
                TextTab(name, active = st.preset == i) { AudioFx.usePreset(i) }
            }
        }
        Hairline()
        Spacer(Modifier.height(18.dp))

        val alpha = if (st.enabled) 1f else 0.35f
        Row(Modifier.fillMaxWidth().weight(1f)) {
            st.bands.forEachIndexed { i, band ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().enter(80 + i * 50, offsetDp = 24f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val db = band.level / 100f
                    T((if (db > 0) "+" else "") + "%.0f".format(db), 13.sp, White.copy(alpha = alpha))
                    Spacer(Modifier.height(8.dp))
                    VerticalSlider(
                        target = (band.level - st.min).toFloat() / (st.max - st.min),
                        alpha = alpha,
                        modifier = Modifier.weight(1f).width(40.dp),
                    ) { v ->
                        val level = (st.min + v * (st.max - st.min)).roundToInt()
                        // pasos de 1 dB
                        AudioFx.setBand(i, (level / 100f).roundToInt() * 100)
                    }
                    Spacer(Modifier.height(8.dp))
                    T(freqLabel(band.centerHz), 12.sp, Dim.copy(alpha = Dim.alpha * alpha))
                }
            }
        }

        if (st.bassSupported) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Graves", color = White.copy(alpha = alpha))
                Spacer(Modifier.weight(1f))
                T("${st.bass / 10}%", 13.sp, Dim)
            }
            HorizontalSlider(st.bass / 1000f, alpha) { AudioFx.setBass((it * 1000).roundToInt()) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun freqLabel(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        (if (k >= 10) "%.0f".format(k) else "%.1f".format(k).replace(".0", "")) + "k"
    } else "$hz"

@Composable
private fun VerticalSlider(target: Float, alpha: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    val value by animateFloatAsState(target, spring(stiffness = Spring.StiffnessMedium), label = "eq")
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o -> cb((1f - o.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> cb((1f - change.position.y / size.height).coerceIn(0f, 1f)) }
            }
    ) {
        val cx = size.width / 2f
        val h = size.height
        val y = h * (1f - value.coerceIn(0f, 1f))
        drawLine(Faint, Offset(cx, 0f), Offset(cx, h), 1.dp.toPx())
        drawLine(Faint, Offset(cx - 8.dp.toPx(), h / 2f), Offset(cx + 8.dp.toPx(), h / 2f), 1.dp.toPx())
        drawLine(White.copy(alpha = alpha), Offset(cx, h / 2f), Offset(cx, y), 2.dp.toPx())
        val t = 14.dp.toPx()
        drawRect(White.copy(alpha = alpha), Offset(cx - t / 2f, (y - t / 2f).coerceIn(0f, h - t)), Size(t, t))
    }
}

@Composable
private fun HorizontalSlider(target: Float, alpha: Float, onChange: (Float) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    val value by animateFloatAsState(target, spring(stiffness = Spring.StiffnessMedium), label = "bass")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) { detectTapGestures { o -> cb((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> cb((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val y = size.height / 2f
        val w = size.width
        val x = w * value.coerceIn(0f, 1f)
        drawLine(Faint, Offset(0f, y), Offset(w, y), 1.dp.toPx())
        drawLine(White.copy(alpha = alpha), Offset(0f, y), Offset(x, y), 2.dp.toPx())
        val t = 12.dp.toPx()
        drawRect(White.copy(alpha = alpha), Offset((x - t / 2f).coerceIn(0f, w - t), y - t / 2f), Size(t, t))
    }
}

// ───────────────────────── Letra ─────────────────────────

@Composable
fun LyricsPanel(
    lyrics: Lyrics?,
    position: Long,
    duration: Long,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pasting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            if (!text.isNullOrBlank()) onSave(text)
        }
    }
    fun import() = importer.launch(arrayOf("text/*", "application/octet-stream"))

    Box(modifier) {
        if (lyrics == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                T("Esta canción no tiene letra.", 18.sp, Dim, italic = true)
                Spacer(Modifier.height(6.dp))
                T(
                    "Podés importar un archivo .lrc (con tiempos, sigue la canción) o .txt, o pegar el texto.",
                    13.sp, Faint, maxLines = 4, align = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RectButton("Importar") { import() }
                    RectButton("Pegar") { pasting = true }
                }
            }
        } else {
            val current = lyrics.indexAt(position, duration)
            if (lyrics.synced) {
                val listState = rememberLazyListState()
                LaunchedEffect(current) {
                    if (current >= 0) runCatching { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) }
                }
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    item { Spacer(Modifier.height(24.dp)) }
                    itemsIndexed(lyrics.lines) { i, line ->
                        val active = i == current
                        val color by animateColorAsState(
                            when {
                                active -> White
                                i < current -> Faint
                                else -> Dim
                            },
                            tween(420), label = "lc",
                        )
                        val size by animateFloatAsState(if (active) 22f else 18f, tween(420, easing = EaseOut), label = "ls")
                        T(
                            line.text.ifEmpty { "·" },
                            size.sp,
                            color,
                            italic = active,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    }
                    item { Spacer(Modifier.height(120.dp)) }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(12.dp))
                    lyrics.lines.forEachIndexed { i, line ->
                        T(
                            line.text.ifEmpty { " " }, 17.sp,
                            if (i == current) White else Dim,
                            italic = i == current, maxLines = 4,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(60.dp))
                }
            }
            Label(
                "Editar letra",
                Modifier
                    .align(Alignment.BottomEnd)
                    .background(Black)
                    .tap { menu = true }
                    .padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                Faint,
            )
        }
    }

    if (menu) {
        ChoiceDialog(
            title = "Letra",
            options = buildList {
                add("Importar archivo .lrc / .txt")
                add("Pegar texto")
                if (lyrics?.fromUser == true) add("Quitar la letra agregada")
            },
            onPick = { i ->
                menu = false
                when (i) {
                    0 -> import()
                    1 -> pasting = true
                    2 -> onDelete()
                }
            },
            onDismiss = { menu = false },
        )
    }
    if (pasting) {
        PasteDialog(
            onSave = { onSave(it); pasting = false },
            onDismiss = { pasting = false },
        )
    }
}

@Composable
private fun PasteDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(Black)
                .drawnBorder()
                .padding(20.dp)
        ) {
            T("Pegar letra", 22.sp, italic = true)
            Spacer(Modifier.height(6.dp))
            T("Si cada línea empieza con [mm:ss], la letra va a seguir la canción.", 13.sp, Faint, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = White, fontSize = 16.sp, fontFamily = Serif),
                cursorBrush = SolidColor(White),
                modifier = Modifier.fillMaxWidth().height(260.dp).border(1.dp, Faint).padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) T("Pegá acá la letra…", 16.sp, Faint, italic = true)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RectButton("Guardar", filled = true) { if (text.isNotBlank()) onSave(text) }
                RectButton("Cancelar", onClick = onDismiss)
            }
        }
    }
}
