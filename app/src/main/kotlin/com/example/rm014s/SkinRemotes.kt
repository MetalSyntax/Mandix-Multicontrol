package com.example.rm014s

/**
 * Los controles de la casa, dibujados como los físicos (layouts remote_*.xml).
 * Cada tecla del layout lleva un tag: "key:Nombre" (tecla IR), "ac:acción" (aire) o "bt:Nombre" (Bluetooth, sin IR).
 * [builtIn] = códigos propios que van antes de la librería de [device].
 * [txMode] = modo de emisión por defecto de este control (probado en el equipo real).
 * [learnFrom] = códigos de librería cuyas señales (más las de [builtIn]) son candidatas del modo aprender:
 *   manteniendo presionada una tecla del código personalizado (el primero) se le asigna la siguiente candidata.
 * [autoProbe] = prueba del modo AUTO, que recorre todos los códigos solo: "key:Tecla" o "ac:on" (encender el aire).
 * [necSweep] = al final de la lista, la búsqueda profunda NEC (mandos de una orden por tecla, sin código conocido).
 * [learnExtra] = más candidatas del modo aprender (tablas conocidas de mandos de la misma familia).
 * [wifi] = "roku" (ECP) o "fire" (ADB): con Wi-Fi activo, las teclas que soporta van por red y el resto por IR.
 */
class SkinRemote(
    val id: String, val title: String, val layout: Int, val device: Device?,
    val builtIn: List<RemoteProfile> = emptyList(), val acProtocols: List<String> = emptyList(),
    val txMode: IrManagerHelper.Mode? = null, val learnFrom: List<String> = emptyList(),
    val autoProbe: String? = null, val necSweep: Boolean = false, val learnExtra: List<IrSignal> = emptyList(),
    val wifi: String? = null, val wifiIp: String = ""
)

object SkinRemotes {
    private fun profile(id: String, name: String, keys: Map<String, IrSignal>) =
        RemoteProfile(id, name, "PROPIO", id, keys.getValue(keys.keys.first()), keys)

    private fun nec(address: Long, keys: Map<String, Int>) = keys.mapValues { IrSignal("NEC", address, it.value.toLong()) }

    /** Roku (Flipper-IRDB Streaming_Devices/Roku): NECext, el byte alto del comando es el inverso. */
    private fun roku(address: Long, keys: Map<String, Int>) =
        keys.mapValues { (_, c) -> IrSignal("NECext", address, (c or ((c.inv() and 0xFF) shl 8)).toLong()) }

    private val ROKU_KEYS = mapOf(
        "Power" to 0x17, "Home" to 0x03, "Back" to 0x66, "Up" to 0x19, "Down" to 0x33, "Left" to 0x1E, "Right" to 0x2D,
        "Ok" to 0x2A, "Replay" to 0x78, "Options" to 0x61, "Rew" to 0x34, "Play" to 0x4C, "Ffwd" to 0x55,
        "Vol_up" to 0x0F, "Vol_dn" to 0x10, "Mute" to 0x20, "Netflix" to 0x4B, "Disney" to 0x11, "Hulu" to 0x4D, "Sling" to 0x27
    )

    /** Mando chino de 21 teclas: mismos códigos por posición que el de los módulos MP3 (NEC 0x00). */
    private val MP3_21 = mapOf(
        "Power" to 0x45, "Stop" to 0x46, "Mute" to 0x47, "Mode" to 0x44, "Return" to 0x40, "Eq" to 0x43,
        "Prev" to 0x07, "Next" to 0x15, "Play" to 0x09, "Vol_dn" to 0x16, "Vol_up" to 0x19, "0" to 0x0D,
        "1" to 0x0C, "2" to 0x18, "3" to 0x5E, "4" to 0x08, "5" to 0x1C, "6" to 0x5A, "7" to 0x42, "8" to 0x52, "9" to 0x4A
    )

    private fun nec04(keys: Map<String, Int>) = nec(0x04, keys)

