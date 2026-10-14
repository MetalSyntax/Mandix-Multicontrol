package com.example.rm014s

/**
 * Una señal IR: o bien un protocolo con dirección/comando (convención de Flipper Zero,
 * que es como viene la librería tv.ir), o bien una captura RAW en microsegundos.
 */
data class IrSignal(
    val protocol: String,
    val address: Long = 0,
    val command: Long = 0,
    val rawFrequency: Int = 0,
    val raw: IntArray? = null
) {
    val isRaw get() = raw != null
    fun describe(): String =
        if (isRaw) "RAW ${rawFrequency / 1000}kHz" else "$protocol 0x%X/0x%X".format(address, command)
}

/**
 * Señal lista para ConsumerIrManager: portadora + tramas. Cada trama va en su propio transmit()
 * seguida de una pausa ([Frame.gapUs]); los silencios largos nunca van dentro del patrón porque
 * el HAL de MediaTek (Tecno/Infinix/Xiaomi) los convierte a un búfer de tamaño fijo.
 */
class IrPattern(val frequency: Int, val frames: List<Frame>) {
    class Frame(val pattern: IntArray, val gapUs: Int)
}

/**
 * Codificadores por protocolo, portados de flipperzero-firmware/lib/infrared/encoder_decoder
 * (+ NECx, Panasonic y JVC de IRDB) para emitir cada código con su trama exacta.
 */
object IrCodec {
    private var rcToggle = false
    private const val SPLIT_GAP = 15000 // µs: un silencio mayor separa tramas en capturas RAW

    fun frequencyOf(protocol: String): Int = when (protocol) {
        "SIRC", "SIRC15", "SIRC20", "Pioneer" -> 40000
        "RC5", "RC5X", "RC6" -> 36000
        "Kaseikyo", "Panasonic" -> 37000
        else -> 38000
    }

    fun encode(s: IrSignal): IrPattern {
        if (s.raw != null) return IrPattern(s.rawFrequency, splitRaw(s.raw))
        val a = s.address
        val c = s.command
        val p = Builder()
        when (s.protocol) {
            "NEC" -> nec(p, bytesLsb(listOf(a and 0xFF, a.inv() and 0xFF, c and 0xFF, c.inv() and 0xFF)))
            "NECext" -> nec(p, bytesLsb(listOf(a and 0xFF, (a shr 8) and 0xFF, c and 0xFF, (c shr 8) and 0xFF)))
            "NEC42" -> nec(p, bitsLsb(a, 13) + bitsLsb(a.inv(), 13) + bitsLsb(c, 8) + bitsLsb(c.inv(), 8))
            "NEC42ext" -> nec(p, bitsLsb(a, 26) + bitsLsb(c, 16))
            "Samsung32" -> {
                pdm(p, 4500, 4500, 550, 550, 1650, bytesLsb(listOf(a and 0xFF, a and 0xFF, c and 0xFF, c.inv() and 0xFF)))
                p.gap(46000)
            }
            // NECx (IRDB): cabecera Samsung con subdispositivo libre; se repite la trama como el mando
            "NECx" -> repeat(2) {
                pdm(p, 4500, 4500, 560, 560, 1690, bytesLsb(listOf(a and 0xFF, (a shr 8) and 0xFF, c and 0xFF, c.inv() and 0xFF)))
                p.gap(54000)
            }
            "SIRC", "SIRC15", "SIRC20" -> {
                val bits = bitsLsb(c, 7) + when (s.protocol) {
                    "SIRC" -> bitsLsb(a, 5)
                    "SIRC15" -> bitsLsb(a, 8)
                    else -> bitsLsb(a, 13)
                }
                // Sony exige mínimo 3 tramas cada 45 ms o la TV ignora la tecla
                repeat(3) {
                    p.mark(2400); p.space(600)
                    bits.forEachIndexed { i, b ->
                        p.mark(if (b) 1200 else 600)
                        if (i < bits.lastIndex) p.space(600)
                    }
                    p.gap(maxOf(10000, 45000 - p.frameTotal))
                }
            }
            "RC5", "RC5X" -> {
                val s2 = if (s.protocol == "RC5") true else (c and 0x40) == 0L
                val bits = listOf(true, s2, nextToggle()) + bitsMsb(a, 5) + bitsMsb(c, 6)
                // RC5: '1' = espacio→marca, '0' = marca→espacio (889 µs cada mitad)
                bits.forEach { b -> if (b) { p.space(889); p.mark(889) } else { p.mark(889); p.space(889) } }
                p.gap(27000)
            }
            "RC6" -> {
                p.mark(2666); p.space(889)
                // RC6: '1' = marca→espacio (444 µs), el bit toggle dura el doble
                listOf(true, false, false, false).forEach { b -> if (b) { p.mark(444); p.space(444) } else { p.space(444); p.mark(444) } }
                if (nextToggle()) { p.mark(889); p.space(889) } else { p.space(889); p.mark(889) }
                (bitsMsb(a, 8) + bitsMsb(c, 8)).forEach { b -> if (b) { p.mark(444); p.space(444) } else { p.space(444); p.mark(444) } }
                p.gap(27000)
            }
            "Kaseikyo" -> {
                val id = (a shr 24) and 3
                val vendor = (a shr 8) and 0xFFFF
                val genre1 = (a shr 4) and 0xF
                val genre2 = a and 0xF
                val d0 = vendor and 0xFF
                val d1 = vendor shr 8
                var parity = d0 xor d1
                parity = (parity and 0xF) xor (parity shr 4)
                val d2 = (parity and 0xF) or (genre1 shl 4)
                val d3 = genre2 or ((c and 0xF) shl 4)
                val d4 = (id shl 6) or ((c shr 4) and 0x3F)
                val d5 = d2 xor d3 xor d4
                pdm(p, 3456, 1728, 432, 432, 1296, bytesLsb(listOf(d0, d1, d2, d3, d4, d5)))
                p.gap(74000)
            }
            // Panasonic (IRDB): fabricante 0x2002, D, S, F y XOR
            "Panasonic" -> {
                val d = a and 0xFF
                val sd = (a shr 8) and 0xFF
                pdm(p, 3456, 1728, 432, 432, 1296, bytesLsb(listOf(0x02, 0x20, d, sd, c and 0xFF, d xor sd xor (c and 0xFF))))
                p.gap(74000)
            }
            // JVC: 8400/4200, 8 bits dir + 8 cmd; la 2da trama va sin cabecera
            "JVC" -> {
                p.mark(8400); p.space(4200)
                repeat(2) {
                    bytesLsb(listOf(a and 0xFF, c and 0xFF)).forEach { b -> p.mark(525); p.space(if (b) 1575 else 525) }
                    p.mark(525)
                    p.gap(maxOf(10000, 50000 - p.frameTotal))
                }
            }
            "RCA" -> {
                val bits = bitsLsb(a, 4) + bitsLsb(c, 8) + bitsLsb(a.inv(), 4) + bitsLsb(c.inv(), 8)
                pdm(p, 4000, 4000, 500, 1000, 2000, bits)
                p.gap(8000)
            }
            "Pioneer" -> repeat(2) {
                pdm(p, 8500, 4225, 500, 500, 1500,
                    bytesLsb(listOf(a and 0xFF, a.inv() and 0xFF, c and 0xFF, c.inv() and 0xFF)) + false)
                p.gap(26000)
            }
            "Sharp" -> {
                // Sharp (Aquos): 5 dir + 8 cmd + exp=1 + chk; 2da trama con comando invertido tras 40 ms
                for (second in listOf(false, true)) {
                    val cmd = if (second) c.inv() else c
                    val bits = bitsLsb(a, 5) + bitsLsb(cmd, 8) + listOf(!second, second)
                    bits.forEach { b -> p.mark(320); p.space(if (b) 1680 else 680) }
                    p.mark(320); p.gap(40000)
                }
            }
            else -> throw IllegalArgumentException("Protocolo no soportado: ${s.protocol}")
        }
        return IrPattern(frequencyOf(s.protocol), p.build())
    }

