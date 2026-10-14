package com.cogito.musica.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cogito.musica.AudioViz
import com.cogito.musica.Folder
import com.cogito.musica.FolderStore
import com.cogito.musica.PlayerUi
import com.cogito.musica.Song
import com.cogito.musica.normalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ───────────────────────── Foto de carpeta ─────────────────────────

@Composable
private fun rememberCover(file: File?, version: Long, sizePx: Int): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, file?.path, version, sizePx) {
        value = if (file == null || !file.exists()) null else withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bmp
}

/** Cuadrado con la foto de la carpeta, o la inicial en cursiva si no tiene. */
@Composable
fun FolderCover(folder: Folder, store: FolderStore, size: Dp, modifier: Modifier = Modifier) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bmp = rememberCover(store.coverFile(folder), folder.coverVersion, px)
    Box(
        modifier.size(size).border(1.dp, Faint).background(Black),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, folder.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            T(
                folder.name.trim().take(1).uppercase(),
                (size.value * 0.45f).sp, Dim, italic = true,
            )
        }
    }
}

// ───────────────────────── Lista de carpetas ─────────────────────────

@Composable
fun FolderList(
    folders: List<Folder>,
    store: FolderStore,
    query: String,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val q = normalize(query.trim())
    val shown = if (q.isEmpty()) folders else folders.filter { q in normalize(it.name) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Column(Modifier.padding(vertical = 16.dp)) {
                RectButton("+ Nueva carpeta", filled = true, onClick = onCreate)
            }
        }
        if (folders.isEmpty()) {
            item {
                T(
                    "Todavía no tenés carpetas.\nCreá una y elegí sus canciones, su orden, su nombre y su foto.\n\n" +
                        "También podés mantener apretada cualquier canción para sumarla a una carpeta.",
                    16.sp, Dim, italic = true, maxLines = 8,
                )
            }
        }
        items(shown, key = { it.id }) { f ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(f.id) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolderCover(f, store, 60.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    T(f.name, 20.sp, italic = true)
                    T(countLabel(f.songIds.size), 13.sp, Dim)
                }
            }
            Hairline()
        }
    }
}

fun countLabel(n: Int) = "$n " + if (n == 1) "canción" else "canciones"

// ───────────────────────── Detalle de carpeta ─────────────────────────

