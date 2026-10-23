package com.example.rm014s

import android.content.Context

/**
 * Un "código" del control: un protocolo + dirección base (o una captura RAW de encendido)
 * y las teclas conocidas. Las teclas que falten se completan con el mapa de la marca/protocolo.
 * Nombres de tecla = los de la librería Flipper (Power, Vol_up, Vol_dn, Ch_next, Ch_prev, Mute)
 * más los de [Keymaps].
 */
data class RemoteProfile(
    val id: String,
    val name: String,
    val brandButtonLabel: String, // ej. "SET + 1"
    val codeNumber: String,       // ej. "001"
    val base: IrSignal,
    val keys: Map<String, IrSignal> = emptyMap()
) {
    val frequency: Int get() = if (base.isRaw) base.rawFrequency else IrCodec.frequencyOf(base.protocol)

    fun signal(key: String): IrSignal? =
        keys[key] ?: if (base.isRaw) null else Keymaps.command(base.protocol, base.address, key)?.let { base.copy(command = it) }
}

/** Comandos de cada tecla por marca/protocolo (las que no están en la librería). */
object Keymaps {
    private val digits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    /** Mapa NEC genérico (Toshiba/placas chinas CX-507/MStar): el que usaba la app originalmente. */
    private val GENERIC = mapOf(
        "Power" to 0x12, "Mute" to 0x10, "Vol_up" to 0x1A, "Vol_dn" to 0x1E, "Ch_next" to 0x1B, "Ch_prev" to 0x1F,
        "Ch_list" to 0x14, "Return" to 0x15, "P_mode" to 0x20, "Guide" to 0x21, "S_mode" to 0x22,
        "Menu" to 0x17, "Source" to 0x0B, "Up" to 0x18, "Down" to 0x52, "Left" to 0x5A, "Right" to 0x5E, "Ok" to 0x16,
        "Back" to 0x13, "Exit" to 0x1C, "Display" to 0x19, "Sleep" to 0x2A, "Fav" to 0x2B, "Info" to 0x2C,
        "Audio" to 0x2D, "Home" to 0x2E, "Rew" to 0x34, "Play" to 0x32, "Stop" to 0x33, "Ffwd" to 0x35,
        "Red" to 0x38, "Green" to 0x39, "Yellow" to 0x3A, "Blue" to 0x3B
    ) + digits.associateWith { it.toInt() }

    private val LG = mapOf(
        "Power" to 0x08, "Mute" to 0x09, "Vol_up" to 0x02, "Vol_dn" to 0x03, "Ch_next" to 0x00, "Ch_prev" to 0x01,
        "Ch_list" to 0x53, "Return" to 0x1A, "P_mode" to 0x4D, "Guide" to 0xA9, "S_mode" to 0x52,
        "Menu" to 0x43, "Source" to 0x0B, "Up" to 0x40, "Down" to 0x41, "Left" to 0x07, "Right" to 0x06, "Ok" to 0x44,
        "Back" to 0x28, "Exit" to 0x5B, "Display" to 0xAA, "Sleep" to 0x0E, "Fav" to 0x1E, "Info" to 0xAA,
        "Audio" to 0x0A, "Home" to 0x7C, "Rew" to 0x8F, "Play" to 0xB0, "Stop" to 0xB1, "Ffwd" to 0x8E,
        "Red" to 0x72, "Green" to 0x71, "Yellow" to 0x63, "Blue" to 0x61
    ) + digits.associateWith { 0x10 + it.toInt() }

