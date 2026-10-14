package com.cogito.musica

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val uri: Uri,
) {
    /** Texto para buscar sin importar mayúsculas ni tildes. */
    val searchKey: String by lazy { normalize("$title $artist $album") }

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}

fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

object MusicRepository {

    /** Lee todas las canciones guardadas en el teléfono (sin internet). */
    suspend fun load(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
        )
        // Música de más de 30 segundos (deja afuera audios cortos y notas de voz)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf("30000")
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()
        try {
            context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    songs += Song(
                        id = id,
                        title = c.getString(titleCol).clean("Sin título"),
                        artist = c.getString(artistCol).clean("Artista desconocido"),
                        album = c.getString(albumCol).clean("Álbum desconocido"),
                        albumId = c.getLong(albumIdCol),
                        durationMs = c.getLong(durCol),
                        track = c.getInt(trackCol) % 1000,
                        uri = ContentUris.withAppendedId(collection, id),
                    )
                }
            }
        } catch (_: SecurityException) {
            // Sin permiso todavía: lista vacía
        }
        songs
    }

    private fun String?.clean(fallback: String): String =
        if (this.isNullOrBlank() || this == "<unknown>") fallback else this.trim()
}