@Composable
fun FolderDetail(
    folder: Folder,
    store: FolderStore,
    allSongs: Map<Long, Song>,
    ui: PlayerUi,
    viz: AudioViz,
    frame: State<Long>,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onAddSongs: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(folder.id) { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }

    // Canciones en el orden guardado; si un archivo ya no existe, se saltea.
    val entries = remember(folder.songIds, allSongs) {
        folder.songIds.mapIndexedNotNull { i, id -> allSongs[id]?.let { i to it } }
    }
    val playable = entries.map { it.second }
    val totalMs = playable.sumOf { it.durationMs }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { store.setCover(folder.id, uri) }
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LazyColumn(Modifier.fillMaxSize().background(Black).padding(horizontal = 24.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("← Volver", Modifier.clickable(onClick = onBack).padding(vertical = 8.dp), Dim)
                    Spacer(Modifier.weight(1f))
                    Label(
                        if (editing) "Listo" else "Editar",
                        Modifier.clickable { editing = !editing }.padding(vertical = 8.dp),
                        if (editing) White else Dim,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    FolderCover(
                        folder, store, 128.dp,
                        Modifier.clickable { if (folder.cover == null) pickPhoto() else photoMenu = true },
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        T(folder.name, 28.sp, italic = true, maxLines = 3, modifier = Modifier.clickable { renaming = true })
                        Spacer(Modifier.height(6.dp))
                        T(countLabel(playable.size), 13.sp, Dim)
                        if (totalMs > 0) T(formatLong(totalMs), 13.sp, Faint)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Label("Renombrar", Modifier.clickable { renaming = true }.padding(vertical = 6.dp), Dim)
                    Label(
                        if (folder.cover == null) "Poner foto" else "Foto",
                        Modifier.clickable { if (folder.cover == null) pickPhoto() else photoMenu = true }
                            .padding(vertical = 6.dp),
                        Dim,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (playable.isNotEmpty()) {
                        RectButton("Reproducir", filled = true) { onPlay(playable, 0) }
                        RectButton("Aleatorio") { onShuffle(playable) }
                    }
                    RectButton("+ Agregar", onClick = onAddSongs)
                }
                Spacer(Modifier.height(12.dp))
                if (editing && playable.isNotEmpty()) {
                    T("Usá las flechas para cambiar el orden de reproducción.", 13.sp, Faint, italic = true, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                }
                Hairline()
            }
        }

        if (playable.isEmpty()) {
            item {
                T(
                    "Carpeta vacía. Tocá «+ Agregar» para elegir canciones.",
                    16.sp, Dim, italic = true, maxLines = 3, modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }

        itemsIndexed(entries, key = { _, e -> "${e.first}-${e.second.id}" }) { pos, (storedIndex, song) ->
            val current = ui.now?.id == song.id.toString()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !editing) { onPlay(playable, pos) }
                    .padding(vertical = if (editing) 6.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(38.dp)) {
                    if (current && !editing) MiniBars(viz, frame, Modifier.size(16.dp, 14.dp))
                    else T("%02d".format(pos + 1), 13.sp, Faint)
                }
                Column(Modifier.weight(1f)) {
                    T(song.title, 18.sp, italic = current)
                    T(song.artist, 13.sp, Dim)
                }
                if (editing) {
                    IconBox(enabled = pos > 0, onClick = {
                        store.move(folder.id, storedIndex, entries[pos - 1].first)
                    }) { ArrowGlyph(up = true, color = it) }
                    IconBox(enabled = pos < entries.lastIndex, onClick = {
                        store.move(folder.id, storedIndex, entries[pos + 1].first)
                    }) { ArrowGlyph(up = false, color = it) }
                    IconBox(onClick = { store.removeAt(folder.id, storedIndex) }) { CrossGlyph(color = it) }
                } else {
                    T(formatTime(song.durationMs), 13.sp, Dim, modifier = Modifier.padding(start = 12.dp))
                }
            }
            Hairline()
        }

        if (editing) {
            item {
                Column(Modifier.padding(vertical = 28.dp)) {
                    RectButton("Eliminar carpeta") { confirmDelete = true }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (renaming) {
        NameDialog(
            title = "Nombre de la carpeta",
            initial = folder.name,
            confirm = "Guardar",
            onConfirm = { store.rename(folder.id, it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (photoMenu) {
        ChoiceDialog(
            title = "Foto de la carpeta",
            options = listOf("Cambiar foto", "Quitar foto"),
            onPick = { i ->
                photoMenu = false
                if (i == 0) pickPhoto() else store.removeCover(folder.id)
            },
            onDismiss = { photoMenu = false },
        )
    }
    if (confirmDelete) {
        ChoiceDialog(
            title = "¿Eliminar «${folder.name}»?",
            message = "Las canciones no se borran del teléfono, solo la carpeta.",
            options = listOf("Eliminar", "Cancelar"),
            onPick = { i ->
                confirmDelete = false
                if (i == 0) { store.delete(folder.id); onBack() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun formatLong(ms: Long): String {
    val min = ms / 60000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

@Composable
private fun IconBox(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
) {
    Box(
        Modifier.size(44.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(12.dp)) { content(if (enabled) White else Faint.copy(alpha = 0.12f)) }
    }
}

@Composable
private fun ArrowGlyph(up: Boolean, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (up) { moveTo(0f, h * 0.85f); lineTo(w / 2f, h * 0.15f); lineTo(w, h * 0.85f) }
            else { moveTo(0f, h * 0.15f); lineTo(w / 2f, h * 0.85f); lineTo(w, h * 0.15f) }
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun CrossGlyph(color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.fillMaxSize()) {
        val s = 1.5.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), s)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), s)
    }
}

// ───────────────────────── Elegir canciones ─────────────────────────

@Composable
fun SongPicker(
    folder: Folder,
    songs: List<Song>,
    onDone: (List<Long>) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var query by rememberSaveable { mutableStateOf("") }
    // Se guarda en el orden en que las tocás: ese será su orden en la carpeta.
    var picked by remember { mutableStateOf(listOf<Long>()) }
    val already = remember(folder.songIds) { folder.songIds.toHashSet() }
    val q = normalize(query.trim())
    val shown = remember(songs, q) { if (q.isEmpty()) songs else songs.filter { q in it.searchKey } }

    Column(Modifier.fillMaxSize().background(Black)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Cancelar", Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp), Dim)
                Spacer(Modifier.weight(1f))
                Label("${picked.size} elegidas", color = Faint)
            }
            Spacer(Modifier.height(8.dp))
            T("Agregar a «${folder.name}»", 26.sp, italic = true, maxLines = 2)
            Spacer(Modifier.height(12.dp))
            PlainField(query, "Buscar", onChange = { query = it })
            Spacer(Modifier.height(8.dp))
            Hairline()
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            items(shown, key = { it.id }) { song ->
                val inFolder = song.id in already
                val order = picked.indexOf(song.id)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !inFolder) {
                            picked = if (order >= 0) picked - song.id else picked + song.id
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp)
                            .border(1.dp, if (inFolder) Faint else White)
                            .background(if (order >= 0) White else Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (order >= 0) T("${order + 1}", 11.sp, Black)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        T(song.title, 17.sp, if (inFolder) Faint else White)
                        T(if (inFolder) "Ya está en la carpeta" else "${song.artist} — ${song.album}", 13.sp, if (inFolder) Faint else Dim)
                    }
                }
                Hairline()
            }
        }
        Hairline(color = White)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RectButton(
                if (picked.isEmpty()) "Elegí canciones" else "Agregar ${picked.size}",
                filled = picked.isNotEmpty(),
            ) { if (picked.isNotEmpty()) onDone(picked) }
        }
    }
}

// ───────────────────────── Diálogos ─────────────────────────

@Composable
private fun Panel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(Black)
                .border(1.dp, White)
                .padding(22.dp)
        ) { content() }
    }
}

@Composable
fun PlainField(value: String, placeholder: String, modifier: Modifier = Modifier, focus: FocusRequester? = null, onDone: (() -> Unit)? = null, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = White, fontSize = 17.sp, fontFamily = Serif),
        cursorBrush = SolidColor(White),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier
            .fillMaxWidth()
            .let { if (focus != null) it.focusRequester(focus) else it }
            .border(1.dp, Faint)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) T(placeholder, 17.sp, Faint, italic = true)
                inner()
            }
        },
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Panel(onDismiss) {
        Column {
            T(title, 22.sp, italic = true, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            PlainField(text, "Nombre", focus = focus, onDone = { if (text.isNotBlank()) onConfirm(text) }) { text = it }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RectButton(confirm, filled = true) { if (text.isNotBlank()) onConfirm(text) }
                RectButton("Cancelar", onClick = onDismiss)
            }
        }
    }
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    Panel(onDismiss) {
        Column {
            T(title, 22.sp, italic = true, maxLines = 3)
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                T(message, 14.sp, Dim, maxLines = 4)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(options) { i, o ->
                    T(o, 18.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 12.dp))
                    if (i < options.lastIndex) Hairline()
                }
            }
        }
    }
}
