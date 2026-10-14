package com.cogito.musica

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** Lo que muestra el widget en cada momento. */
data class WidgetState(
    val title: String = "",
    val artist: String = "",
    val previous: String = "",
    val current: String = "",
    val next: String = "",
    val isPlaying: Boolean = false,
    val hasSong: Boolean = false,
)

/** Widget de pantalla de inicio: título, la línea de la letra que suena y controles. */
class LyricsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        LyricsWidget.push(context, LyricsWidget.last)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_TOGGLE || action == ACTION_NEXT || action == ACTION_PREV) {
            val pending = goAsync()
            val app = context.applicationContext
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val future = MediaController.Builder(app, token).buildAsync()
            future.addListener({
                runCatching {
                    val c = future.get()
                    when (action) {
                        ACTION_TOGGLE -> if (c.isPlaying) c.pause() else if (c.mediaItemCount > 0) {
                            if (c.playbackState == androidx.media3.common.Player.STATE_IDLE) c.prepare()
                            c.play()
                        } else openApp(app)
                        ACTION_NEXT -> c.seekToNext()
                        ACTION_PREV -> c.seekToPrevious()
                    }
                }
                // Se suelta un momento después para que el comando llegue
                android.os.Handler(app.mainLooper).postDelayed({
                    MediaController.releaseFuture(future)
                    pending.finish()
                }, 300)
            }, ContextCompat.getMainExecutor(app))
            return
        }
        super.onReceive(context, intent)
    }

    private fun openApp(context: Context) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { context.startActivity(it) } }
    }

    companion object {
        const val ACTION_TOGGLE = "com.cogito.musica.widget.TOGGLE"
        const val ACTION_NEXT = "com.cogito.musica.widget.NEXT"
        const val ACTION_PREV = "com.cogito.musica.widget.PREV"
    }
}

object LyricsWidget {

    @Volatile var last = WidgetState()
        private set

    /** Actualiza todos los widgets, solo si algo cambió. */
    fun update(context: Context, state: WidgetState) {
        if (state == last) return
        last = state
        push(context, state)
    }

    fun push(context: Context, state: WidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, LyricsWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = build(context, state)
        runCatching { manager.updateAppWidget(ids, views) }
    }

    private fun build(context: Context, s: WidgetState): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_lyrics)
        if (!s.hasSong) {
            v.setTextViewText(R.id.w_title, "Música")
            v.setTextViewText(R.id.w_artist, "Nada sonando")
            v.setTextViewText(R.id.w_prev, "")
            v.setTextViewText(R.id.w_current, "Tocá para abrir")
            v.setTextViewText(R.id.w_next, "")
        } else {
            v.setTextViewText(R.id.w_title, s.title)
            v.setTextViewText(R.id.w_artist, s.artist)
            v.setTextViewText(R.id.w_prev, s.previous)
            v.setTextViewText(R.id.w_current, s.current)
            v.setTextViewText(R.id.w_next, s.next)
        }
        v.setViewVisibility(R.id.w_prev, if (s.previous.isEmpty()) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.w_next, if (s.next.isEmpty()) View.GONE else View.VISIBLE)
        v.setImageViewResource(R.id.w_play, if (s.isPlaying) R.drawable.w_pause else R.drawable.w_play)

        v.setOnClickPendingIntent(R.id.w_play, action(context, LyricsWidgetProvider.ACTION_TOGGLE, 1))
        v.setOnClickPendingIntent(R.id.w_next_btn, action(context, LyricsWidgetProvider.ACTION_NEXT, 2))
        v.setOnClickPendingIntent(R.id.w_prev_btn, action(context, LyricsWidgetProvider.ACTION_PREV, 3))

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launch != null) {
            v.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        return v
    }

    private fun action(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, LyricsWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Arma el estado a partir de la letra y la posición actual. */
    fun stateFor(
        title: String, artist: String, lyrics: Lyrics?, positionMs: Long, durationMs: Long, playing: Boolean,
    ): WidgetState {
        if (lyrics == null || lyrics.lines.isEmpty()) {
            return WidgetState(title, artist, "", "Sin letra", "Agregala desde la app", playing, true)
        }
        val i = lyrics.indexAt(positionMs, durationMs)
        fun at(k: Int) = lyrics.lines.getOrNull(k)?.text ?: ""
        val cur = at(i).ifEmpty { "♪" }
        return WidgetState(title, artist, at(i - 1), cur, at(i + 1), playing, true)
    }
}