    private val SAMSUNG = mapOf(
        "Power" to 0x02, "Mute" to 0x0F, "Vol_up" to 0x07, "Vol_dn" to 0x0B, "Ch_next" to 0x12, "Ch_prev" to 0x10,
        "Ch_list" to 0x6B, "Return" to 0x13, "P_mode" to 0x28, "Guide" to 0x4F,
        "Menu" to 0x1A, "Source" to 0x01, "Up" to 0x60, "Down" to 0x61, "Left" to 0x65, "Right" to 0x62, "Ok" to 0x68,
        "Back" to 0x58, "Exit" to 0x2D, "Display" to 0x1F, "Sleep" to 0x03, "Info" to 0x1F,
        "Home" to 0x79, "Rew" to 0x45, "Play" to 0x47, "Stop" to 0x46, "Ffwd" to 0x48,
        "Red" to 0x6C, "Green" to 0x14, "Yellow" to 0x15, "Blue" to 0x16,
        "1" to 0x04, "2" to 0x05, "3" to 0x06, "4" to 0x08, "5" to 0x09, "6" to 0x0A,
        "7" to 0x0C, "8" to 0x0D, "9" to 0x0E, "0" to 0x11
    )

    private val SONY = mapOf(
        "Power" to 0x15, "Mute" to 0x14, "Vol_up" to 0x12, "Vol_dn" to 0x13, "Ch_next" to 0x10, "Ch_prev" to 0x11,
        "Return" to 0x3B, "Menu" to 0x60, "Home" to 0x60, "Source" to 0x25, "Display" to 0x3A, "Info" to 0x3A,
        "Up" to 0x74, "Down" to 0x75, "Left" to 0x34, "Right" to 0x33, "Ok" to 0x65,
        "0" to 0x09
    ) + (1..9).associate { it.toString() to it - 1 }

    private val PHILIPS_RC5 = mapOf(
        "Power" to 0x0C, "Mute" to 0x0D, "Vol_up" to 0x10, "Vol_dn" to 0x11, "Ch_next" to 0x20, "Ch_prev" to 0x21,
        "Source" to 0x38, "Display" to 0x0F, "Info" to 0x0F, "Sleep" to 0x26,
        "Up" to 0x50, "Down" to 0x51, "Left" to 0x55, "Right" to 0x56, "Ok" to 0x57,
        "Red" to 0x37, "Green" to 0x36, "Yellow" to 0x32, "Blue" to 0x34
    ) + digits.associateWith { it.toInt() }

    private val PHILIPS_RC6 = mapOf(
        "Power" to 0x0C, "Mute" to 0x0D, "Vol_up" to 0x10, "Vol_dn" to 0x11, "Ch_next" to 0x20, "Ch_prev" to 0x21,
        "Menu" to 0x54, "Home" to 0x54, "Source" to 0x38, "Back" to 0x0A, "Display" to 0x0F, "Info" to 0x0F, "Guide" to 0xCC,
        "Up" to 0x58, "Down" to 0x59, "Left" to 0x5A, "Right" to 0x5B, "Ok" to 0x5C,
        "Rew" to 0x2B, "Play" to 0x2C, "Stop" to 0x31, "Ffwd" to 0x28,
        "Red" to 0x6D, "Green" to 0x6E, "Yellow" to 0x6F, "Blue" to 0x70
    ) + digits.associateWith { it.toInt() }

    private val SHARP = mapOf(
        "Power" to 0x16, "Mute" to 0x17, "Vol_up" to 0x14, "Vol_dn" to 0x15, "Ch_next" to 0x11, "Ch_prev" to 0x12,
        "Source" to 0x13, "0" to 0x0A
    ) + (1..9).associate { it.toString() to it }

    fun command(protocol: String, address: Long, key: String): Long? {
        val map = when {
            protocol == "NEC" && address == 0x04L -> LG
            protocol == "Samsung32" -> SAMSUNG
            protocol.startsWith("SIRC") -> SONY
            protocol.startsWith("RC5") -> PHILIPS_RC5
            protocol == "RC6" -> PHILIPS_RC6
            protocol == "Sharp" -> SHARP
            protocol.startsWith("NEC") -> GENERIC
            else -> return null
        }
        val cmd = map[key]?.toLong() ?: return null
        // NECext lleva el byte inverso dentro del comando (convención Flipper)
        return if (protocol == "NECext") cmd or ((cmd.inv() and 0xFF) shl 8) else cmd
    }
}

