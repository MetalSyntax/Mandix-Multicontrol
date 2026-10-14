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
     * Transmite una trama de protocolo NEC estándar a 38.000 Hz
     * @param address Dirección del chasis / placa (8 bits: 0..255)
     * @param command Código del botón / función (8 bits: 0..255)
     */
    fun transmitNec(address: Int, command: Int): Boolean {
        if (!hasIrEmitter) {
            Log.w(TAG, "Dispositivo sin emisor IR o no soportado.")
            return false
        }

        try {
            val pattern = buildNecPattern(address, command)
            irManager?.transmit(38000, pattern)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitiendo señal IR: ${e.message}", e)
            return false
        }
    }

    /**
     * Construye la secuencia exacta de microsegundos alternando ON y OFF:
     * - Pulso líder: 9000µs ON, 4500µs OFF
     * - 32 bits de datos:
     *   - Bit 0: 560µs ON, 560µs OFF
     *   - Bit 1: 560µs ON, 1690µs OFF
     * - Bit de parada (Stop bit): 560µs ON
     */
    private fun buildNecPattern(address: Int, command: Int): IntArray {
        val pattern = ArrayList<Int>(67)

        // 1. Pulso Líder (Leader code)
        pattern.add(9000)
        pattern.add(4500)

        // 2. Trama de 4 bytes: [Dir, ~Dir, Cmd, ~Cmd]
        val invAddress = address.inv() and 0xFF
        val invCommand = command.inv() and 0xFF

        val bytes = intArrayOf(
            address and 0xFF,
            invAddress,
            command and 0xFF,
            invCommand
        )

        for (byteVal in bytes) {
            for (bitIdx in 0 until 8) {
                val bit = (byteVal shr bitIdx) and 1
                pattern.add(560) // Pulso ON constante
                if (bit == 1) {
                    pattern.add(1690) // Espacio OFF bit 1
                } else {
                    pattern.add(560)  // Espacio OFF bit 0
                }
            }
        }

        // 3. Pulso de parada final
        pattern.add(560)

        return pattern.toIntArray()
    }
}
