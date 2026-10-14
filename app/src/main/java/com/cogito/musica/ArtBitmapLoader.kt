package com.cogito.musica

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/** Dirección especial que pide una portada generada: musica://art?t=título&a=artista */
fun artUri(title: String, artist: String): Uri = Uri.Builder()
    .scheme("musica").authority("art")
    .appendQueryParameter("t", title)
    .appendQueryParameter("a", artist)
    .build()

/**
 * Entrega las portadas que usan la notificación y la pantalla de bloqueo.
 * Para canciones sin foto dibuja una portada negra con el título en blanco, en la
 * misma tipografía de la app. Android arma los colores de esos controles a partir de
 * la portada, así que una portada negra deja todo en negro y blanco.
 */
@OptIn(UnstableApi::class)
class ArtBitmapLoader(private val context: Context) : BitmapLoader {

    private val delegate = DataSourceBitmapLoader(context)
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (uri.scheme == "musica") {
            executor.submit<Bitmap> {
                ArtRenderer.render(
                    context,
                    uri.getQueryParameter("t") ?: "",
                    uri.getQueryParameter("a") ?: "",
                )
            }
        } else {
            delegate.loadBitmap(uri)
        }
}

object ArtRenderer {
    private var regular: Typeface? = null
    private var italic: Typeface? = null

    fun render(context: Context, title: String, artist: String, size: Int = 720): Bitmap {
        if (regular == null) {
            regular = runCatching { ResourcesCompat.getFont(context, R.font.serif_regular) }.getOrNull() ?: Typeface.SERIF
            italic = runCatching { ResourcesCompat.getFont(context, R.font.serif_italic) }.getOrNull()
                ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)

        val m = size * 0.08f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = size / 360f
        }
        c.drawRect(m / 2, m / 2, size - m / 2, size - m / 2, line)

        // Etiqueta superior
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            typeface = regular
            textSize = size * 0.032f
            letterSpacing = 0.25f
        }
        c.drawText("MÚSICA", m, m + label.textSize, label)

        // Cinco barras, como el ícono
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val heights = floatArrayOf(0.35f, 0.75f, 1f, 0.6f, 0.45f)
        val bw = size * 0.018f
        val gap = size * 0.014f
        val maxH = size * 0.12f
        val right = size - m
        val baseY = m + maxH
        heights.forEachIndexed { i, h ->
            val x = right - (heights.size - i) * (bw + gap) + gap
            c.drawRect(x, baseY - maxH * h, x + bw, baseY, bar)
        }

        // Título y artista abajo
        val width = (size - 2 * m).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = italic
            textSize = size * 0.095f
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            typeface = regular
            textSize = size * 0.045f
        }
        val titleLayout = layout(title.ifBlank { "Sin título" }, titlePaint, width, 3)
        val artistLayout = layout(artist, artistPaint, width, 1)
        val gapY = size * 0.025f
        val bottom = size - m
        val artistTop = bottom - artistLayout.height
        val titleTop = artistTop - gapY - titleLayout.height

        c.save(); c.translate(m, titleTop); titleLayout.draw(c); c.restore()
        c.save(); c.translate(m, artistTop); artistLayout.draw(c); c.restore()
        return bmp
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
}
