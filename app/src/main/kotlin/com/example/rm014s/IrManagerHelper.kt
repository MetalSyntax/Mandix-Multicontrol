package com.example.rm014s

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log

class IrManagerHelper(context: Context) {
    private val TAG = "IrManagerHelper"
    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasIrEmitter: Boolean
        get() = irManager?.hasIrEmitter() ?: false

    /**
     * Devuelve los rangos de frecuencias soportados por el hardware del teléfono
     */
    fun getSupportedFrequencyRanges(): String {
        val ranges = irManager?.carrierFrequencies ?: return "No disponible"
        return ranges.joinToString(", ") { "${it.minFrequency / 1000}kHz-${it.maxFrequency / 1000}kHz" }
    }

    /**
     * Transmite una trama de protocolo NEC con DOBLE RÁFAGA / REPETICIÓN
     * @param address Dirección (8 bits: 0..255)
     * @param command Código del comando (8 bits: 0..255)
     * @param frequency Frecuencia en Hz (por defecto 38000 Hz, o 36000, 40000, 56000)
     * @param addressHigh Segundo byte de dirección para NEC extendido (opcional)
     * @param sendRepeat Si es true, envía pulso de repetición/segunda trama para asegurar recepción
     */
    fun transmitNec(
        address: Int,
        command: Int,
        frequency: Int = 38000,
        addressHigh: Int? = null,
        sendRepeat: Boolean = true
    ): Boolean {
        if (!hasIrEmitter) {
            Log.w(TAG, "Dispositivo sin emisor IR.")
            return false
        }

        try {
            val pattern = buildNecPattern(
                address = address,
                command = command,
                addressHigh = addressHigh,
                includeRepeat = sendRepeat
            )
            irManager?.transmit(frequency, pattern)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitiendo señal IR ($frequency Hz): ${e.message}", e)
            return false
        }
    }

    /**
     * Construye la secuencia exacta de microsegundos con doble ráfaga:
     * 1. Pulso Líder: 9000µs ON, 4500µs OFF
     * 2. 32 bits de datos (LSB first):
     *    - Byte 0: Address Low
     *    - Byte 1: Address High (o ~Address Low en NEC estándar)
     *    - Byte 2: Command
     *    - Byte 3: ~Command
     * 3. Bit de parada: 560µs ON
     * 4. Pausa de repetición: 38000µs OFF
     * 5. Pulso de repetición NEC oficial: 9000µs ON, 2250µs OFF, 560µs ON
     *    (o segunda trama completa, lo que garantiza que los receptores con filtro AGC la capten)
     */
    private fun buildNecPattern(
        address: Int,
        command: Int,
        addressHigh: Int?,
        includeRepeat: Boolean
    ): IntArray {
        val pattern = ArrayList<Int>(140)

        fun appendSingleFrame() {
            // Líder
            pattern.add(9000)
            pattern.add(4500)

            val addrLow = address and 0xFF
            val addrHigh = (addressHigh ?: address.inv()) and 0xFF
            val cmd = command and 0xFF
            val invCmd = cmd.inv() and 0xFF

            val bytes = intArrayOf(addrLow, addrHigh, cmd, invCmd)

            for (byteVal in bytes) {
                for (bitIdx in 0 until 8) {
                    val bit = (byteVal shr bitIdx) and 1
                    pattern.add(560) // Pulso portadora ON
                    if (bit == 1) {
                        pattern.add(1690) // Espacio OFF bit 1
                    } else {
                        pattern.add(560)  // Espacio OFF bit 0
                    }
                }
            }

            // Stop bit
            pattern.add(560)
        }

        // Trama 1
        appendSingleFrame()

        if (includeRepeat) {
            // Pausa entre ráfagas (40 ms estándar)
            pattern.add(40000)

            // Ráfaga 2 completa (Doble transmisión para asegurar recepción en placas chinas)
            appendSingleFrame()

            // Pausa y código de repetición estándar NEC
            pattern.add(35000)
            pattern.add(9000)
            pattern.add(2250)
            pattern.add(560)
        }

        return pattern.toIntArray()
    }
}