    /**
     * Roku TV (televisores con Roku integrado), de Flipper-IRDB TVs/: van antes que los Roku de caja.
     * Hisense: navegación en NECext 86 05 y el resto en NEC 0x04 (como Sharp Roku TV).
     */
    private val ROKU_TV = listOf(
        profile("rokutv_hisense", "Hisense Roku TV (NECext 86 05)", mapOf(
            "Power" to 0x0F, "Ok" to 0x18, "Up" to 0x42, "Down" to 0x43, "Left" to 0x16, "Right" to 0x15,
            "Vol_up" to 0x0C, "Vol_dn" to 0x0D, "Back" to 0x1B, "Home" to 0x14
        ).mapValues { (_, c) -> IrSignal("NECext", 0x0586, (c or ((c.inv() and 0xFF) shl 8)).toLong()) } + nec04(mapOf(
            "Mute" to 0x09, "Netflix" to 0x4C, "Hulu" to 0x5C, "Sleep" to 0x1A, "Options" to 0x0C, "Replay" to 0x41,
            "Ffwd" to 0x5B, "Rew" to 0x4F, "Play" to 0x42
        ))),
        profile("rokutv_nec04", "Roku TV (NEC 0x04, Sharp)", nec04(mapOf(
            "Power" to 0x08, "Back" to 0x04, "Home" to 0x43, "Vol_up" to 0x02, "Vol_dn" to 0x03, "Mute" to 0x09,
            "Up" to 0x56, "Down" to 0x57, "Left" to 0x58, "Right" to 0x59, "Ok" to 0x5A, "Replay" to 0x41, "Sleep" to 0x1A,
            "Options" to 0x0C, "Rew" to 0x4F, "Play" to 0x42, "Ffwd" to 0x5B, "Netflix" to 0x4C, "Hulu" to 0x5C, "Sling" to 0x45
        ))),
        profile("rokutv_tcl", "Roku TV TCL/Onn/RCA (EA C7, Power 0x97)",
            roku(0xC7EA, ROKU_KEYS + mapOf("Power" to 0x97, "Netflix" to 0x52, "Disney" to 0x0C, "Sleep" to 0x62, "Sling" to 0x86)))
    )

    /**
     * Tiras LED RGB. Mando blanco de 24 teclas: NECext 00 EF, comando = posición (fila×4 + columna), confirmado en
     * Flipper-IRDB LED_Lighting/Unknown/RGB.ir. Variante NEC 0x00 (controladores MagicHome, ver Tasmota): mismo
     * orden de teclas con la tabla clásica 0x00FF____ (comando = byte 3 con bits invertidos).
     */
    private val LED24_EF00 = (0 until 24).associate { "P$it" to IrSignal("NECext", 0xEF00, (it or ((it.inv() and 0xFF) shl 8)).toLong()) }
    private val LED24_NEC00 = listOf(0x09, 0x1D, 0x1F, 0x0D, 0x19, 0x1B, 0x11, 0x15, 0x17, 0x12, 0x16, 0x4D,
        0x40, 0x4C, 0x04, 0x00, 0x0A, 0x1E, 0x0E, 0x1A, 0x1C, 0x14, 0x0F, 0x0C)
        .withIndex().associate { (i, c) -> "P$i" to IrSignal("NEC", 0x00, c.toLong()) }

    /** Mando de 44 teclas (NEC 0x00), fila por fila: candidatas para el Magic Home (trae DIY R/G/B ±). */
    private val LED44_NEC00 = listOf(0x5C, 0x5D, 0x41, 0x40, 0x58, 0x59, 0x45, 0x44, 0x54, 0x55, 0x49, 0x48,
        0x50, 0x51, 0x4D, 0x4C, 0x1C, 0x1D, 0x1E, 0x1F, 0x18, 0x19, 0x1A, 0x1B, 0x14, 0x15, 0x16, 0x17,
        0x10, 0x11, 0x12, 0x13, 0x0C, 0x0D, 0x0E, 0x0F, 0x08, 0x09, 0x0A, 0x0B, 0x04, 0x05, 0x06, 0x07)
        .map { IrSignal("NEC", 0x00, it.toLong()) }

    /** Teclas del Magic Home de 28 teclas desde la tabla de 24 (P0 brillo+, P1 brillo−, P2 OFF, P3 ON, P4-P7 R G B W...). */
    private fun magic(t: Map<String, IrSignal>) = mapOf(
        "BrightUp" to "P0", "BrightDn" to "P1", "Off" to "P2", "On" to "P3", "R" to "P4", "G" to "P5", "B" to "P6", "W" to "P7",
        "Mode" to "P11", "C1" to "P8", "C2" to "P9", "C3" to "P10", "C4" to "P12", "C5" to "P13", "C6" to "P14", "C7" to "P16", "C8" to "P17"
    ).mapValues { t.getValue(it.value) }

