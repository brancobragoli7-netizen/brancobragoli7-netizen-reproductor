package com.cogito.musica

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val fromUser: Boolean) {

    /** Línea que corresponde al momento actual de la canción. */
    fun indexAt(positionMs: Long, durationMs: Long): Int {
        if (lines.isEmpty()) return -1
        return if (synced) {
            var idx = 0
            for (i in lines.indices) {
                val t = lines[i].timeMs ?: continue
                if (t <= positionMs + 250) idx = i else break
            }
            idx
        } else {
            // Sin tiempos: se reparte la letra a lo largo de la canción
            if (durationMs <= 0) 0
            else ((positionMs.toDouble() / durationMs) * lines.size).toInt().coerceIn(0, lines.lastIndex)
        }
    }
}

/**
 * Busca la letra de cada canción, sin internet:
 *  1. la que agregaste vos desde la app (archivo .lrc o texto pegado),
 *  2. la que viene guardada dentro del archivo (MP3 con etiqueta USLT, FLAC con LYRICS).
 */
object LyricsRepository {

    private val cache = HashMap<String, Lyrics?>()

    /** Cambia cada vez que se guarda o borra una letra, para que todo se actualice. */
    private val _version = MutableStateFlow(0)
    val version = _version.asStateFlow()

    private fun dir(context: Context) = File(context.filesDir, "letras").apply { mkdirs() }
    private fun userFile(context: Context, songId: String) = File(dir(context), "$songId.lrc")

    suspend fun load(context: Context, songId: String, uri: Uri?): Lyrics? = withContext(Dispatchers.IO) {
        synchronized(cache) { if (cache.containsKey(songId)) return@withContext cache[songId] }
        val user = userFile(context, songId).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
        val result = when {
            !user.isNullOrBlank() -> parse(user, fromUser = true)
            uri != null -> runCatching { readEmbedded(context, uri) }.getOrNull()?.let { parse(it, fromUser = false) }
            else -> null
        }
        synchronized(cache) { cache[songId] = result }
        result
    }

    fun save(context: Context, songId: String, text: String) {
        userFile(context, songId).writeText(text)
        synchronized(cache) { cache.remove(songId) }
        _version.value++
    }

    fun delete(context: Context, songId: String) {
        userFile(context, songId).delete()
        synchronized(cache) { cache.remove(songId) }
        _version.value++
    }

    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metaTag = Regex("""^\[[a-zA-Z]+:.*]$""")

    fun parse(raw: String, fromUser: Boolean): Lyrics? {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return null
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        for (line in text.lines()) {
            val tags = timeTag.findAll(line).toList()
            if (tags.isNotEmpty()) {
                val words = line.replace(timeTag, "").trim()
                for (t in tags) {
                    val min = t.groupValues[1].toLong()
                    val sec = t.groupValues[2].toLong()
                    val fracStr = t.groupValues[3]
                    val frac = when (fracStr.length) {
                        0 -> 0L
                        1 -> fracStr.toLong() * 100
                        2 -> fracStr.toLong() * 10
                        else -> fracStr.take(3).toLong()
                    }
                    timed += LyricLine(min * 60_000 + sec * 1000 + frac, words)
                }
            } else if (!metaTag.matches(line.trim())) {
                plain += LyricLine(null, line.trim())
            }
        }
        return if (timed.size >= 2) {
            Lyrics(timed.sortedBy { it.timeMs }, synced = true, fromUser = fromUser)
        } else {
            val lines = plain.dropWhile { it.text.isEmpty() }.dropLastWhile { it.text.isEmpty() }
            if (lines.isEmpty()) null else Lyrics(lines, synced = false, fromUser = fromUser)
        }
    }

    // ── Lectura de la letra guardada dentro del archivo ──

    private fun readEmbedded(context: Context, uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { raw ->
            val input = DataInputStream(raw.buffered(64 * 1024))
            val head = ByteArray(4)
            input.readFully(head)
            when {
                head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() ->
                    readId3Uslt(input, head[3].toInt())
                String(head, Charsets.ISO_8859_1) == "fLaC" -> readFlacLyrics(input)
                else -> null
            }
        }

    private fun readId3Uslt(input: DataInputStream, major: Int): String? {
        if (major !in 3..4) return null
        input.readUnsignedByte() // revisión
        val flags = input.readUnsignedByte()
        val tagSize = syncsafe(input.readInt())
        var read = 0
        if (flags and 0x40 != 0) { // encabezado extendido
            val ext = if (major == 4) syncsafe(input.readInt()) - 4 else input.readInt()
            skip(input, ext.toLong()); read += ext + 4
        }
        while (read + 10 <= tagSize) {
            val idBytes = ByteArray(4)
            input.readFully(idBytes)
            if (idBytes[0].toInt() == 0) return null // relleno: no hay más marcos
            val id = String(idBytes, Charsets.ISO_8859_1)
            val raw = input.readInt()
            val size = if (major == 4) syncsafe(raw) else raw
            input.readUnsignedShort() // flags del marco
            read += 10
            if (size <= 0 || read + size > tagSize) return null
            if (id == "USLT" && size < 512 * 1024) {
                val data = ByteArray(size)
                input.readFully(data)
                return decodeUslt(data)
            }
            skip(input, size.toLong())
            read += size
        }
        return null
    }

    private fun decodeUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val enc = data[0].toInt()
        val charset: Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        // [codificación][idioma x3][descripción + terminador][letra]
        var i = 4
        if (enc == 1 || enc == 2) {
            while (i + 1 < data.size && !(data[i].toInt() == 0 && data[i + 1].toInt() == 0)) i += 2
            i += 2
            // la letra en UTF-16 trae su propio BOM
        } else {
            while (i < data.size && data[i].toInt() != 0) i++
            i += 1
        }
        if (i >= data.size) return null
        return String(data, i, data.size - i, charset).trim('\u0000', ' ', '\n').ifBlank { null }
    }

    private fun readFlacLyrics(input: DataInputStream): String? {
        while (true) {
            val header = input.readUnsignedByte()
            val last = header and 0x80 != 0
            val type = header and 0x7F
            val len = (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
            if (type == 4 && len < 2 * 1024 * 1024) {
                val block = ByteArray(len)
                input.readFully(block)
                var p = 0
                fun le32(): Int {
                    val v = (block[p].toInt() and 0xFF) or ((block[p + 1].toInt() and 0xFF) shl 8) or
                        ((block[p + 2].toInt() and 0xFF) shl 16) or ((block[p + 3].toInt() and 0xFF) shl 24)
                    p += 4
                    return v
                }
                val vendor = le32(); p += vendor
                val count = le32()
                repeat(count) {
                    if (p + 4 > block.size) return null
                    val l = le32()
                    if (l < 0 || p + l > block.size) return null
                    val entry = String(block, p, l, Charsets.UTF_8)
                    p += l
                    val eq = entry.indexOf('=')
                    if (eq > 0) {
                        val key = entry.substring(0, eq).uppercase()
                        if (key == "LYRICS" || key == "UNSYNCEDLYRICS") return entry.substring(eq + 1).ifBlank { null }
                    }
                }
                return null
            }
            skip(input, len.toLong())
            if (last) return null
        }
    }

    private fun syncsafe(v: Int): Int =
        (v and 0x7F) or ((v shr 8 and 0x7F) shl 7) or ((v shr 16 and 0x7F) shl 14) or ((v shr 24 and 0x7F) shl 21)

    private fun skip(input: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val s = input.skip(left)
            if (s <= 0) { if (input.read() < 0) return; left-- } else left -= s
        }
    }
}
