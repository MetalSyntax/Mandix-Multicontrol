package com.example.rm014s

data class BrandPreset(
    val name: String,
    val description: String,
    val defaultAddress: Int
)

object RemoteConstants {
    // Comandos NEC estándar para placas chinas OEM y RM-014S+
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

    val PRESETS = listOf(
        BrandPreset("Genérico / CX-507 (0x00)", "Placa china genérica / Premium / Challenger", 0x00),
        BrandPreset("Genérico / CX-507 (0x04)", "Chasis MStar / Realtek / TP.VST59", 0x04),
        BrandPreset("Genérico / CX-507 (0x40)", "Chasis CV9203 / TP.MS338", 0x40),
        BrandPreset("Genérico / CX-507 (0x08)", "Variante chasis universal", 0x08),
        BrandPreset("Genérico / CX-507 (0x80)", "Variante chasis universal", 0x80),
        BrandPreset("Samsung TV (0x07)", "Protocolo NEC / Samsung 38kHz", 0x07),
        BrandPreset("LG TV (0x04)", "Protocolo NEC LG 38kHz", 0x04),
        BrandPreset("TCL / RCA (0x01)", "Chasis TCL Thomson", 0x01),
        BrandPreset("Hisense (0x20)", "Chasis Hisense Smart TV", 0x20),
        BrandPreset("Konka (0x02)", "Chasis Konka LCD/LED", 0x02),
        BrandPreset("Skyworth (0xBF)", "Chasis Skyworth OEM", 0xBF),
        BrandPreset("Changhong (0x7F)", "Chasis Changhong / CHiQ", 0x7F),
        BrandPreset("Haier (0xEF)", "Chasis Haier TV", 0xEF)
    )
}
