package com.cogito.musica

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Comparte el id de la sesión de audio con la pantalla, para el visualizador. */
object AudioSessionHolder {
    val id = MutableStateFlow(0)
}

/** Servicio que reproduce en segundo plano y muestra los controles en la notificación. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lyricsJob: Job? = null
    private var currentLyrics: Lyrics? = null
    private var lyricsFor: String? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val sessionId = audioManager.generateAudioSessionId()
        player.setAudioSessionId(sessionId)
        AudioSessionHolder.id.value = sessionId
        AudioFx.attach(this, sessionId)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioSessionHolder.id.value = audioSessionId
                AudioFx.attach(this@PlaybackService, audioSessionId)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                refreshWidget(player)
            }
        })

        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .setCallback(SessionCallback())
            .setBitmapLoader(CacheBitmapLoader(ArtBitmapLoader(this)))
            .build()

        // Mientras suena, el widget sigue la letra línea por línea.
        scope.launch {
            while (isActive) {
                delay(400)
                session?.player?.let { if (it.isPlaying) refreshWidget(it) }
            }
        }
        scope.launch {
            LyricsRepository.version.collect {
                lyricsFor = null
                session?.player?.let { refreshWidget(it) }
            }
        }
    }

    private fun refreshWidget(player: Player) {
        val item = player.currentMediaItem
        if (item == null) {
            LyricsWidget.update(this, WidgetState())
            return
        }
        val id = item.mediaId
        if (id != lyricsFor) {
            lyricsFor = id
            currentLyrics = null
            lyricsJob?.cancel()
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
            lyricsJob = scope.launch {
                currentLyrics = LyricsRepository.load(this@PlaybackService, id, uri)
                session?.player?.let { pushWidget(it) }
            }
        }
        pushWidget(player)
    }

    private fun pushWidget(player: Player) {
        val md = player.mediaMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        LyricsWidget.update(
            this,
            LyricsWidget.stateFor(
                title = md.title?.toString() ?: "Sin título",
                artist = md.artist?.toString() ?: "",
                lyrics = currentLyrics,
                positionMs = player.currentPosition,
                durationMs = duration,
                playing = player.isPlaying,
            )
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        AudioFx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        AudioSessionHolder.id.value = 0
        LyricsWidget.update(this, WidgetState())
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        // Reconstruye la dirección del archivo de cada canción que llega desde la pantalla.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                if (uri != null) item.buildUpon().setUri(uri).build() else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