object RemoteConstants {
    private fun p(id: String, name: String, label: String, code: String, protocol: String, address: Long, keys: Map<String, Int> = emptyMap()) =
        RemoteProfile(id, name, label, code, IrSignal(protocol, address),
            keys.mapValues { IrSignal(protocol, address, it.value.toLong()) })

    /** NEC con dirección extendida: byte alto distinto del inverso → NECext. */
    private fun necExt(lo: Int, hi: Int, keys: Map<String, Int>): Pair<Long, Map<String, Int>> =
        (lo or (hi shl 8)).toLong() to keys.mapValues { (_, c) -> c or ((c.inv() and 0xFF) shl 8) }

    private fun nx(id: String, name: String, label: String, code: String, lo: Int, hi: Int, keys: Map<String, Int> = emptyMap()) =
        if (hi == (lo.inv() and 0xFF)) p(id, name, label, code, "NEC", lo.toLong(), keys)
        else necExt(lo, hi, keys).let { (a, k) -> p(id, name, label, code, "NECext", a, k) }

    private fun core(power: Int, volUp: Int, volDn: Int, chUp: Int, chDn: Int) =
        mapOf("Power" to power, "Vol_up" to volUp, "Vol_dn" to volDn, "Ch_next" to chUp, "Ch_prev" to chDn)

    // Perfiles de marca del manual RM-014S+, cada uno con su protocolo real
    val PROFILES = listOf(
        p("lg", "LG", "SET + 1", "001", "NEC", 0x04),
        p("sony", "SONY", "SET + 8", "002", "SIRC", 0x01),
        p("samsung", "SAMSUNG", "SET + 6", "003", "Samsung32", 0x07),
        p("sharp", "SHARP", "SET + 0", "004", "Sharp", 0x01),
        p("panasonic", "PANASONIC", "SET + 9", "005", "Kaseikyo", 0x00200280,
            mapOf("Power" to 0x3D0, "Vol_up" to 0x200, "Vol_dn" to 0x210, "Ch_next" to 0x340, "Mute" to 0x320)),
        p("toshiba", "TOSHIBA / JVC", "SET + 2", "006", "NEC", 0x40),
        p("philips", "PHILIPS (RC6)", "SET + 7", "007", "RC6", 0x00),
        p("philips_rc5", "PHILIPS (RC5)", "SET + 7", "018", "RC5", 0x00),
        nx("haier", "HAIER", "SET + 5 / RETURN", "008", 0xEF, 0x10),
        nx("hitachi", "HITACHI & SANYO", "SET + VOL+", "009", 0x50, 0xAF, core(0x08, 0x02, 0x03, 0x00, 0x01)),
        nx("tcl_konka", "TCL & KONKA", "SET + 3", "010", 0x01, 0xFE),
        nx("nobel", "NOBEL", "SET + SLEEP", "011", 0x00, 0xBF),
        nx("shownic_hisense", "SHOWNIC / HISENSE", "SET + 4 / SOURCE", "012", 0x20, 0xDF),
        nx("vestel", "VESTEL", "SET + MENU", "013", 0x00, 0xFF, core(0x0C, 0x10, 0x11, 0x20, 0x21)),
        nx("aoc", "AOC", "SET + INFO", "014", 0x1F, 0xE0),
        nx("changhong", "CHANGHONG", "SET + CH+", "015", 0x7F, 0x80),
        nx("skyworth", "SKYWORTH", "SET + CH-", "016", 0xBF, 0x40),
        nx("mystery", "MYSTERY", "SET + EXIT", "017", 0x00, 0xDF),
        // Placas chinas OEM más comunes (CX-507 / CV9203 / MStar / Realtek)
        nx("oem_cx507_1", "CX-507 (OEM 1)", "SET + 0", "020", 0x00, 0xBF),
        nx("oem_cx507_3", "CX-507 (OEM 3 / MStar)", "SET + CH+", "025", 0x40, 0xBF, core(0x48, 0x44, 0x45, 0x42, 0x43)),
        nx("oem_cx507_4", "CV9203 / TP.MS338", "SET + CH-", "036", 0x08, 0xF7),
        nx("oem_cx507_5", "TP.VST59 / Realtek", "SET + MENU", "038", 0x00, 0xFF, core(0x12, 0x1E, 0x1F, 0x1A, 0x1B))
    )
}

