package com.example.rm014s

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import kotlinx.coroutines.delay

class IrManagerHelper(context: Context) {
    private val TAG = "IrManagerHelper"
    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasIrEmitter: Boolean
        get() = irManager?.hasIrEmitter() ?: false

    /**
     * Devuelve información de depuración de frecuencias del hardware
     */
    fun getHardwareInfo(): String {
        if (!hasIrEmitter) return "Sin sensor IR de hardware"
        val ranges = irManager?.carrierFrequencies ?: return "Frecuencias: Desconocidas"
        return ranges.joinToString(", ") { "${it.minFrequency / 1000}-${it.maxFrequency / 1000}kHz" }
    }

    /**
     * Transmite una trama de protocolo NEC a la frecuencia seleccionada.
     * Para evitar el error de desbordamiento de buffer del kernel (MAX_BUFFER en MediaTek),
     * la trama tiene exactamente 68 enteros (longitud par con cierre en OFF).
     *
     * @param address Dirección (8 bits: 0..255)
     * @param command Comando (8 bits: 0..255)
     * @param frequency Frecuencia en Hz (ej. 38000, 36000, 40000, 56000)
     * @param addressHigh Segundo byte de dirección (opcional para NEC extendido)
     * @param doubleBurst Si es true, envía una segunda ráfaga tras 45ms (como los mandos físicos)
     * @param useCyclesMode Si es true, traduce microsegundos a ciclos de portadora (bug MediaTek HAL)
     */
    suspend fun transmitNec(
        address: Int,
        command: Int,
        frequency: Int = 38000,
        addressHigh: Int? = null,
        doubleBurst: Boolean = true,
        useCyclesMode: Boolean = false
    ): Boolean {
        if (!hasIrEmitter) {
            Log.w(TAG, "Hardware IR no disponible.")
            return false
        }

        try {
            // Construye una trama compacta de 68 elementos exactamente
            val pattern = buildSafeNecFrame(
                address = address,
                command = command,
                addressHigh = addressHigh,
                frequency = frequency,
                useCyclesMode = useCyclesMode
            )

            // Primera transmisión
            irManager?.transmit(frequency, pattern)

            // Si se solicita doble ráfaga, espera 45ms y transmite la segunda trama
            if (doubleBurst) {
                delay(45)
                irManager?.transmit(frequency, pattern)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitiendo IR ($frequency Hz): ${e.message}", e)
            return false
        }
    }

    /**
     * Construye una trama NEC oficial de 68 elementos exactos:
     * - Índice 0: Pulso Líder ON (9000 µs)
     * - Índice 1: Espacio Líder OFF (4500 µs)
     * - Índices 2 a 65: 32 bits de datos (32 pares de [560 ON, espacio OFF])
     * - Índice 66: Pulso Stop ON (560 µs)
     * - Índice 67: Cierre OFF obligatorio (10000 µs)
     *
     * Longitud = 68 enteros. Siempre par, siempre cerrado en OFF.
     * Cabe en cualquier buffer DMA de Linux/MediaTek/Qualcomm.
     */
    private fun buildSafeNecFrame(
        address: Int,
        command: Int,
        addressHigh: Int?,
        frequency: Int,
        useCyclesMode: Boolean
    ): IntArray {
        val pattern = IntArray(68)
        var idx = 0

        fun toVal(microsec: Int): Int {
            return if (useCyclesMode) {
                // Conversión de microsegundos a ciclos de reloj
                ((microsec.toLong() * frequency) / 1000000L).toInt().coerceAtLeast(1)
            } else {
                microsec
            }
        }

        // 1. Líder
        pattern[idx++] = toVal(9000) // ON
        pattern[idx++] = toVal(4500) // OFF

        // 2. Datos: [Dir, ~Dir / DirHigh, Cmd, ~Cmd]
        val addrLow = address and 0xFF
        val addrHigh = (addressHigh ?: address.inv()) and 0xFF
        val cmd = command and 0xFF
        val invCmd = cmd.inv() and 0xFF

        val bytes = intArrayOf(addrLow, addrHigh, cmd, invCmd)

        for (byteVal in bytes) {
            for (bitIdx in 0 until 8) {
                val bit = (byteVal shr bitIdx) and 1
                pattern[idx++] = toVal(560) // Portadora ON siempre 560µs
                if (bit == 1) {
                    pattern[idx++] = toVal(1690) // Espacio bit 1
                } else {
                    pattern[idx++] = toVal(560)  // Espacio bit 0
                }
            }
        }

        // 3. Stop bit y Cierre OFF
        pattern[idx++] = toVal(560)   // ON stop bit
        pattern[idx++] = toVal(10000) // OFF cierre (crucial para evitar cuelgues del HAL)

        return pattern
    }

    /**
     * Tren de pulsos rápido para que el usuario pueda verificar con la cámara de otro celular
     * que el LED infrarrojo del Tecno Camon 30 realmente emite luz física.
     */
    suspend fun transmitCameraTestSignal(frequency: Int = 38000) {
        if (!hasIrEmitter) return
        val testPattern = intArrayOf(
            15000, 15000,
            15000, 15000,
            15000, 15000,
            15000, 15000,
            15000, 15000
        )
        try {
            repeat(4) {
                irManager?.transmit(frequency, testPattern)
                delay(120)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en test de cámara: ${e.message}")
        }
    }
}
