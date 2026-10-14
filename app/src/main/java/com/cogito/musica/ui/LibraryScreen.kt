package com.cogito.musica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cogito.musica.AudioViz
import com.cogito.musica.Folder
import com.cogito.musica.FolderStore
import com.cogito.musica.PlayerUi
import com.cogito.musica.Song
import com.cogito.musica.normalize

private enum class Tab(val label: String) { SONGS("Canciones"), FOLDERS("Carpetas"), ALBUMS("Álbumes"), ARTISTS("Artistas") }

private data class Group(val title: String, val subtitle: String, val songs: List<Song>)

@Composable
fun LibraryScreen(
    songs: List<Song>,
    loaded: Boolean,
    folders: List<Folder>,
    store: FolderStore,
    ui: PlayerUi,
    viz: AudioViz,
    frame: State<Long>,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.SONGS) }
    var query by rememberSaveable { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<Group?>(null) }

    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerFor by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var addToFolder by remember { mutableStateOf<Song?>(null) }
    var newFolderFor by remember { mutableStateOf<Song?>(null) }

    BackHandler(enabled = openGroup != null) { openGroup = null }

    val byId = remember(songs) { songs.associateBy { it.id } }
    val openFolder = folders.find { it.id == openFolderId }
    val pickerFolder = folders.find { it.id == pickerFor }

    // Elegir canciones para una carpeta: ocupa toda la pantalla
    if (pickerFolder != null) {
        SongPicker(
            folder = pickerFolder,
            songs = songs,
            onDone = { ids -> store.addSongs(pickerFolder.id, ids); pickerFor = null },
            onCancel = { pickerFor = null },
        )
        return
    }

    val q = normalize(query.trim())
    val filtered = remember(songs, q) { if (q.isEmpty()) songs else songs.filter { q in it.searchKey } }
    val albums = remember(filtered) {
        filtered.groupBy { it.albumId }.values.map { list ->
            val sorted = list.sortedWith(compareBy<Song>({ it.track }, { it.title.lowercase() }))
            Group(sorted.first().album, sorted.first().artist, sorted)
        }.sortedBy { it.title.lowercase() }
    }
    val artists = remember(filtered) {
        filtered.groupBy { it.artist }.map { (name, list) ->
            Group(name, "", list.sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.track })))
        }.sortedBy { it.title.lowercase() }
    }

    Column(Modifier.fillMaxSize().background(Black)) {
      if (openFolder != null) {
        Box(Modifier.weight(1f)) {
            FolderDetail(
                folder = openFolder,
                store = store,
                allSongs = byId,
                ui = ui,
                viz = viz,
                frame = frame,
                onBack = { openFolderId = null },
                onPlay = onPlay,
                onShuffle = onShuffle,
                onAddSongs = { pickerFor = openFolder.id },
            )
        }
      } else {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                T("Música", 44.sp, italic = true)
                Spacer(Modifier.weight(1f))
                if (loaded) T("${songs.size} canciones", 13.sp, Dim, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            SearchField(query) { query = it; openGroup = null }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Tab.entries.forEach { t ->
                    TextTab(t.label, active = tab == t) { tab = t; openGroup = null }
                }
            }
            Hairline()
        }

        val listState = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            when {
                !loaded -> CenterNote("Buscando música…")
                songs.isEmpty() -> CenterNote(
                    "No encontré música guardada en este teléfono.\n\n" +
                        "Copiá archivos MP3, FLAC, M4A u OGG a la carpeta Música o Descargas."
                )
                openGroup != null -> {
                    val g = openGroup!!
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        item {
                            Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                                Label("← Volver", Modifier.clickable { openGroup = null }.padding(vertical = 6.dp), Dim)
                                Spacer(Modifier.height(10.dp))
                                T(g.title, 30.sp, italic = true, maxLines = 2)
                                if (g.subtitle.isNotEmpty()) T(g.subtitle, 15.sp, Dim)
                                Spacer(Modifier.height(16.dp))
                                PlayRow(onPlay = { onPlay(g.songs, 0) }, onShuffle = { onShuffle(g.songs) })
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        songItems(g.songs, ui, viz, frame, onPlay) { addToFolder = it }
                    }
                }
                tab == Tab.SONGS -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), state = listState) {
                    item {
                        Column(Modifier.padding(vertical = 16.dp)) {
                            PlayRow(onPlay = { onPlay(filtered, 0) }, onShuffle = { onShuffle(filtered) })
                        }
                    }
                    if (filtered.isEmpty()) item { T("Sin resultados.", 16.sp, Dim, italic = true) }
                    songItems(filtered, ui, viz, frame, onPlay) { addToFolder = it }
                }
                tab == Tab.FOLDERS -> FolderList(
                    folders = folders,
                    store = store,
                    query = query,
                    onOpen = { openFolderId = it },
                    onCreate = { creating = true },
                )
                else -> {
                    val groups = if (tab == Tab.ALBUMS) albums else artists
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        itemsIndexed(groups) { _, g ->
                            Column(
                                Modifier.fillMaxWidth().clickable { openGroup = g }.padding(vertical = 14.dp)
                            ) {
                                T(g.title, 20.sp, italic = true)
                                val count = "${g.songs.size} " + if (g.songs.size == 1) "canción" else "canciones"
                                T(if (g.subtitle.isNotEmpty()) "${g.subtitle} — $count" else count, 13.sp, Dim)
                            }
                            Hairline()
                        }
                    }
                }
            }
        }

      }

        if (ui.now != null) {
            MiniPlayer(ui, viz, frame, onToggle, onNext, onExpand)
        }
    }

    if (creating) {
        NameDialog(
            title = "Nueva carpeta",
            initial = "",
            confirm = "Crear",
            onConfirm = { name ->
                creating = false
                openFolderId = store.create(name)
            },
            onDismiss = { creating = false },
        )
    }
    addToFolder?.let { song ->
        ChoiceDialog(
            title = "Agregar «${song.title}» a…",
            options = listOf("+ Nueva carpeta") + folders.map { f ->
                if (song.id in f.songIds) "${f.name}  (ya está)" else f.name
            },
            onPick = { i ->
                addToFolder = null
                if (i == 0) newFolderFor = song
                else store.addSongs(folders[i - 1].id, listOf(song.id))
            },
            onDismiss = { addToFolder = null },
        )
    }
    newFolderFor?.let { song ->
        NameDialog(
            title = "Nueva carpeta",
            initial = "",
            confirm = "Crear",
            onConfirm = { name -> store.create(name, listOf(song.id)); newFolderFor = null },
            onDismiss = { newFolderFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.songItems(
    list: List<Song>,
    ui: PlayerUi,
    viz: AudioViz,
    frame: State<Long>,
    onPlay: (List<Song>, Int) -> Unit,
    onLongPress: (Song) -> Unit,
) {
    itemsIndexed(list, key = { _, s -> s.id }) { i, song ->
        val current = ui.now?.id == song.id.toString()
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onLongClick = { onLongPress(song) }) { onPlay(list, i) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(38.dp)) {
                if (current) MiniBars(viz, frame, Modifier.size(16.dp, 14.dp))
                else T("%02d".format(i + 1), 13.sp, Faint)
            }
            Column(Modifier.weight(1f)) {
                T(song.title, 18.sp, italic = current)
                T("${song.artist} — ${song.album}", 13.sp, Dim)
            }
            T(formatTime(song.durationMs), 13.sp, Dim, modifier = Modifier.padding(start = 12.dp))
        }
        Hairline()
    }
}

@Composable
private fun PlayRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RectButton("Reproducir", filled = true, onClick = onPlay)
        RectButton("Aleatorio", onClick = onShuffle)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = White, fontSize = 16.sp, fontFamily = Serif),
        cursorBrush = SolidColor(White),
        modifier = Modifier.fillMaxWidth().border(1.dp, Faint).padding(horizontal = 14.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) T("Buscar canción, artista o álbum", 16.sp, Faint, italic = true)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Label("Borrar", Modifier.clickable { onChange("") }.padding(start = 12.dp), Dim)
                }
            }
        },
    )
}

@Composable
private fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        T(text, 17.sp, Dim, italic = true, maxLines = 8, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun MiniPlayer(
    ui: PlayerUi,
    viz: AudioViz,
    frame: State<Long>,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
) {
    val now = ui.now ?: return
    val frac = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().background(Black).clickable(onClick = onExpand)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Faint)) {
            Box(Modifier.fillMaxWidth(frac).height(1.dp).background(White))
        }
        Row(
            Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniBars(viz, frame, Modifier.size(22.dp, 18.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                T(now.title, 16.sp, italic = true)
                T(now.artist, 12.sp, Dim)
            }
            Box(Modifier.size(48.dp).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                PlayPauseGlyph(ui.isPlaying, Modifier.size(16.dp))
            }
            Box(Modifier.size(48.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
                SkipGlyph(true, Modifier.size(14.dp))
            }
        }
    }
}
