package com.example.rm014s

enum class AcMode { AUTO, COOL, DRY, FAN, HEAT }

/** Estado del aire: los mandos de aire envían el estado completo en cada tecla. [fan] 0 = auto, 1-3 = baja-alta. */
data class AcState(
    val power: Boolean = true, val mode: AcMode = AcMode.COOL, val temp: Int = 24, val fan: Int = 0,
    val swing: Boolean = false, val sleep: Boolean = false
)

/**
 * Protocolos de aires chinos con temperatura, portados de IRremoteESP8266 (ir_Gree, ir_Coolix, ir_Midea):
 * - GREE: Gree y sus clones (Tosot, Cooper&Hunter, Soleus, muchas marcas blancas).
 * - COOLIX: splits fabricados por Midea (mando RG57: Midea, Carrier, Electrolux, OEM).
 * - MIDEA: protocolo de 48 bits de Midea (portátiles, ventana, Pioneer).
 */
object AcCodec {
    val PROTOCOLS = listOf("GREE", "COOLIX", "MIDEA")

    fun tempRange(protocol: String) = if (protocol == "GREE") 16..30 else 17..30

    fun encode(protocol: String, s: AcState): IrSignal = IrSignal("RAW", rawFrequency = 38000, raw = when (protocol) {
        "GREE" -> gree(s)
        "COOLIX" -> coolix(coolixWord(s))
        else -> midea(mideaWord(s))
    })

    /** Tiempos ON/OFF alternados, empezando en marca. */
    private class Raw {
        val out = ArrayList<Int>()
        fun mark(us: Int) = out.add(us)
        fun space(us: Int) = out.add(us)
        fun bits(value: Int, n: Int, msbFirst: Boolean, mark: Int, one: Int, zero: Int) = repeat(n) { i ->
            val bit = if (msbFirst) n - 1 - i else i
            mark(mark); space(if ((value shr bit) and 1 == 1) one else zero)
        }
    }

    fun greeBytes(s: AcState): IntArray {
        val temp = if (s.mode == AcMode.AUTO) 25 else s.temp.coerceIn(16, 30) // AUTO fija 25 °C
        val fan = if (s.mode == AcMode.DRY) 1 else s.fan.coerceIn(0, 3)
        val mode = when (s.mode) { AcMode.AUTO -> 0; AcMode.COOL -> 1; AcMode.DRY -> 2; AcMode.FAN -> 3; AcMode.HEAT -> 4 }
        val b = IntArray(8)
        b[0] = mode or (if (s.power) 0x08 else 0) or (fan shl 4) or (if (s.swing) 0x40 else 0) or (if (s.sleep) 0x80 else 0)
        b[1] = temp - 16
        b[2] = 0x20 or (if (s.power) 0x40 else 0) // Light + bit de encendido del modelo YAW1F
        b[3] = 0x50
        b[4] = if (s.swing) 1 else 0 // SwingV automático
        b[5] = 0x20
        // Checksum de Kelvinator: 10 + nibbles bajos de bytes 0-3 + nibbles altos de 4-6
        val sum = 10 + (0..3).sumOf { b[it] and 0xF } + (4..6).sumOf { b[it] shr 4 }
        b[7] = (sum and 0xF) shl 4
        return b
    }

    private fun gree(s: AcState): IntArray {
        val b = greeBytes(s)
        val r = Raw()
        r.mark(9000); r.space(4500)
        for (i in 0..3) r.bits(b[i], 8, false, 620, 1600, 540)
        r.bits(0b010, 3, false, 620, 1600, 540)
        r.mark(620); r.space(19980)
        for (i in 4..7) r.bits(b[i], 8, false, 620, 1600, 540)
        r.mark(620)
        return r.out.toIntArray()
    }

    private val COOLIX_TEMP = intArrayOf(0b0000, 0b0001, 0b0011, 0b0010, 0b0110, 0b0111, 0b0101,
        0b0100, 0b1100, 0b1101, 0b1001, 0b1000, 0b1010, 0b1011) // 17..30 °C

