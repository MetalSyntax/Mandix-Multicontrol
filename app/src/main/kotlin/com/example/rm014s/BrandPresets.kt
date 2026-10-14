package com.example.rm014s

data class RemoteProfile(
    val id: String,
    val name: String,
    val brandButtonLabel: String, // ej. "SET + 1"
    val codeNumber: String,       // ej. "001"
    val address: Int,
    val addressHigh: Int? = null,
    val frequency: Int = 38000,
    val cmdPower: Int = RemoteConstants.CMD_POWER,
    val cmdVolUp: Int = RemoteConstants.CMD_VOL_UP,
    val cmdVolDown: Int = RemoteConstants.CMD_VOL_DOWN,
    val cmdChUp: Int = RemoteConstants.CMD_CH_UP,
    val cmdChDown: Int = RemoteConstants.CMD_CH_DOWN,
    val cmdMute: Int = RemoteConstants.CMD_MUTE
)

object RemoteConstants {
    // Comandos base
    const val CMD_POWER = 0x12
    const val CMD_MUTE = 0x10

    const val CMD_NUM_1 = 0x01
    const val CMD_NUM_2 = 0x02
    const val CMD_NUM_3 = 0x03
    const val CMD_NUM_4 = 0x04
    const val CMD_NUM_5 = 0x05
    const val CMD_NUM_6 = 0x06
    const val CMD_NUM_7 = 0x07
    const val CMD_NUM_8 = 0x08
    const val CMD_NUM_9 = 0x09
    const val CMD_NUM_0 = 0x00
    const val CMD_CH_LIST = 0x14
    const val CMD_RETURN = 0x15

    const val CMD_P_MODE = 0x20
    const val CMD_GUIDE = 0x21
    const val CMD_S_MODE = 0x22

    const val CMD_MENU = 0x17
    const val CMD_SOURCE = 0x0B

    const val CMD_UP = 0x18
    const val CMD_DOWN = 0x52
    const val CMD_LEFT = 0x5A
    const val CMD_RIGHT = 0x5E
    const val CMD_OK = 0x16

    const val CMD_BACK = 0x13
    const val CMD_EXIT = 0x1C

    const val CMD_VOL_UP = 0x1A
    const val CMD_VOL_DOWN = 0x1E
    const val CMD_CH_UP = 0x1B
    const val CMD_CH_DOWN = 0x1F

    const val CMD_DISPLAY = 0x19
    const val CMD_SLEEP = 0x2A

    const val CMD_FAV = 0x2B
    const val CMD_INFO = 0x2C
    const val CMD_AUDIO = 0x2D
    const val CMD_HOME = 0x2E
    const val CMD_REC = 0x2F
    const val CMD_3D = 0x30
    const val CMD_SUB = 0x31

    const val CMD_REWIND = 0x34
    const val CMD_PLAY_PAUSE = 0x32
    const val CMD_STOP = 0x33
    const val CMD_FAST_FWD = 0x35

    const val CMD_COLOR_RED = 0x38
    const val CMD_COLOR_GREEN = 0x39
    const val CMD_COLOR_YELLOW = 0x3A
    const val CMD_COLOR_BLUE = 0x3B

    // Comandos alternativos comunes de placas chinas OEM (CX-507 / CV9203 / TP.VST59)
    val VOL_UP_CANDIDATES = intArrayOf(0x1A, 0x02, 0x1E, 0x44, 0x0B, 0x15, 0x46, 0x5B, 0x07, 0x10)
    val POWER_CANDIDATES = intArrayOf(0x12, 0x08, 0x02, 0x48, 0x14, 0x1A, 0x3C, 0x46, 0x01)
    val FREQUENCIES = intArrayOf(38000, 36000, 40000, 56000)

