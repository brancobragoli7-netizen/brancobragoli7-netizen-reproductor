package com.cogito.musica

import android.media.audiofx.Visualizer
import android.os.SystemClock
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Escucha lo que está sonando EN ESTA APP (no el micrófono) y lo convierte en
 * valores de 0 a 1 por banda de frecuencia, más la forma de onda.
 * Android exige el permiso de "grabar audio" para esto, pero no se graba nada.
 */
class AudioViz(val bandCount: Int = 32, val wavePoints: Int = 128) {

    private val rawBands = FloatArray(bandCount)
    private val rawWave = FloatArray(wavePoints)

    /** Valores suavizados, listos para dibujar. */
    val bands = FloatArray(bandCount)
    val wave = FloatArray(wavePoints)
    var bass = 0f
        private set
    var time = 0f
        private set

    @Volatile private var lastData = 0L
    private var visualizer: Visualizer? = null

    fun attach(sessionId: Int) {
        release()
        if (sessionId <= 0) return
        visualizer = try {
            Visualizer(sessionId).apply {
                setEnabled(false)
                setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024))
                setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
                setDataCaptureListener(listener, Visualizer.getMaxCaptureRate(), true, true)
                setEnabled(true)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun release() {
        visualizer?.let { v ->
            runCatching { v.setEnabled(false) }
            runCatching { v.release() }
        }
        visualizer = null
    }

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, samplingRate: Int) {
            if (data == null || data.isEmpty()) return
            val step = data.size.toFloat() / wavePoints
            for (i in 0 until wavePoints) {
                val b = data[(i * step).toInt().coerceAtMost(data.size - 1)].toInt() and 0xFF
                rawWave[i] = (b - 128) / 128f
            }
            lastData = SystemClock.uptimeMillis()
        }

        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            if (fft == null || fft.size < 8) return
            val n = fft.size
            val bins = n / 2
            val binHz = (samplingRate / 1000f) / n
            var prevEnd = 0
            for (b in 0 until bandCount) {
                val f0 = MIN_F * (MAX_F / MIN_F).pow(b / bandCount.toFloat())
                val f1 = MIN_F * (MAX_F / MIN_F).pow((b + 1) / bandCount.toFloat())
                val k0 = max((f0 / binHz).toInt(), prevEnd + 1).coerceIn(1, bins - 1)
                val k1 = max((f1 / binHz).toInt(), k0).coerceIn(k0, bins - 1)
                prevEnd = k1
                var m = 0f
                for (k in k0..k1) {
                    val re = fft[2 * k].toFloat()
                    val im = fft[2 * k + 1].toFloat()
                    val mag = sqrt(re * re + im * im)
                    if (mag > m) m = mag
                }
                var v01 = 20f * log10(max(m, 1f)) / 42f
                v01 *= 1f + 0.5f * b / bandCount // los agudos tienen menos energía
                rawBands[b] = v01.coerceIn(0f, 1f).pow(1.3f)
            }
            lastData = SystemClock.uptimeMillis()
        }
    }

    /** Se llama en cada cuadro de animación: sube rápido, baja lento. */
    fun step(dt: Float) {
        time += dt
        val stale = visualizer == null || SystemClock.uptimeMillis() - lastData > 300
        val up = 1f - exp(-dt * 22f)
        val down = 1f - exp(-dt * 6f)
        for (i in bands.indices) {
            val target = if (stale) 0f else rawBands[i]
            bands[i] += (target - bands[i]) * (if (target > bands[i]) up else down)
        }
        val w = 1f - exp(-dt * 25f)
        for (i in wave.indices) {
            val target = if (stale) 0f else rawWave[i]
            wave[i] += (target - wave[i]) * w
        }
        bass = (bands[0] + bands[1] + bands[2] + bands[3]) / 4f
    }

    private companion object {
        const val MIN_F = 50f
        const val MAX_F = 16000f
    }
}