    val ALL = listOf(
        // Fire TV recibe la navegación por Bluetooth; por IR solo Power/Vol/Mute, que el control manda al TV
        // Fire TV Stick 4K Max del usuario (AFTMM) con ADB por red en 192.168.3.19
        SkinRemote("amazon", "Amazon", R.layout.remote_amazon, Device.TV, autoProbe = "key:Vol_dn", wifi = "fire", wifiIp = "192.168.3.19", builtIn = listOf(
            profile("fire_nec", "Fire TV → TV (NEC 0x03)", nec(0x03, mapOf("Power" to 0x1D, "Vol_up" to 0x11, "Vol_dn" to 0x15)) +
                ("Mute" to IrSignal("NECext", 0x7F00, 0xB14E))),
            profile("fire_rc5", "Fire TV 2ª gen → TV (RC5)",
                mapOf("Power" to 0x0C, "Vol_up" to 0x10, "Vol_dn" to 0x11, "Mute" to 0x0D).mapValues { IrSignal("RC5", 0, it.value.toLong()) })
        ) + ROKU_TV),
        // El del usuario es un Hisense 43R6+ Roku TV
        SkinRemote("roku", "Roku", R.layout.remote_roku, null, autoProbe = "key:Vol_dn", wifi = "roku", wifiIp = "192.168.100.7", builtIn = ROKU_TV + listOf(
            profile("roku_c7", "Roku (NECext EA C7)", roku(0xC7EA, ROKU_KEYS)),
            profile("roku_c2", "Roku (NECext EA C2)", roku(0xC2EA, ROKU_KEYS))
        )),
        SkinRemote("corneta_sala", "Corneta Sala", R.layout.remote_corneta_sala, Device.AUDIO),
        // Mando chino de 21 teclas. Probado en la corneta: Vol− de AUDIO #104 baja, Vol− de #165 sube, #185 apaga.
        // El resto de las teclas sale del mapa por posición y se corrige con el modo aprender.
        SkinRemote("corneta_hab", "Corneta Hab.", R.layout.remote_corneta_hab, Device.AUDIO, listOf(
            profile("custom_corneta_hab", "PERSONALIZADO", nec(0x00, MP3_21) + mapOf(
                "Vol_dn" to IrSignal("NEC", 0x00, 0x16), "Vol_up" to IrSignal("NEC", 0x80, 0x06), "Power" to IrSignal("NEC", 0x78, 0xCC)
            )),
            profile("mp3_21", "21 teclas (NEC 0x00, por posición)", nec(0x00, MP3_21))
        ), learnFrom = listOf("104", "165", "185", "164", "184", "103")),
        // El mando AIWA es el Midea R51M: protocolo Coolix
        // Probado: Coolix en modo ALTERNA controla todo el aire
        SkinRemote("aire_hab", "Aire Hab.", R.layout.remote_aire_hab, Device.AC, acProtocols = listOf("COOLIX", "MIDEA", "GREE"),
            txMode = IrManagerHelper.Mode.ALT),
        // Damasco: ningún código de la lista sirvió → AUTO recorre protocolos, librería y luego la búsqueda profunda NEC
        SkinRemote("aire_sala", "Aire Sala", R.layout.remote_aire_sala, Device.AC, acProtocols = listOf("GREE", "COOLIX", "MIDEA"),
            autoProbe = "ac:on", necSweep = true),
        SkinRemote("luz_generica", "Luz Genérica", R.layout.remote_luz_generica, null, listOf(
            profile("led24_ef00", "24 teclas (NECext 00 EF)", LED24_EF00),
            profile("led24_nec00", "24 teclas (NEC 0x00)", LED24_NEC00)
        )),
        // Sin tabla publicada para este mando: base = tabla MagicHome NEC 0x00; ♫, DIY y 30 min se aprenden
        SkinRemote("magic_home", "Magic Home", R.layout.remote_magic_home, null, listOf(
            profile("custom_magic_home", "PERSONALIZADO (NEC 0x00)", magic(LED24_NEC00)),
            profile("magic_ef00", "Tabla 00 EF", magic(LED24_EF00))
        ), learnExtra = LED24_NEC00.values + LED44_NEC00 + LED24_EF00.values)
    )

    fun get(id: String?) = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
