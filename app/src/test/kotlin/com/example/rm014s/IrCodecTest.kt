package com.example.rm014s

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IrCodecTest {
    private fun IrPattern.allOdd() = frames.all { it.pattern.size % 2 == 1 }

    @Test fun necLgPower() {
        val p = IrCodec.encode(IrSignal("NEC", 0x04, 0x08))
        assertEquals(38000, p.frequency)
        val frame = p.frames[0].pattern
        assertEquals(9000, frame[0]); assertEquals(4500, frame[1])
        // Primer byte 0x04 LSB-first: 0,0,1 → tercer bit largo
        assertEquals(560, frame[3]); assertEquals(560, frame[5]); assertEquals(1690, frame[7])
        assertEquals(67, frame.size)
        // Trama + silencio = periodo NEC de 110 ms; luego el código de repetición en su propio transmit
        assertEquals(110000, frame.sum() + p.frames[0].gapUs)
        assertEquals(listOf(9000, 2250, 560), p.frames[1].pattern.toList())
        assertTrue(p.allOdd())
    }

    @Test fun samsungHeader() {
        val p = IrCodec.encode(IrSignal("Samsung32", 0x07, 0x02))
        assertEquals(4500, p.frames[0].pattern[0]); assertEquals(4500, p.frames[0].pattern[1]); assertEquals(67, p.frames[0].pattern.size)
    }

    @Test fun sonyThreeFrames() {
        val p = IrCodec.encode(IrSignal("SIRC", 0x01, 0x15))
        assertEquals(40000, p.frequency)
        assertEquals(3, p.frames.size)
        p.frames.forEach { assertEquals(25, it.pattern.size); assertEquals(45000, it.pattern.sum() + it.gapUs) }
    }

    @Test fun rc5AndRc6AreManchester() {
        val rc5 = IrCodec.encode(IrSignal("RC5", 0x00, 0x0C)).frames.single().pattern
        assertTrue(rc5.all { it % 889 == 0 })
        assertTrue(rc5.sum() in (26 * 889)..(27 * 889))
        assertEquals(2666, IrCodec.encode(IrSignal("RC6", 0x00, 0x0C)).frames[0].pattern[0])
    }

    @Test fun newProtocolsEncode() {
        listOf(IrSignal("NECx", 0x0E0E, 0x0C), IrSignal("Panasonic", 0x0080, 0x3D), IrSignal("JVC", 0x03, 0x17))
            .forEach { assertTrue(it.protocol, IrCodec.encode(it).allOdd()) }
    }

    @Test fun rawSplitsOnLongGaps() {
        val p = IrCodec.encode(IrSignal("RAW", rawFrequency = 38000, raw = intArrayOf(9000, 4500, 560, 40000, 9000, 2250, 560, 30000)))
        assertEquals(2, p.frames.size)
        assertEquals(listOf(9000, 4500, 560), p.frames[0].pattern.toList())
        assertEquals(40000, p.frames[0].gapUs)
    }

    @Test fun librariesParse() {
        val base = RemoteLibrary.parse(File("src/main/assets/tv.ir").readText())
        val extra = RemoteLibrary.parse(File("src/main/assets/tv_extra.ir").readText(), RemoteLibrary.FIRST_CODE + base.size)
        assertTrue("codes=${base.size}", base.size > 250)
        assertTrue("extra=${extra.size}", extra.isNotEmpty())
        assertEquals((base + extra).size, (base + extra).map { it.codeNumber }.toSet().size)
        // Todo código debe codificarse en tramas de longitud impar (LIRC)
        (base + extra).forEach { c -> c.keys.values.forEach { assertTrue(it.describe(), IrCodec.encode(it).allOdd()) } }
        val lg = base.first { it.base.protocol == "NEC" && it.base.address == 0x04L }
        assertEquals(0x02L, lg.signal("Vol_up")!!.command)
        assertEquals(0x14L, lg.signal("4")!!.command)
        assertTrue(extra.first().name.contains("•"))
    }

    @Test fun deepScanTable() {
        val t = File("src/main/assets/nec_scan.txt").readLines().associate { it.substringBefore(":") to it.substringAfter(":").trim().split(" ") }
        assertEquals(256, t.getValue("NEC").toSet().size)
        assertEquals(24, t.getValue("POWER").size)
        assertTrue(t.getValue("NECext").size > 100)
    }

    @Test fun presetsEncode() {
        RemoteConstants.PROFILES.forEach { p ->
            listOf("Power", "Vol_up", "Vol_dn", "Ch_next", "Mute").forEach { k -> p.signal(k)?.let { IrCodec.encode(it) } }
            assertTrue(p.name, p.signal("Power") != null)
        }
        assertEquals(RemoteConstants.PROFILES.size, RemoteConstants.PROFILES.map { it.codeNumber }.toSet().size)
    }

    @Test fun altModeRepeatsAndInverts() {
        val nec = IrCodec.encode(IrSignal("NEC", 0x04, 0x08)).frames
        val rep = IrManagerHelper.withRepeats(nec)
        assertEquals(3, rep.size)
        assertEquals(listOf(9000, 2250, 560), rep[2].pattern.toList())
        assertEquals(110000, rep[1].pattern.sum() + rep[1].gapUs)
        assertEquals(3, IrManagerHelper.withRepeats(nec.take(1)).size)
        val inv = IrManagerHelper.invertForDriver(nec[0].pattern)
        assertEquals(9000, inv[1]); assertTrue(inv.size % 2 == 1)
    }

    // Vectores de los tests de IRremoteESP8266 (estado inicial de cada clase)
    @Test fun acGreeReset() {
        val b = AcCodec.greeBytes(AcState(power = false, mode = AcMode.AUTO, temp = 25, fan = 0))
        assertEquals(listOf(0x00, 0x09, 0x20, 0x50, 0x00, 0x20, 0x00, 0x50), b.toList())
    }

    @Test fun acCoolixDefault() {
        assertEquals(0xB21FC8, AcCodec.coolixWord(AcState(mode = AcMode.AUTO, temp = 25)))
        assertEquals(0xB27BE0, AcCodec.coolixWord(AcState(power = false)))
    }

    @Test fun acMideaChecksum() {
        assertEquals(0x62, AcCodec.mideaChecksum(0xA1826FFFFF00))
        val raw = AcCodec.encode("MIDEA", AcState()).raw!!
        assertTrue(raw.size % 2 == 1)
        assertEquals(1, IrCodec.encode(AcCodec.encode("COOLIX", AcState())).frames.size)
    }

    @Test fun audioAndAcLibraries() {
        val cn = RemoteLibrary.parse(File("src/main/assets/audio_cn.ir").readText(), device = Device.AUDIO)
        assertEquals(1, cn.size); assertEquals(0x46L, cn[0].keys["Mode"]!!.command)
        val audio = RemoteLibrary.parse(File("src/main/assets/audio.ir").readText(), device = Device.AUDIO)
        assertTrue(audio.size > 50)
        val ac = RemoteLibrary.parse(File("src/main/assets/ac.ir").readText(), device = Device.AC)
        assertTrue(ac.size > 100)
        // Electrolux EACM-16 (primer modelo de ac.ir): sus 6 estados en un solo código
        assertEquals(setOf("Off", "Dh", "Cool_hi", "Cool_lo", "Heat_hi", "Heat_lo"), ac[0].keys.keys)
        assertTrue(ac[0].name.contains("Electrolux"))
    }

    // Descubierto probando en la corneta de la habitación (23-oct-2026): estos números deben seguir apuntando a esos mandos
    @Test fun cornetaHabLearnSources() {
        var lib = emptyList<RemoteProfile>()
        Device.AUDIO.files.forEach { lib = lib + RemoteLibrary.parse(File("src/main/assets/$it").readText(), RemoteLibrary.FIRST_CODE + lib.size, Device.AUDIO) }
        fun code(n: String) = lib.first { it.codeNumber == n }
        assertEquals(IrSignal("NEC", 0x00, 0x16), code("104").keys["Vol_dn"])
        assertEquals(IrSignal("NEC", 0x80, 0x06), code("165").keys["Vol_dn"])
        assertEquals(IrSignal("NEC", 0x78, 0xCC), code("185").keys["Power"])
        val custom = SkinRemotes.get("corneta_hab").builtIn[0].keys
        assertEquals(code("165").keys["Vol_dn"], custom["Vol_up"])
        assertEquals(IrManagerHelper.Mode.ALT, SkinRemotes.get("aire_hab").txMode)
    }

    // Flipper-IRDB TVs/Hisense/Hisense_RokuTV.ir: "address: 86 05", "V_down command: 0D F2"
    @Test fun hisenseRokuTv() {
        val hisense = SkinRemotes.get("roku").builtIn.first()
        assertEquals(IrSignal("NECext", 0x0586, 0xF20D), hisense.keys["Vol_dn"])
        assertEquals(IrSignal("NEC", 0x04, 0x09), hisense.keys["Mute"])
        assertEquals("key:Vol_dn", SkinRemotes.get("amazon").autoProbe)
    }

    // Flipper-IRDB LED_Lighting/Unknown/RGB.ir ("On: 00 EF / 03 FC") y Tasmota MagicHome (0xFFF807 = apagar)
    @Test fun ledRemotes() {
        val generic = SkinRemotes.get("luz_generica").builtIn
        assertEquals(IrSignal("NECext", 0xEF00, 0xFC03), generic[0].keys["P3"])
        assertEquals(IrSignal("NEC", 0x00, 0x1F), generic[1].keys["P2"])
        val magic = SkinRemotes.get("magic_home").builtIn[0].keys
        assertEquals(IrSignal("NEC", 0x00, 0x0D), magic["On"])
        assertEquals(IrSignal("NEC", 0x00, 0x09), magic["BrightUp"])
    }
}
