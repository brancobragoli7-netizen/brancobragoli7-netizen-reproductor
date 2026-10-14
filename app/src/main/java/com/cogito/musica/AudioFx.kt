package com.cogito.musica

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ecualizador del sistema aplicado solo a la música de esta app. Recuerda los ajustes. */
object AudioFx {

    data class Band(val centerHz: Int, val level: Int)

    data class State(
        val available: Boolean = false,
        val enabled: Boolean = false,
        val bands: List<Band> = emptyList(),
        val min: Int = -1500,
        val max: Int = 1500,
        val presets: List<String> = emptyList(),
        val preset: Int = -1, // -1 = personalizado
        val bassSupported: Boolean = false,
        val bass: Int = 0, // 0..1000
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var eq: Equalizer? = null
    private var boost: BassBoost? = null
    private var prefs: SharedPreferences? = null

    fun attach(context: Context, sessionId: Int) {
        release()
        if (sessionId <= 0) return
        val p = context.getSharedPreferences("ecualizador", Context.MODE_PRIVATE)
        prefs = p
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
        boost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        val e = eq
        if (e == null) {
            _state.value = State(available = false)
            return
        }
        runCatching {
            val n = e.numberOfBands.toInt()
            val preset = p.getInt("preset", -1)
            if (preset >= 0 && preset < e.numberOfPresets) {
                e.usePreset(preset.toShort())
            } else {
                for (i in 0 until n) {
                    if (p.contains("b$i")) e.setBandLevel(i.toShort(), p.getInt("b$i", 0).toShort())
                }
            }
            val on = p.getBoolean("on", false)
            e.setEnabled(on)
            boost?.let { b ->
                if (b.strengthSupported) b.setStrength(p.getInt("bass", 0).toShort())
                b.setEnabled(on)
            }
        }
        publish()
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { boost?.release() }
        eq = null
        boost = null
    }

    fun setEnabled(on: Boolean) {
        runCatching { eq?.setEnabled(on) }
        runCatching { boost?.setEnabled(on) }
        prefs?.edit()?.putBoolean("on", on)?.apply()
        publish()
    }

    fun setBand(index: Int, level: Int) {
        val e = eq ?: return
        runCatching { e.setBandLevel(index.toShort(), level.toShort()) }
        prefs?.edit()?.apply {
            putInt("preset", -1)
            for (i in 0 until e.numberOfBands) putInt("b$i", e.getBandLevel(i.toShort()).toInt())
        }?.apply()
        publish(presetOverride = -1)
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        runCatching { e.usePreset(index.toShort()) }
        prefs?.edit()?.putInt("preset", index)?.apply()
        publish(presetOverride = index)
    }

    fun setBass(strength: Int) {
        val b = boost ?: return
        runCatching { b.setStrength(strength.coerceIn(0, 1000).toShort()) }
        prefs?.edit()?.putInt("bass", strength)?.apply()
        publish()
    }

    fun reset() {
        val e = eq ?: return
        runCatching { for (i in 0 until e.numberOfBands) e.setBandLevel(i.toShort(), 0) }
        runCatching { boost?.setStrength(0) }
        prefs?.edit()?.apply {
            putInt("preset", -1); putInt("bass", 0)
            for (i in 0 until e.numberOfBands) putInt("b$i", 0)
        }?.apply()
        publish(presetOverride = -1)
    }

    private fun publish(presetOverride: Int? = null) {
        val e = eq ?: return
        _state.value = runCatching {
            val range = e.bandLevelRange
            State(
                available = true,
                enabled = e.enabled,
                bands = (0 until e.numberOfBands).map { i ->
                    Band(e.getCenterFreq(i.toShort()) / 1000, e.getBandLevel(i.toShort()).toInt())
                },
                min = range[0].toInt(),
                max = range[1].toInt(),
                presets = (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) },
                preset = presetOverride ?: prefs?.getInt("preset", -1) ?: -1,
                bassSupported = boost?.strengthSupported == true,
                bass = runCatching { boost?.roundedStrength?.toInt() ?: 0 }.getOrDefault(0),
            )
        }.getOrDefault(State(available = false))
    }
}
