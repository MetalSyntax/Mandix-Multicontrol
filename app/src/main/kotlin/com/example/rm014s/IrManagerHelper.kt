package com.example.rm014s

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class IrManagerHelper(context: Context) {
    private val TAG = "IrManagerHelper"
    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    /**
     * Modo de emisión (ver DIAGNOSTICO_HARDWARE_Y_FALLAS.md):
     * ORIGINAL = comportamiento previo; ALT = espera real de cada trama (B) + mínimo 3 tramas (C);
     * ALT_INV = ALT + compensación de polaridad invertida del driver MediaTek (A).
     */
    enum class Mode { ORIGINAL, ALT, ALT_INV }
    var mode = Mode.ORIGINAL

    val hasIrEmitter: Boolean
        get() = irManager?.hasIrEmitter() ?: false

    /**
     * Indica si el hardware declara soporte para [frequency].
     * Si el HAL no reporta rangos, se asume que sí.
     */
    fun isFrequencySupported(frequency: Int): Boolean {
        val ranges = irManager?.carrierFrequencies ?: return true
        return ranges.any { frequency in it.minFrequency..it.maxFrequency }
    }

    /** Rangos de portadora del hardware, para mostrar en pantalla. */
    fun getHardwareInfo(context: Context): String {
        if (!hasIrEmitter) return context.getString(R.string.hw_no_ir)
        val ranges = irManager?.carrierFrequencies ?: return context.getString(R.string.hw_freq_unknown)
        return ranges.joinToString(", ") { "${it.minFrequency / 1000}-${it.maxFrequency / 1000}kHz" }
    }

    /**
     * Emite [signal] con su protocolo real (NEC, Samsung32, SIRC, RC5, RC6, Kaseikyo...).
     * [frequencyOverride] = 0 usa la portadora propia del protocolo.
     * [firstFrameOnly]: solo la trama principal (búsqueda profunda, para ir más rápido).
     * [padRepeats] = false para aires: su mensaje es un estado completo (a veces en 2 bloques) y no se repite.
     */
    suspend fun transmit(signal: IrSignal, frequencyOverride: Int = 0, firstFrameOnly: Boolean = false, padRepeats: Boolean = true): Boolean {
        if (!hasIrEmitter) {
            Log.w(TAG, "Hardware IR no disponible.")
            return false
        }
        return try {
            val p = IrCodec.encode(signal)
            val freq = if (frequencyOverride > 0) frequencyOverride else p.frequency
            if (mode == Mode.ORIGINAL) {
                // transmit() bloquea mientras emite: fuera del hilo de UI. Una trama por llamada.
                for (frame in if (firstFrameOnly) p.frames.take(1) else p.frames) {
                    withContext(Dispatchers.IO) { irManager?.transmit(freq, frame.pattern) }
                    if (frame.gapUs > 0) delay((frame.gapUs + 999) / 1000L)
                }
                return true
            }
            // Alternativa: firstFrameOnly se ignora, una trama aislada la descarta el firmware del TV (C).
            for (frame in if (padRepeats) withRepeats(p.frames) else p.frames) {
                val pattern = if (mode == Mode.ALT_INV) invertForDriver(frame.pattern) else frame.pattern
                val t0 = SystemClock.elapsedRealtime()
                withContext(Dispatchers.IO) { irManager?.transmit(freq, pattern) }
                // transmit() puede volver antes de que el HAL termine de emitir (B): esperar lo que falte.
                val pendingMs = pattern.sum() / 1000L - (SystemClock.elapsedRealtime() - t0)
                delay(pendingMs.coerceAtLeast(0) + (frame.gapUs + 999) / 1000L)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitiendo ${signal.describe()}: ${e.message}", e)
            false
        }
    }

    companion object {
        private const val MIN_FRAMES = 3
        private const val PERIOD_US = 110_000 // periodo NEC: también seguro para el resto de protocolos
        private const val INV_PAD_US = 500

        /** Completa hasta [MIN_FRAMES] repitiendo la última trama (el código de repetición, si existe). */
        fun withRepeats(frames: List<IrPattern.Frame>): List<IrPattern.Frame> {
            if (frames.isEmpty() || frames.size >= MIN_FRAMES) return frames
            val last = frames.last()
            val gap = maxOf(last.gapUs, PERIOD_US - last.pattern.sum(), 40_000)
            val rep = IrPattern.Frame(last.pattern, gap)
            return frames.dropLast(1) + List(MIN_FRAMES - frames.size + 1) { rep }
        }

        /**
         * Driver con polaridad invertida (A): un silencio de relleno al inicio desplaza las marcas a
         * índices impares, que ese driver enciende. Otro relleno al final mantiene la longitud impar
         * que exige el HAL MediaTek.
         */
        fun invertForDriver(pattern: IntArray): IntArray = intArrayOf(INV_PAD_US) + pattern + INV_PAD_US
    }

    /**
     * Tren de pulsos lento para verificar con la cámara de otro celular
     * que el LED infrarrojo de este equipo realmente emite luz.
     */
    suspend fun transmitCameraTestSignal(frequency: Int = 38000) {
        if (!hasIrEmitter) return
        val testPattern = IntArray(9) { 15000 } // impar: termina en pulso (LIRC)
        try {
            repeat(4) {
                withContext(Dispatchers.IO) { irManager?.transmit(frequency, testPattern) }
                delay(120)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en test de cámara: ${e.message}")
        }
    }
}