/**
 * Librerías universales por tipo de equipo, como la memoria del RM-014S+ (números 101+).
 * [powerKey] = la tecla que define cada código (en aires, el apagado: es la única que todos tienen).
 * - tv.ir / audio.ir / ac.ir: librerías universales de flipperzero-firmware (GPLv3).
 * - tv_extra.ir / audio_extra.ir: IRDB + Flipper-IRDB sin repetir (tools/build_tv_extra.py).
 * - audio_cn.ir: mando chino de 21 teclas de las cornetas Bluetooth genéricas.
 */
enum class Device(val files: List<String>, val label: String, val powerKey: String) {
    TV(listOf("tv.ir", "tv_extra.ir"), "TV", "Power"),
    AUDIO(listOf("audio_cn.ir", "audio.ir", "audio_extra.ir"), "AUDIO", "Power"),
    AC(listOf("ac.ir"), "AIRE", "Off")
}

/** Cada código de encendido distinto es un "código" seleccionable. */
object RemoteLibrary {
    const val FIRST_CODE = 101
    private val cache = HashMap<Device, List<RemoteProfile>>()

    fun codes(context: Context, device: Device = Device.TV): List<RemoteProfile> = cache.getOrPut(device) {
        device.files.fold(emptyList()) { acc, file ->
            acc + parse(context.assets.open(file).bufferedReader().readText(), FIRST_CODE + acc.size, device)
        }
    }

