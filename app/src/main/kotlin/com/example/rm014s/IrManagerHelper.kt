package com.example.rm014s

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class IrManagerHelper(context: Context) {
    private val TAG = "IrManagerHelper"
    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

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
     */
    suspend fun transmit(signal: IrSignal, frequencyOverride: Int = 0, firstFrameOnly: Boolean = false): Boolean {
        if (!hasIrEmitter) {
            Log.w(TAG, "Hardware IR no disponible.")
            return false
        }
        return try {
            val p = IrCodec.encode(signal)
            val freq = if (frequencyOverride > 0) frequencyOverride else p.frequency
            // transmit() bloquea mientras emite: fuera del hilo de UI. Una trama por llamada.
            for (frame in if (firstFrameOnly) p.frames.take(1) else p.frames) {
                withContext(Dispatchers.IO) { irManager?.transmit(freq, frame.pattern) }
                if (frame.gapUs > 0) delay((frame.gapUs + 999) / 1000L)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitiendo ${signal.describe()}: ${e.message}", e)
            false
        }
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
