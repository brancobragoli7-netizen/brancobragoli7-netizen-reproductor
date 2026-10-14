package com.cogito.musica.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min

/*
 * Movimiento de la app: rápido, sin rebotes ni brillos.
 * Todo entra con una curva que frena al final, como una página que se acomoda.
 */

val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/**
 * Reemplazo de `clickable`: al apretar, el elemento se achica apenas
 * (unos pocos píxeles, sin importar su tamaño) y se ilumina un tono.
 */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressVisual(source, enabled)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Igual que [tap], con además una acción al mantener apretado. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressFeedback(onLongClick: () -> Unit, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressVisual(source, true)
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

private fun Modifier.pressVisual(source: MutableInteractionSource, enabled: Boolean): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(if (pressed) 90 else 320, easing = EaseOut),
        label = "tap",
    )
    this
        .graphicsLayer {
            if (p > 0f && size.width > 0f) {
                val k = min(0.05f, 8.dp.toPx() / size.width) * p
                scaleX = 1f - k
                scaleY = 1f - k
            }
        }
        .drawWithContent {
            drawContent()
            if (p > 0f) drawRect(Color.White.copy(alpha = 0.07f * p))
        }
}

/** Entrada escalonada: aparece desde abajo, de a poco. */
fun Modifier.enter(delayMs: Int = 0, offsetDp: Float = 14f, durationMs: Int = 560): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        a.animateTo(1f, tween(durationMs, easing = EaseOut))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * offsetDp.dp.toPx()
    }
}

/** Borde que se dibuja solo, recorriendo el rectángulo en sentido horario. */
fun Modifier.drawnBorder(color: Color = White, durationMs: Int = 520): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs, easing = EaseInOut)) }
    drawWithContent {
        drawContent()
        val sw = 1.dp.toPx()
        val i = sw / 2f
        val w = size.width - sw
        val h = size.height - sw
        val corners = listOf(
            Offset(i, i), Offset(i + w, i), Offset(i + w, i + h), Offset(i, i + h), Offset(i, i),
        )
        var left = 2f * (w + h) * a.value
        for (k in 0 until 4) {
            if (left <= 0f) break
            val s = corners[k]
            val e = corners[k + 1]
            val len = if (k % 2 == 0) w else h
            val d = min(left, len)
            drawLine(color, s, s + (e - s) * (d / len), sw)
            left -= d
        }
    }
}

/** Recuerda el último valor no nulo, para que una ventana pueda animar su salida. */
@Composable
fun <T> rememberLast(value: T?): T? {
    val box = remember { arrayOfNulls<Any?>(1) }
    if (value != null) box[0] = value
    @Suppress("UNCHECKED_CAST")
    return box[0] as T?
}

// ── Transiciones de ventanas ──

/** Pantalla que entra desde la derecha (abrir), o vuelve desde la izquierda (cerrar). */
fun <S> AnimatedContentTransitionScope<S>.pageTransition(forward: Boolean): ContentTransform {
    val enter = slideInHorizontally(tween(420, easing = EaseOut)) { w -> if (forward) w / 4 else -w / 4 } +
        fadeIn(tween(320, delayMillis = 60))
    val exit = slideOutHorizontally(tween(300, easing = EaseInOut)) { w -> if (forward) -w / 8 else w / 8 } +
        fadeOut(tween(180))
    return enter togetherWith exit
}

/** Cambio de contenido en el lugar: lo nuevo sube un poco mientras lo viejo se desvanece. */
fun <S> AnimatedContentTransitionScope<S>.riseTransition(): ContentTransform =
    (slideInVertically(tween(380, easing = EaseOut)) { h -> h / 14 } + fadeIn(tween(300, delayMillis = 40)))
        .togetherWith(fadeOut(tween(140)))
