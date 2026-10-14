package com.cogito.musica.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.cogito.musica.AudioViz
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class VizMode(val label: String) { BARS("Barras"), WAVE("Onda"), RING("Anillo"), LYRICS("Letra") }

/** Avanza la animación en cada cuadro de pantalla. Leer `.value` dentro de un Canvas lo redibuja. */
@Composable
fun rememberVizFrame(viz: AudioViz): State<Long> {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(viz) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                viz.step(dt)
                tick.longValue = now
            }
        }
    }
    return tick
}

@Composable
fun BigVisualizer(viz: AudioViz, frame: State<Long>, mode: VizMode, modifier: Modifier = Modifier) {
    when (mode) {
        VizMode.BARS -> BarsViz(viz, frame, modifier)
        VizMode.WAVE -> WaveViz(viz, frame, modifier)
        VizMode.RING -> RingViz(viz, frame, modifier)
        VizMode.LYRICS -> Unit
    }
}

/** Barras simétricas: graves al centro, agudos hacia los bordes, con reflejo tenue. */
@Composable
private fun BarsViz(viz: AudioViz, frame: State<Long>, modifier: Modifier) {
    Canvas(modifier) {
        frame.value
        val n = viz.bandCount
        val total = n * 2
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (total - 1)) / total
        val base = size.height * 0.66f
        val minH = 1.dp.toPx()
        for (j in 0 until total) {
            val band = if (j < n) n - 1 - j else j - n
            val v = viz.bands[band]
            val h = (v * base * 0.96f).coerceAtLeast(minH)
            val x = j * (w + gap)
            drawRect(White, Offset(x, base - h), Size(w, h))
            drawRect(White.copy(alpha = 0.16f), Offset(x, base + gap * 2), Size(w, h * 0.35f))
        }
    }
}

/** Osciloscopio: la forma de onda real de lo que suena. */
@Composable
private fun WaveViz(viz: AudioViz, frame: State<Long>, modifier: Modifier) {
    Canvas(modifier) {
        frame.value
        val mid = size.height / 2f
        val amp = size.height * 0.42f
        val step = size.width / (viz.wavePoints - 1)
        val path = Path()
        for (i in 0 until viz.wavePoints) {
            val x = i * step
            val y = mid + viz.wave[i] * amp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(White.copy(alpha = 0.15f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        drawPath(path, White, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** Anillo de rayos alrededor de un cuadrado que gira y late con los graves. */
@Composable
private fun RingViz(viz: AudioViz, frame: State<Long>, modifier: Modifier) {
    Canvas(modifier) {
        frame.value
        val c = center
        val s = min(size.width, size.height)
        val r0 = s * 0.22f * (1f + 0.18f * viz.bass)
        val n = viz.bandCount
        val total = n * 2
        val stroke = 2.dp.toPx()
        for (k in 0 until total) {
            val band = if (k < n) k else total - 1 - k
            val v = viz.bands[band]
            val a = (k / total.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val len = 2.dp.toPx() + v * s * 0.26f
            val dx = cos(a)
            val dy = sin(a)
            drawLine(
                White,
                Offset(c.x + dx * r0, c.y + dy * r0),
                Offset(c.x + dx * (r0 + len), c.y + dy * (r0 + len)),
                strokeWidth = stroke,
                cap = StrokeCap.Butt,
            )
        }
        val sq = r0 * 1.1f
        rotate(degrees = viz.time * 12f, pivot = c) {
            drawRect(
                White,
                topLeft = Offset(c.x - sq / 2f, c.y - sq / 2f),
                size = Size(sq, sq),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        drawCircle(White, radius = 2.dp.toPx() + viz.bass * 4.dp.toPx(), center = c)
    }
}

/** Cuatro barritas para la lista y el mini reproductor. */
@Composable
fun MiniBars(viz: AudioViz, frame: State<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        frame.value
        val idx = intArrayOf(1, 6, 13, 22)
        val gap = size.width * 0.12f
        val w = (size.width - gap * (idx.size - 1)) / idx.size
        val minH = 1.5.dp.toPx()
        idx.forEachIndexed { i, b ->
            val v = viz.bands[b.coerceAtMost(viz.bandCount - 1)]
            val h = (v * size.height).coerceAtLeast(minH)
            drawRect(White, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
    }
}
