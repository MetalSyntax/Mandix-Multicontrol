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
}
