package com.cogito.musica

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Una carpeta propia: nombre, foto y canciones en el orden elegido. */
data class Folder(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val cover: String? = null,
    val coverVersion: Long = 0L,
)

/** Guarda las carpetas en el teléfono (archivo carpetas.json y fotos en /portadas). */
class FolderStore(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "carpetas.json")
    private val coversDir = File(context.filesDir, "portadas").apply { mkdirs() }
    private val resolver = context.contentResolver

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1) // escrituras de a una, en orden

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders = _folders.asStateFlow()

    init {
        scope.launch(io) { _folders.value = read() }
    }

    fun coverFile(folder: Folder): File? = folder.cover?.let { File(coversDir, it) }

    fun create(name: String, songIds: List<Long> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        val clean = name.trim().ifEmpty { "Nueva carpeta" }
        update { it + Folder(id, clean, songIds.distinct()) }
        return id
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        edit(id) { it.copy(name = clean) }
    }

    fun delete(id: String) {
        _folders.value.find { it.id == id }?.let { f -> coverFile(f)?.delete() }
        update { list -> list.filterNot { it.id == id } }
    }

    fun addSongs(id: String, ids: List<Long>) = edit(id) { f ->
        f.copy(songIds = f.songIds + ids.filterNot { it in f.songIds }.distinct())
    }

    fun removeAt(id: String, index: Int) = edit(id) { f ->
        if (index !in f.songIds.indices) f
        else f.copy(songIds = f.songIds.toMutableList().apply { removeAt(index) })
    }

    fun move(id: String, from: Int, to: Int) = edit(id) { f ->
        if (from !in f.songIds.indices || to !in f.songIds.indices || from == to) f
        else f.copy(songIds = f.songIds.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Copia la foto elegida, achicada, dentro de la app. */
    suspend fun setCover(id: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bmp = runCatching { decodeScaled(uri, 900) }.getOrNull() ?: return@withContext false
        val name = "$id.jpg"
        runCatching {
            File(coversDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }.getOrElse { return@withContext false }
        bmp.recycle()
        edit(id) { it.copy(cover = name, coverVersion = System.currentTimeMillis()) }
        true
    }

    fun removeCover(id: String) {
        _folders.value.find { it.id == id }?.let { f -> coverFile(f)?.delete() }
        edit(id) { it.copy(cover = null, coverVersion = System.currentTimeMillis()) }
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val side = max(info.size.width, info.size.height)
                if (side > maxSide) {
                    val scale = maxSide.toFloat() / side
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }

    private fun edit(id: String, transform: (Folder) -> Folder) =
        update { list -> list.map { if (it.id == id) transform(it) else it } }

    private fun update(transform: (List<Folder>) -> List<Folder>) {
        _folders.update(transform)
        scope.launch(io) { write(_folders.value) }
    }

    private fun read(): List<Folder> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val songs = o.optJSONArray("songs")
            Folder(
                id = o.getString("id"),
                name = o.getString("name"),
                songIds = if (songs == null) emptyList() else (0 until songs.length()).map { songs.getLong(it) },
                cover = o.optString("cover").ifEmpty { null },
                coverVersion = o.optLong("v"),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(list: List<Folder>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("songs", JSONArray(f.songIds))
                put("cover", f.cover ?: "")
                put("v", f.coverVersion)
            })
        }
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }
}