    // Perfiles oficiales de marcas y placas del manual RM-014S+
    val PROFILES = listOf(
        // Placas chinas OEM más comunes (CX-507 / CV9203 / MStar / Realtek)
        RemoteProfile("oem_cx507_1", "Placa China CX-507 (OEM 1)", "SET + 0", "014", 0x00, 0xBF, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("oem_cx507_2", "Placa China CX-507 (OEM 2)", "SET + VOL+", "005", 0x04, 0xFB, 38000, 0x08, 0x02, 0x03, 0x00, 0x01),
        RemoteProfile("oem_cx507_3", "Placa China CX-507 (OEM 3 / MStar)", "SET + CH+", "025", 0x40, 0xBF, 38000, 0x48, 0x44, 0x45, 0x42, 0x43),
        RemoteProfile("oem_cx507_4", "Placa China CV9203 / TP.MS338", "SET + CH-", "036", 0x08, 0xF7, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("oem_cx507_5", "Placa China TP.VST59 / Realtek", "SET + MENU", "038", 0x00, 0xFF, 38000, 0x12, 0x1E, 0x1F, 0x1A, 0x1B),
        RemoteProfile("oem_cx507_6", "Placa China 36kHz (Frecuencia 36kHz)", "SET + SLEEP", "045", 0x00, 0xBF, 36000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("oem_cx507_7", "Placa China 40kHz (Frecuencia 40kHz)", "SET + FAV", "056", 0x04, 0xFB, 40000, 0x08, 0x02, 0x03, 0x00, 0x01),

        // Perfiles oficiales de marcas del empaque RM-014S+
        RemoteProfile("lg", "LG", "SET + 1", "001", 0x04, 0xFB, 38000, 0x08, 0x02, 0x03, 0x00, 0x01),
        RemoteProfile("sony", "SONY", "SET + 2", "002", 0x01, null, 40000, 0x15, 0x12, 0x13, 0x10, 0x11),
        RemoteProfile("samsung", "SAMSUNG", "SET + 3", "003", 0x07, 0x07, 38000, 0x02, 0x07, 0x0B, 0x12, 0x10),
        RemoteProfile("sharp", "SHARP", "SET + 4", "004", 0x55, null, 38000, 0x14, 0x0A, 0x0B, 0x08, 0x09),
        RemoteProfile("panasonic", "PANASONIC", "SET + 5", "005", 0x08, 0xF7, 36700, 0x3C, 0x20, 0x21, 0x22, 0x23),
        RemoteProfile("toshiba", "TOSHIBA / JVC", "SET + 6", "006", 0x40, 0xBF, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("philips", "PHILIPS", "SET + 7", "007", 0x00, null, 36000, 0x0C, 0x10, 0x11, 0x20, 0x21),
        RemoteProfile("haier", "HAIER", "SET + 8", "008", 0xEF, 0x10, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("hitachi", "HITACHI & SANYO", "SET + 9", "009", 0x50, 0xAF, 38000, 0x08, 0x02, 0x03, 0x00, 0x01),
        RemoteProfile("tcl_konka", "TCL & KONKA", "SET + 0", "010", 0x01, 0xFE, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("nobel", "NOBEL", "SET + CH.LIST", "011", 0x00, 0xBF, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("shownic_hisense", "SHOWNIC / HISENSE", "SET + SOURCE", "012", 0x20, 0xDF, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("vestel", "VESTEL", "SET + MENU", "013", 0x00, 0xFF, 38000, 0x0C, 0x10, 0x11, 0x20, 0x21),
        RemoteProfile("aoc", "AOC", "SET + INFO", "014", 0x1F, 0xE0, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("changhong", "CHANGHONG", "SET + ▲", "015", 0x7F, 0x80, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("skyworth", "SKYWORTH", "SET + ▼", "016", 0xBF, 0x40, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F),
        RemoteProfile("mystery", "MYSTERY", "SET + EXIT", "017", 0x00, 0xDF, 38000, 0x12, 0x1A, 0x1E, 0x1B, 0x1F)
    )
}
