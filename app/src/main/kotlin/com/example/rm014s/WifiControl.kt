package com.example.rm014s

import android.os.Build
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Control por red de equipos que no reciben (todas) sus teclas por IR. Las teclas usan los nombres de los layouts. */
interface WifiLink {
    fun supports(key: String): Boolean

    /** null = enviado; si no, el motivo del fallo para mostrar al usuario. */
    suspend fun press(ip: String, key: String): String?
}

/** Roku: External Control Protocol (HTTP, puerto 8060). Requiere "Control por apps móviles" en Permisivo. */
object RokuEcp : WifiLink {
    private val KEYS = mapOf(
        "Power" to "Power", "Home" to "Home", "Back" to "Back", "Up" to "Up", "Down" to "Down", "Left" to "Left",
        "Right" to "Right", "Ok" to "Select", "Replay" to "InstantReplay", "Options" to "Info", "Rew" to "Rev",
        "Play" to "Play", "Ffwd" to "Fwd", "Vol_up" to "VolumeUp", "Vol_dn" to "VolumeDown", "Mute" to "VolumeMute"
    )
    private val APPS = mapOf("Netflix" to "12", "Disney" to "291097", "Hulu" to "2285", "Sling" to "46041")

    override fun supports(key: String) = key in KEYS || key in APPS

    override suspend fun press(ip: String, key: String): String? = withContext(Dispatchers.IO) {
        val path = KEYS[key]?.let { "keypress/$it" } ?: "launch/${APPS.getValue(key)}"
        try {
            val c = URL("http://$ip:8060/$path").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 2500
            c.readTimeout = 4000
            val code = c.responseCode
            c.disconnect()
            when (code) {
                in 200..299 -> null
                403 -> "403"
                else -> "HTTP $code"
            }
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }
}

/**
 * Fire TV: ADB por red (puerto 5555). La primera vez el televisor pide aceptar la clave de Mandix
 * ("Permitir siempre"); la clave se guarda en [keyDir] para no volver a pedirla.
 */
class FireAdb(private val keyDir: File) : WifiLink {
    private val KEYCODES = mapOf(
        "Home" to 3, "Back" to 4, "Up" to 19, "Down" to 20, "Left" to 21, "Right" to 22, "Select" to 23,
        "Vol_up" to 24, "Vol_dn" to 25, "Power" to 26, "Alexa" to 84, "Menu" to 82, "Play" to 85, "Rew" to 89,
        "Ffwd" to 90, "Mute" to 164, "Guide" to 172
    )
    // Apps de Fire TV: solo se abren por la categoría LEANBACK_LAUNCHER (Prime Video no tiene LAUNCHER)
    private val APPS = mapOf(
        "Prime" to "com.amazon.firebat", "Netflix" to "com.netflix.ninja",
        "Disney" to "com.disney.disneyplus", "Hulu" to "com.hulu.plus"
    )
    private val mutex = Mutex()
    private var dadb: Dadb? = null
    private var dadbIp: String? = null

    override fun supports(key: String) = key in KEYCODES || key in APPS

    val connected get() = dadb != null

    override suspend fun press(ip: String, key: String): String? = withContext(Dispatchers.IO) {
        // AdbKeyPair usa java.util.Base64 (Android 8+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext "Android 8+"
        val cmd = KEYCODES[key]?.let { "input keyevent $it" }
            ?: "monkey -p ${APPS.getValue(key)} -c android.intent.category.LEANBACK_LAUNCHER 1"
        mutex.withLock {
            try {
                run(ip, cmd)
            } catch (e: Exception) {
                close() // conexión caída (TV dormido, cambio de IP): reconectar una vez
                try { run(ip, cmd) } catch (e2: Exception) { close(); return@withLock e2.message ?: e2.javaClass.simpleName }
            }
            null
        }
    }

    /** Un stream "shell:" por tecla sobre la misma conexión; se lee hasta el final para que el comando termine. */
    private fun run(ip: String, cmd: String) {
        val d = dadb?.takeIf { dadbIp == ip } ?: connect(ip)
        d.open("shell:$cmd").use { it.source.readUtf8() }
    }

    private fun connect(ip: String): Dadb {
        close()
        val priv = File(keyDir, "adbkey")
        val pub = File(keyDir, "adbkey.pub")
        if (!priv.exists()) AdbKeyPair.generate(priv, pub)
        return Dadb.create(ip, 5555, AdbKeyPair.read(priv, pub), connectTimeout = 3000).also { dadb = it; dadbIp = ip }
    }

    fun close() {
        try { dadb?.close() } catch (_: Exception) {}
        dadb = null
        dadbIp = null
    }
}
