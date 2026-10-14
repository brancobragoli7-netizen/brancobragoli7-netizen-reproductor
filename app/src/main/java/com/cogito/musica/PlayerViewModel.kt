package com.cogito.musica

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class NowPlaying(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
)

data class PlayerUi(
    val now: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()

    private val _ui = MutableStateFlow(PlayerUi())
    val ui = _ui.asStateFlow()

    /** Carpetas propias del usuario. */
    val folderStore = FolderStore(app, viewModelScope)

    /** Letra de la canción que suena (null si no tiene). */
    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics = _lyrics.asStateFlow()

    private var controller: MediaController? = null

    private val future = MediaController.Builder(
        app, SessionToken(app, ComponentName(app, PlaybackService::class.java))
    ).buildAsync()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    init {
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            sync()
        }, ContextCompat.getMainExecutor(app))

        // Carga la letra cuando cambia la canción o se edita
        viewModelScope.launch {
            combine(
                _ui.map { it.now?.id }.distinctUntilChanged(),
                LyricsRepository.version,
                _songs,
            ) { id, _, list -> id to list }.collectLatest { (id, list) ->
                _lyrics.value = null
                if (id == null) return@collectLatest
                val song = id.toLongOrNull()?.let { sid -> list.find { it.id == sid } }
                _lyrics.value = LyricsRepository.load(getApplication(), id, song?.uri)
            }
        }

        // Actualiza la barra de progreso mientras suena
        viewModelScope.launch {
            while (isActive) {
                delay(200)
                if (controller?.isPlaying == true) sync()
            }
        }
    }

    private fun sync() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val md = c.mediaMetadata
        _ui.value = PlayerUi(
            now = item?.let {
                NowPlaying(
                    id = it.mediaId,
                    title = md.title?.toString() ?: "Sin título",
                    artist = md.artist?.toString() ?: "Artista desconocido",
                    album = md.albumTitle?.toString() ?: "",
                )
            },
            isPlaying = c.isPlaying,
            position = c.currentPosition.coerceAtLeast(0L),
            duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
        )
    }

    fun loadSongs() {
        viewModelScope.launch {
            _songs.value = MusicRepository.load(getApplication())
            _loaded.value = true
        }
    }

    fun play(list: List<Song>, index: Int, shuffle: Boolean = false, cover: File? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        val art = cover?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
        c.setMediaItems(list.map { it.toMediaItem(art) }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun shuffleAll(list: List<Song>, cover: File? = null) {
        if (list.isEmpty()) return
        play(list, Random.nextInt(list.size), shuffle = true, cover = cover)
    }

    fun saveLyrics(text: String) {
        val id = _ui.value.now?.id ?: return
        LyricsRepository.save(getApplication(), id, text)
    }

    fun deleteLyrics() {
        val id = _ui.value.now?.id ?: return
        LyricsRepository.delete(getApplication(), id)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() { controller?.seekToNext(); sync() }
    fun previous() { controller?.seekToPrevious(); sync() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); sync() }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
    }
}