    fun coolixWord(s: AcState): Int {
        if (!s.power) return 0xB27BE0
        val mode = when (s.mode) { AcMode.COOL -> 0; AcMode.DRY, AcMode.FAN -> 1; AcMode.AUTO -> 2; AcMode.HEAT -> 3 }
        val temp = if (s.mode == AcMode.FAN) 0b1110 else COOLIX_TEMP[s.temp.coerceIn(17, 30) - 17] // FAN = DRY sin temperatura
        val fan = if (s.mode == AcMode.AUTO || s.mode == AcMode.DRY) 0b000
            else when (s.fan) { 1 -> 0b100; 2 -> 0b010; 3 -> 0b001; else -> 0b101 }
        return (0xB2 shl 16) or (((fan shl 5) or 0x1F) shl 8) or (temp shl 4) or (mode shl 2)
    }

    private fun coolix(w: Int): IntArray {
        val r = Raw()
        repeat(2) { rep -> // el mando original lo envía dos veces
            r.mark(4692); r.space(4416)
            for (i in 2 downTo 0) {
                val byte = (w shr (8 * i)) and 0xFF
                r.bits(byte, 8, true, 552, 1656, 552)
                r.bits(byte xor 0xFF, 8, true, 552, 1656, 552)
            }
            r.mark(552)
            if (rep == 0) r.space(5244)
        }
        return r.out.toIntArray()
    }

    private fun rev8(v: Int) = Integer.reverse(v) ushr 24

    fun mideaWord(s: AcState): Long {
        val mode = when (s.mode) { AcMode.COOL -> 0; AcMode.DRY -> 1; AcMode.AUTO -> 2; AcMode.HEAT -> 3; AcMode.FAN -> 4 }
        val b4 = (if (s.power) 0x80 else 0) or (if (s.sleep) 0x40 else 0) or (s.fan.coerceIn(0, 3) shl 3) or mode
        val b3 = 0x40 or (s.temp.coerceIn(17, 30) - 17) // Celsius
        val w = (0xA1L shl 40) or (b4.toLong() shl 32) or (b3.toLong() shl 24) or (0xFFFFL shl 8)
        return w or mideaChecksum(w).toLong()
    }

    /** Suma de los bytes 1-5 con bits invertidos, en complemento a 256 (byte 0 del mensaje). */
    fun mideaChecksum(w: Long): Int = rev8((256 - (1..5).sumOf { rev8(((w shr (8 * it)) and 0xFF).toInt()) }) and 0xFF)

    private fun midea(word: Long): IntArray {
        var w = word
        val r = Raw()
        repeat(2) { phase -> // mensaje y luego el mismo mensaje invertido
            r.mark(4480); r.space(4480)
            for (i in 5 downTo 0) r.bits(((w shr (8 * i)) and 0xFF).toInt(), 8, true, 560, 1680, 560)
            r.mark(560)
            if (phase == 0) r.space(5600)
            w = w.inv()
        }
        return r.out.toIntArray()
    }

    /**
     * Teclas de acción única (conmutan en el aire, no van en el estado): "swing", "sleep", "led", "eco".
     * null = el protocolo la lleva en el estado ([AcState.swing]/[AcState.sleep]) o no la tiene.
     */
    fun command(protocol: String, action: String): IrSignal? {
        val raw = when (protocol) {
            "COOLIX" -> when (action) {
                "swing" -> coolix(0xB26BE0); "sleep" -> coolix(0xB2E003); "led" -> coolix(0xB5F5A5); else -> null
            }
            "MIDEA" -> when (action) {
                "swing" -> midea(0xA201FFFFFF7C); "led" -> midea(0xA208FFFFFF75); "eco" -> midea(0xA202FFFFFF7E); else -> null
            }
            else -> null
        }
        return raw?.let { IrSignal("RAW", rawFrequency = 38000, raw = it) }
    }

    /** Códigos de la librería (capturas de estados fijos): el más parecido al estado pedido. */
    fun libraryKey(s: AcState): List<String> = when {
        !s.power -> listOf("Off")
        s.mode == AcMode.DRY -> listOf("Dh", "Cool_lo", "Cool_hi")
        s.mode == AcMode.HEAT -> if (s.temp >= 26) listOf("Heat_hi", "Heat_lo") else listOf("Heat_lo", "Heat_hi")
        s.temp <= 21 -> listOf("Cool_hi", "Cool_lo")
        else -> listOf("Cool_lo", "Cool_hi")
    }
}