    fun parse(text: String, firstCode: Int = FIRST_CODE, device: Device = Device.TV): List<RemoteProfile> {
        class Entry(val name: String, val brand: String?, val group: Int, val signal: IrSignal)
        // "# Model: X" separa mandos en las librerías de audio/aire de Flipper (group 0 = sin separadores)
        var group = 0
        val models = HashMap<Int, String>()
        val signals = text.split("\n#").mapNotNull { block ->
            val f = block.lines().mapNotNull { l -> l.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
            f["Model"]?.let { models[++group] = it; return@mapNotNull null }
            val name = f["name"] ?: return@mapNotNull null
            Entry(name, f["brand"] ?: models[group], group, when (f["type"]) {
                "raw" -> IrSignal("RAW", rawFrequency = f["frequency"]!!.toInt(),
                    raw = f["data"]!!.split(" ").filter { it.isNotBlank() }.map { it.toInt() }.toIntArray())
                else -> IrSignal(f["protocol"]!!, leHex(f["address"]!!), leHex(f["command"]!!))
            })
        }
        // Teclas por (protocolo, dirección): agrupa todas las señales de un mismo mando
        val byRemote = HashMap<Pair<String, Long>, MutableMap<String, IrSignal>>()
        signals.filter { !it.signal.isRaw }.forEach { e -> byRemote.getOrPut(e.signal.protocol to e.signal.address) { HashMap() }.putIfAbsent(e.name, e.signal) }

        val seen = HashSet<String>()
        val result = ArrayList<RemoteProfile>()
        signals.forEachIndexed { i, e ->
            val s = e.signal
            val power = device.powerKey
            if (e.name != power || !seen.add(s.describe() + s.raw?.contentHashCode())) return@forEachIndexed
            // Teclas del mismo mando: todo su grupo "# Model", o lo que sigue a este encendido (tienen prioridad)
            val block = if (e.group > 0) signals.filter { it.group == e.group }.reversed().associate { it.name to it.signal }
                else signals.drop(i + 1).takeWhile { it.name != power }.associate { it.name to it.signal }
            val keys = (if (s.isRaw) emptyMap() else byRemote[s.protocol to s.address]!!) + block + (power to s)
            val code = (firstCode + result.size).toString()
            val brand = e.brand?.let { "$it • " } ?: ""
            val id = if (device == Device.TV) "lib:$code" else "${device.name.lowercase()}:$code"
            result.add(RemoteProfile(id, "${device.label} #$code • $brand${s.describe()}", "UNIVERSAL", code, s, keys))
        }
        return result
    }

    /** "01 00 20 00" → 0x00200001 (little-endian, como lo guarda Flipper). */
    private fun leHex(s: String) = s.split(" ").filter { it.isNotBlank() }
        .foldIndexed(0L) { i, acc, b -> acc or (b.toLong(16) shl (8 * i)) }

    fun find(context: Context, id: String): RemoteProfile? {
        RemoteConstants.PROFILES.firstOrNull { it.id == id }?.let { return it }
        codes(context).firstOrNull { it.id == id }?.let { return it }
        val parts = id.split(":")
        return when {
            parts[0] == "nec" && parts.size == 2 -> parts[1].toLongOrNull(16)?.let { necSweep(it) }
            parts[0] == "deep" && parts.size == 4 -> {
                val a = parts[2].toLongOrNull(16) ?: return null
                val c = parts[3].toLongOrNull(16) ?: return null
                deepProfile(context, IrSignal(parts[1], a, c))
            }
            else -> null
        }
    }

    fun necSweep(address: Long) = String.format("0x%02X", address).let {
        RemoteProfile("nec:%02X".format(address), "NEC $it", "SCAN", it, IrSignal("NEC", address))
    }

    /**
     * Código hallado por la búsqueda profunda: POWER exacto y, si algún mando de la librería
     * comparte protocolo+dirección (misma placa), el resto de sus teclas.
     */
    fun deepProfile(context: Context, power: IrSignal): RemoteProfile {
        val twin = codes(context).firstOrNull { !it.base.isRaw && it.base.protocol == power.protocol && it.base.address == power.address }
        val hex = if (power.protocol == "NEC") "%02X".format(power.address) else "%04X".format(power.address)
        val cmd = power.command and 0xFF
        return RemoteProfile(
            "deep:${power.protocol}:%X:%X".format(power.address, power.command),
            "${power.protocol} 0x$hex • POWER 0x%02X".format(cmd), "SCAN", "$hex-%02X".format(cmd),
            power.copy(command = 0), (twin?.keys ?: emptyMap()) + ("Power" to power)
        )
    }

    /**
     * Búsqueda profunda para TVs chinas sin código conocido: cada lote = una dirección NEC/NECext
     * con N comandos POWER, ordenados por frecuencia real en IRDB/Flipper (assets/nec_scan.txt).
     * Primero los 8 comandos más comunes en todas las direcciones, luego los 16 siguientes.
     */
    fun deepBatches(context: Context): List<List<IrSignal>> {
        val t = context.assets.open("nec_scan.txt").bufferedReader().readLines()
            .associate { l -> l.substringBefore(":") to l.substringAfter(":").trim().split(" ").map { it.toLong(16) } }
        val power = t.getValue("POWER")
        val addrs = t.getValue("NEC").map { "NEC" to it } + t.getValue("NECext").map { "NECext" to it }
        return listOf(power.take(8), power.drop(8)).flatMap { cmds ->
            addrs.map { (proto, a) ->
                cmds.map { c -> IrSignal(proto, a, if (proto == "NECext") c or ((c.inv() and 0xFF) shl 8) else c) }
            }
        }
    }
}
