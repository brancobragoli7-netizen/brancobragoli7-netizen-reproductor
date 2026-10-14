package com.cogito.musica

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cogito.musica.ui.Black
import com.cogito.musica.ui.Dim
import com.cogito.musica.ui.Faint
import com.cogito.musica.ui.LibraryScreen
import com.cogito.musica.ui.PlayerScreen
import com.cogito.musica.ui.RectButton
import com.cogito.musica.ui.T
import com.cogito.musica.ui.rememberVizFrame

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { App() }
    }
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun App() {
    val vm: PlayerViewModel = viewModel()
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAudio by remember { mutableStateOf(ctx.has(audioPermission)) }
    var hasMic by remember { mutableStateOf(ctx.has(Manifest.permission.RECORD_AUDIO)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var started by remember { mutableStateOf(true) }

    // Revisa permisos al volver a la app (por ejemplo, desde Ajustes) y pausa
    // el visualizador cuando la app no está a la vista.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                Lifecycle.Event.ON_RESUME -> {
                    hasAudio = ctx.has(audioPermission)
                    hasMic = ctx.has(Manifest.permission.RECORD_AUDIO)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        askedOnce = true
        hasAudio = ctx.has(audioPermission)
        hasMic = ctx.has(Manifest.permission.RECORD_AUDIO)
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasMic = it }

    LaunchedEffect(hasAudio) { if (hasAudio) vm.loadSongs() }

    val songs by vm.songs.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val ui by vm.ui.collectAsState()
    val sessionId by AudioSessionHolder.id.collectAsState()

    val viz = remember { AudioViz() }
    val frame = rememberVizFrame(viz)

    DisposableEffect(sessionId, hasMic, started, ui.isPlaying) {
        if (hasMic && started && sessionId > 0) viz.attach(sessionId)
        onDispose { viz.release() }
    }

    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Black).safeDrawingPadding()) {
        if (!hasAudio) {
            Intro(askedOnce) {
                val perms = buildList {
                    add(audioPermission)
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permsLauncher.launch(perms.toTypedArray())
            }
        } else {
            LibraryScreen(
                songs = songs,
                loaded = loaded,
                ui = ui,
                viz = viz,
                frame = frame,
                onPlay = { list, i -> vm.play(list, i) },
                onShuffle = { list -> vm.shuffleAll(list); expanded = true },
                onToggle = vm::togglePlay,
                onNext = vm::next,
                onExpand = { expanded = true },
            )

            AnimatedVisibility(
                visible = expanded && ui.now != null,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                PlayerScreen(
                    ui = ui,
                    viz = viz,
                    frame = frame,
                    hasMic = hasMic,
                    onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onClose = { expanded = false },
                    onToggle = vm::togglePlay,
                    onNext = vm::next,
                    onPrev = vm::previous,
                    onSeek = vm::seekTo,
                    onShuffle = vm::toggleShuffle,
                    onRepeat = vm::cycleRepeat,
                )
            }
        }
    }
}

@Composable
private fun Intro(askedOnce: Boolean, onAllow: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Spacer(Modifier.weight(1f))
        T("Música", 60.sp, italic = true)
        Spacer(Modifier.height(20.dp))
        T(
            "Un reproductor para la música guardada en este teléfono. Funciona sin internet.",
            19.sp, Dim, maxLines = 5,
        )
        Spacer(Modifier.height(14.dp))
        T(
            "El permiso de audio se usa solo para que las animaciones sigan el ritmo de lo que suena en esta app. No se graba ni se envía nada.",
            14.sp, Faint, maxLines = 6,
        )
        Spacer(Modifier.height(36.dp))
        RectButton("Permitir acceso", filled = true, onClick = onAllow)
        if (askedOnce) {
            Spacer(Modifier.height(24.dp))
            T(
                "Si no aparece el aviso, habilitá el permiso de música y audio desde los ajustes de la app.",
                14.sp, Dim, italic = true, maxLines = 4,
            )
            Spacer(Modifier.height(12.dp))
            RectButton("Abrir ajustes") {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", ctx.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        Spacer(Modifier.weight(1.4f))
    }
}
