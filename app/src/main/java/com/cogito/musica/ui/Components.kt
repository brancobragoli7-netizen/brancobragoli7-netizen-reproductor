package com.cogito.musica.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cogito.musica.R

// Liberation Serif: mismas medidas que Times New Roman, de uso libre.
val Serif = FontFamily(
    Font(R.font.serif_regular),
    Font(R.font.serif_italic, style = FontStyle.Italic),
    Font(R.font.serif_bold, weight = FontWeight.Bold),
    Font(R.font.serif_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic),
)

val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Dim = Color(0xA6FFFFFF)
val Faint = Color(0x40FFFFFF)

@Composable
fun T(
    text: String,
    size: TextUnit = 16.sp,
    color: Color = White,
    modifier: Modifier = Modifier,
    italic: Boolean = false,
    bold: Boolean = false,
    maxLines: Int = 1,
    spacing: TextUnit = TextUnit.Unspecified,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = Serif,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = spacing,
            textAlign = align,
        ),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Etiqueta en mayúsculas espaciadas. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = White, size: TextUnit = 11.sp) {
    T(text.uppercase(), size = size, color = color, modifier = modifier, spacing = 2.sp)
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Faint) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * Botón rectangular con borde fino; lleno (blanco) cuando está activo.
 * Al apretarlo, el color contrario barre el botón de izquierda a derecha.
 */
@Composable
fun RectButton(text: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val wipe by animateFloatAsState(
        if (pressed) 1f else 0f,
        tween(if (pressed) 200 else 460, easing = EaseOut),
        label = "wipe",
    )
    val fill by animateFloatAsState(if (filled) 1f else 0f, tween(340, easing = EaseOut), label = "fill")
    val bg = lerp(Black, White, fill)
    val fg = lerp(White, Black, fill)
    Box(
        modifier
            .graphicsLayer {
                val k = 0.03f * wipe
                scaleX = 1f - k
                scaleY = 1f - k
            }
            .border(1.dp, White)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.background(bg).padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Label(text, color = fg, size = 12.sp)
        }
        if (wipe > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent { clipRect(right = size.width * wipe) { this@drawWithContent.drawContent() } }
                    .background(fg),
                contentAlignment = Alignment.Center,
            ) {
                Label(text, color = bg, size = 12.sp)
            }
        }
    }
}

/** Pestaña de texto: el subrayado crece desde la izquierda al activarse. */
@Composable
fun TextTab(text: String, active: Boolean, onClick: () -> Unit) {
    val p by animateFloatAsState(if (active) 1f else 0f, tween(380, easing = EaseOut), label = "tab")
    Column(Modifier.width(IntrinsicSize.Max).tap(onClick = onClick).padding(vertical = 8.dp)) {
        Label(text, color = lerp(Faint, White, p), size = 12.sp)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(1.dp).fillMaxWidth(p).background(White))
    }
}

/** El triángulo de play se parte en dos y se convierte en las barras de pausa. */
@Composable
fun PlayPauseGlyph(playing: Boolean, modifier: Modifier = Modifier, color: Color = White) {
    val t by animateFloatAsState(if (playing) 1f else 0f, tween(280, easing = EaseInOut), label = "pp")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val a = w * 0.12f
        val b = w * 0.98f
        val m = (a + b) / 2f
        fun l(x: Float, y: Float) = x + (y - x) * t
        // Cuatro esquinas de cada mitad: arriba-izq, arriba-der, abajo-der, abajo-izq
        val left = listOf(
            Offset(l(a, w * 0.12f), 0f), Offset(l(m, w * 0.40f), l(h / 4f, 0f)),
            Offset(l(m, w * 0.40f), l(3f * h / 4f, h)), Offset(l(a, w * 0.12f), h),
        )
        val right = listOf(
            Offset(l(m, w * 0.60f), l(h / 4f, 0f)), Offset(l(b, w * 0.88f), l(h / 2f, 0f)),
            Offset(l(b, w * 0.88f), l(h / 2f, h)), Offset(l(m, w * 0.60f), l(3f * h / 4f, h)),
        )
        for (q in listOf(left, right)) {
            val p = Path().apply {
                moveTo(q[0].x, q[0].y)
                for (k in 1..3) lineTo(q[k].x, q[k].y)
                close()
            }
            drawPath(p, color)
        }
    }
}

@Composable
fun SkipGlyph(next: Boolean, modifier: Modifier = Modifier, color: Color = White) {
    Canvas(modifier) {
        scale(if (next) 1f else -1f, 1f) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.78f, h / 2f)
                lineTo(0f, h)
                close()
            }
            drawPath(p, color)
            drawRect(color, Offset(w * 0.82f, 0f), Size(w * 0.16f, h))
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Barra de progreso recta con cuadrado como indicador. Tocar o arrastrar para adelantar. */
@Composable
fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val thumb by animateFloatAsState(if (dragFrac != null) 15f else 9f, tween(220, easing = EaseOut), label = "thumb")
    val frac = dragFrac ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { o ->
                        if (duration > 0) onSeek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFrac?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                            dragFrac = null
                        },
                        onDragCancel = { dragFrac = null },
                    ) { change, _ ->
                        dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
        ) {
            val y = size.height / 2f
            val w = size.width
            drawLine(Faint, Offset(0f, y), Offset(w, y), 1.dp.toPx())
            drawLine(White, Offset(0f, y), Offset(w * frac, y), 1.5.dp.toPx())
            val t = thumb.dp.toPx()
            drawRect(White, Offset((w * frac - t / 2f).coerceIn(0f, w - t), y - t / 2f), Size(t, t))
        }
        Row(Modifier.fillMaxWidth()) {
            T(formatTime((frac * duration).toLong()), 13.sp, Dim)
            Spacer(Modifier.weight(1f))
            T(formatTime(duration), 13.sp, Dim)
        }
    }
}