    /** NEC: trama + un código de repetición a los 110 ms, como un mando físico al pulsar. */
    private fun nec(p: Builder, bits: List<Boolean>) {
        pdm(p, 9000, 4500, 560, 560, 1690, bits)
        p.gap(110000 - p.frameTotal)
        p.mark(9000); p.space(2250); p.mark(560)
        p.gap(40000)
    }

    /** Modulación por distancia de pulsos: preámbulo, bits (marca fija + espacio variable) y marca de stop. */
    private fun pdm(p: Builder, pm: Int, ps: Int, mark: Int, s0: Int, s1: Int, bits: List<Boolean>) {
        p.mark(pm); p.space(ps)
        bits.forEach { p.mark(mark); p.space(if (it) s1 else s0) }
        p.mark(mark)
    }

    private fun nextToggle(): Boolean { rcToggle = !rcToggle; return rcToggle }

    private fun bitsLsb(v: Long, n: Int) = (0 until n).map { (v shr it) and 1L == 1L }
    private fun bitsMsb(v: Long, n: Int) = (n - 1 downTo 0).map { (v shr it) and 1L == 1L }
    private fun bytesLsb(bytes: List<Long>) = bytes.flatMap { bitsLsb(it, 8) }

    /** Parte una captura RAW en tramas por sus silencios largos. */
    private fun splitRaw(raw: IntArray): List<IrPattern.Frame> {
        val p = Builder()
        raw.forEachIndexed { i, us ->
            when {
                i % 2 == 0 -> p.mark(us)
                us >= SPLIT_GAP || i == raw.lastIndex -> p.gap(maxOf(us, SPLIT_GAP))
                else -> p.space(us)
            }
        }
        return p.build()
    }

    /**
     * El driver LIRC de Linux (Xiaomi/MediaTek y la mayoría de HAL) rechaza con EINVAL los patrones
     * de longitud par, y Android solo lo registra en logcat: debe terminar en pulso (longitud impar).
     */
    private fun endOnPulse(raw: IntArray): IntArray =
        if (raw.size % 2 == 0) raw.copyOf(raw.size - 1) else raw

    /** Acumula tiempos fusionando niveles consecutivos iguales (necesario en Manchester). */
    private class Builder {
        private val frames = ArrayList<IrPattern.Frame>()
        private val out = ArrayList<Int>()
        var frameTotal = 0; private set
        fun mark(us: Int) = add(us, true)
        fun space(us: Int) = add(us, false)
        private fun add(us: Int, on: Boolean) {
            if (us <= 0 || (out.isEmpty() && !on)) return // una trama siempre empieza encendida
            frameTotal += us
            val nextIsOn = out.size % 2 == 0 // índice par = ON
            if (on == nextIsOn) out.add(us) else out[out.lastIndex] += us
        }
        /** Cierra la trama actual; [us] = silencio hasta la siguiente (se hace con delay, no en el patrón). */
        fun gap(us: Int) {
            if (out.isNotEmpty()) frames.add(IrPattern.Frame(endOnPulse(out.toIntArray()), us))
            out.clear(); frameTotal = 0
        }
        fun build(): List<IrPattern.Frame> { gap(0); return frames }
    }
}
