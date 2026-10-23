package com.example.rm014s

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.example.rm014s.databinding.ViewCodeSelectorBinding
import com.example.rm014s.databinding.ViewDeviceTabsBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Pestañas: TV (MainActivity, siempre al fondo) + los controles de [SkinRemotes]. */
fun AppCompatActivity.bindDeviceTabs(tabs: ViewDeviceTabsBinding, currentId: String) {
    val density = resources.displayMetrics.density
    (listOf("tv" to "📺 TV") + SkinRemotes.ALL.map { it.id to it.title }).forEach { (id, title) ->
        val tab = AppCompatButton(this).apply {
            text = title
            textSize = 11f
            isAllCaps = false
            setTextColor(getColor(R.color.amber_accent))
            setBackgroundResource(R.drawable.btn_remote_dark_pill)
            minWidth = 0; minimumWidth = 0
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            alpha = if (id == currentId) 1f else 0.5f
            setOnClickListener {
                if (id == currentId) return@setOnClickListener
                if (id != "tv") startActivity(Intent(this@bindDeviceTabs, SkinRemoteActivity::class.java).putExtra(SkinRemoteActivity.EXTRA_REMOTE, id))
                if (this@bindDeviceTabs !is MainActivity) finish()
                @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            }
        }
        tabs.tabRow.addView(tab, ViewGroup.MarginLayoutParams((0).toInt(), (36 * density).toInt()).apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            marginEnd = (6 * density).toInt()
        })
        if (id == currentId) tabs.root.post { tabs.root.smoothScrollTo(tab.left - (40 * density).toInt(), 0) }
    }
}

/**
 * Un control de la casa: dibujo de [SkinRemote.layout]; ◀ ▶ recorren sus códigos (propios + librería)
 * como SET en el RM-014S+. Usa el modo de emisión elegido en la pantalla de TV.
 */
class SkinRemoteActivity : AppCompatActivity() {
    companion object { const val EXTRA_REMOTE = "remote" }

    private lateinit var spec: SkinRemote
    private lateinit var irHelper: IrManagerHelper
    private lateinit var codes: List<RemoteProfile>
    private lateinit var codeView: ViewCodeSelectorBinding
    private val prefs by lazy { getSharedPreferences("rm014s_prefs_v4", MODE_PRIVATE) }
    private var index = 0
    private var state = AcState()
    private var learnPool = emptyList<IrSignal>()
    private var autoJob: Job? = null
    private val wifiLink: WifiLink? by lazy {
        when (spec.wifi) { "roku" -> RokuEcp; "fire" -> FireAdb(File(filesDir, "adb")); else -> null }
    }
    private val wifiIp get() = prefs.getString("wifi_ip_${spec.id}", null) ?: spec.wifiIp
    private val wifiOn get() = wifiLink != null && prefs.getBoolean("wifi_on_${spec.id}", true)
    private val profile get() = codes[index]
    private val protocol get() = profile.base.protocol.takeIf { it in AcCodec.PROTOCOLS }
    private val tempRange get() = protocol?.let { AcCodec.tempRange(it) } ?: 16..30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spec = SkinRemotes.get(intent.getStringExtra(EXTRA_REMOTE))
        setContentView(spec.layout)
        irHelper = IrManagerHelper(this)
        // Modo de emisión: el elegido en este control, si no el probado para él, si no el de la pantalla de TV.
        // Sin nada elegido, ALTERNA: es el que funcionó en el teléfono del usuario (Tecno Camon 30, aire AIWA)
        irHelper.mode = (prefs.getString("tx_mode_${spec.id}", null) ?: spec.txMode?.name ?: prefs.getString("tx_mode", null))
            ?.let { m -> IrManagerHelper.Mode.values().firstOrNull { it.name == m } } ?: IrManagerHelper.Mode.ALT

        val acCodes = spec.acProtocols.map {
            val r = AcCodec.tempRange(it)
            RemoteProfile("acp:$it", "$it • ${r.first}-${r.last} °C", "PROTOCOLO", it, IrSignal(it))
        }
        val sweep = if (!spec.necSweep) emptyList() else RemoteLibrary.deepBatches(this).mapIndexed { i, batch ->
            val a = batch.first()
            RemoteProfile("sweep:$i", "BARRIDO ${a.protocol} 0x%X • ${batch.size} POWER".format(a.address), "BARRIDO", "S$i", a,
                batch.withIndex().associate { (j, sig) -> "Power$j" to sig })
        }
        codes = acCodes + spec.builtIn + (spec.device?.let { RemoteLibrary.codes(this, it) } ?: emptyList()) + sweep
        index = prefs.getInt("code_v2_${spec.id}", 0).coerceIn(0, codes.lastIndex)
        learnPool = ((spec.learnFrom.mapNotNull { n -> codes.firstOrNull { it.codeNumber == n } } + spec.builtIn)
            .flatMap { it.keys.values } + spec.learnExtra).distinctBy { it.describe() }
        state = loadState()

        bindDeviceTabs(ViewDeviceTabsBinding.bind(findViewById(R.id.tabs)), spec.id)
        codeView = ViewCodeSelectorBinding.bind(findViewById(R.id.code))
        codeView.btnCodePrev.setOnClickListener { step(-1) }
        codeView.btnCodeNext.setOnClickListener { step(1) }
        codeView.btnTxMode.setOnClickListener {
            val modes = IrManagerHelper.Mode.values()
            irHelper.mode = modes[(irHelper.mode.ordinal + 1) % modes.size]
            prefs.edit().putString("tx_mode_${spec.id}", irHelper.mode.name).apply()
            render()
        }
        if (wifiLink != null) {
            codeView.btnWifi.visibility = View.VISIBLE
            codeView.btnWifi.setOnClickListener { wifiDialog() }
        }
        if (spec.autoProbe != null) {
            codeView.btnAuto.visibility = View.VISIBLE
            codeView.btnAuto.setOnClickListener { toggleAuto() }
        }
        codeView.tvCodeName.setOnLongClickListener {
            if (!isCustom) return@setOnLongClickListener false
            prefs.edit().apply { prefs.all.keys.filter { it.startsWith("learn_${spec.id}_") }.forEach { remove(it) } }.apply()
            toast(R.string.toast_learn_reset)
            true
        }
        bindKeys(findViewById(android.R.id.content))
        render()
    }

    /** Conecta cada vista con tag "key:", "ac:" o "bt:". */
    private fun bindKeys(v: View) {
        (v.tag as? String)?.split(":", limit = 2)?.takeIf { it.size == 2 }?.let { (kind, name) ->
            v.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                when {
                    kind != "ac" && wifiOn && wifiLink!!.supports(name) -> pressWifi(name)
                    kind == "key" -> send(key(name))
                    kind == "ac" -> acAction(name)
                    else -> toast(R.string.toast_bluetooth_only)
                }
            }
            if (kind == "key") v.setOnLongClickListener { learn(name); true }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) bindKeys(v.getChildAt(i))
    }

    override fun onDestroy() {
        super.onDestroy()
        (wifiLink as? FireAdb)?.close()
    }

    private fun pressWifi(name: String) {
        val link = wifiLink ?: return
        if (link is FireAdb && !link.connected) toast(R.string.toast_fire_first_connect)
        lifecycleScope.launch {
            val led = findViewById<View>(R.id.viewLedIr)
            led?.setBackgroundResource(R.drawable.led_indicator_active)
            val error = link.press(wifiIp, name)
            led?.setBackgroundResource(R.drawable.led_indicator)
            when (error) {
                null -> {}
                "403" -> Toast.makeText(this@SkinRemoteActivity, R.string.toast_roku_limited, Toast.LENGTH_LONG).show()
                else -> Toast.makeText(this@SkinRemoteActivity, getString(R.string.toast_wifi_failed, wifiIp, error), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** IP del equipo y si se usa Wi-Fi o solo IR. */
    private fun wifiDialog() {
        val input = EditText(this).apply { setText(wifiIp); setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle(R.string.wifi_dialog_title)
            .setMessage(if (spec.wifi == "roku") R.string.wifi_dialog_roku else R.string.wifi_dialog_fire)
            .setView(input)
            .setPositiveButton(R.string.wifi_use) { _, _ ->
                prefs.edit().putString("wifi_ip_${spec.id}", input.text.toString().trim()).putBoolean("wifi_on_${spec.id}", true).apply()
                (wifiLink as? FireAdb)?.close()
                render()
            }
            .setNegativeButton(R.string.wifi_use_ir) { _, _ ->
                prefs.edit().putBoolean("wifi_on_${spec.id}", false).apply()
                render()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (autoJob != null) toggleAuto() // no seguir emitiendo con la pantalla cerrada
    }

    /**
     * AUTO: emite la prueba de cada código y avanza solo. Al detenerlo queda en el último código emitido,
     * que es el número que el usuario vio en pantalla cuando el equipo reaccionó.
     */
    private fun toggleAuto() {
        autoJob?.let {
            it.cancel()
            autoJob = null
            render()
            Toast.makeText(this, getString(R.string.toast_auto_stopped, index + 1), Toast.LENGTH_LONG).show()
            return
        }
        autoJob = lifecycleScope.launch {
            while (isActive) {
                render()
                val signals = autoSignals()
                // Solo el estado completo de un aire va sin repetir; teclas y barrido NEC llevan sus repeticiones
                val pad = spec.autoProbe != "ac:on" || profile.id.startsWith("sweep:")
                signals.forEach { emit(it, padRepeats = pad) }
                delay(if (signals.isEmpty()) 30 else 1200) // códigos sin esa tecla se saltan
                step(1)
            }
        }
        render()
    }

    private fun autoSignals(): List<IrSignal> {
        val probe = spec.autoProbe ?: return emptyList()
        if (profile.id.startsWith("sweep:")) return profile.keys.values.toList()
        if (probe.startsWith("key:")) return listOfNotNull(key(probe.removePrefix("key:")))
        val on = state.copy(power = true)
        return listOfNotNull(protocol?.let { AcCodec.encode(it, on) }
            ?: AcCodec.libraryKey(on).firstNotNullOfOrNull { profile.keys[it] })
    }

    private fun step(delta: Int) {
        index = (index + delta + codes.size) % codes.size
        prefs.edit().putInt("code_v2_${spec.id}", index).apply()
        render()
    }

    private val isCustom get() = learnPool.isNotEmpty() && profile.id.startsWith("custom_")

    private fun learned(name: String) = prefs.getInt("learn_${spec.id}_$name", -1).takeIf { isCustom && it in learnPool.indices }

    /** Lo aprendido manda; luego MODE↔SOURCE y PLAY↔PAUSE se sustituyen entre sí (muchos mandos tienen solo una). */
    private fun key(name: String): IrSignal? = learned(name)?.let { learnPool[it] }
        ?: (if (name == "OnOff") toggleOnOff() else null)
        ?: profile.keys[name] ?: ALIASES[name]?.let { profile.keys[it] }

    /** Tecla única ON/OFF en mandos que solo tienen On y Off por separado: alterna entre las dos. */
    private fun toggleOnOff(): IrSignal? {
        val on = !prefs.getBoolean("onoff_${spec.id}", false)
        prefs.edit().putBoolean("onoff_${spec.id}", on).apply()
        return profile.keys[if (on) "On" else "Off"]
    }

    /** Modo aprender: asigna a [name] la siguiente señal candidata y la emite para probarla en el equipo. */
    private fun learn(name: String) {
        if (!isCustom) return toast(R.string.toast_learn_only_custom)
        val next = ((learned(name) ?: -1) + 1) % learnPool.size
        prefs.edit().putInt("learn_${spec.id}_$name", next).apply()
        Toast.makeText(this, getString(R.string.toast_learned, name, next + 1, learnPool.size, learnPool[next].describe()), Toast.LENGTH_SHORT).show()
        send(learnPool[next])
    }

    private fun acAction(action: String) {
        val p = protocol
        when (action) {
            "power" -> applyAc(state.copy(power = !state.power))
            "temp_up" -> applyAc(state.copy(power = true, temp = state.temp + 1))
            "temp_down" -> applyAc(state.copy(power = true, temp = state.temp - 1))
            "mode" -> applyAc(state.copy(power = true, mode = AcMode.values()[(state.mode.ordinal + 1) % AcMode.values().size]))
            "fan" -> applyAc(state.copy(power = true, fan = (state.fan + 1) % 4))
            "resend" -> applyAc(state)
            "swing" -> if (p == "GREE") applyAc(state.copy(swing = !state.swing)) else sendCommand(p, action)
            "sleep" -> if (p == "GREE" || p == "MIDEA") applyAc(state.copy(sleep = !state.sleep)) else sendCommand(p, action)
            else -> sendCommand(p, action)
        }
    }

    private fun sendCommand(p: String?, action: String) {
        val signal = p?.let { AcCodec.command(it, action) }
        if (signal == null) toast(R.string.toast_ac_unsupported) else send(signal, padRepeats = false)
    }

    private fun applyAc(s: AcState) {
        state = s.copy(temp = s.temp.coerceIn(tempRange))
        prefs.edit().putString("ac_${spec.id}",
            listOf(state.power, state.mode.name, state.temp, state.fan, state.swing, state.sleep).joinToString(",")).apply()
        render()
        send(protocol?.let { AcCodec.encode(it, state) }
            ?: AcCodec.libraryKey(state).firstNotNullOfOrNull { profile.keys[it] }, padRepeats = false)
    }

    private fun loadState(): AcState {
        val f = prefs.getString("ac_${spec.id}", null)?.split(",") ?: return AcState()
        return AcState(f[0].toBoolean(), AcMode.values().firstOrNull { it.name == f.getOrNull(1) } ?: AcMode.COOL,
            f.getOrNull(2)?.toIntOrNull() ?: 24, f.getOrNull(3)?.toIntOrNull() ?: 0,
            f.getOrNull(4).toBoolean(), f.getOrNull(5).toBoolean())
    }

    private fun render() {
        codeView.tvCodeName.text = "${index + 1}/${codes.size}\n${profile.name}"
        codeView.btnWifi.text = if (wifiOn) getString(R.string.btn_wifi_on, wifiIp) else getString(R.string.btn_wifi_off)
        codeView.btnAuto.text = if (autoJob != null) getString(R.string.btn_auto_running, index + 1) else getString(R.string.btn_auto)
        codeView.btnTxMode.setText(when (irHelper.mode) {
            IrManagerHelper.Mode.ORIGINAL -> R.string.btn_mode_original
            IrManagerHelper.Mode.ALT -> R.string.btn_mode_alt
            IrManagerHelper.Mode.ALT_INV -> R.string.btn_mode_alt_inv
        })
        val t = state.temp.coerceIn(tempRange)
        findViewById<TextView>(R.id.tvTemp)?.text = when {
            !state.power -> "OFF"
            state.mode == AcMode.FAN -> "--"
            else -> "$t°C"
        }
        findViewById<TextView>(R.id.tvAcInfo)?.text = buildString {
            append(getString(R.string.ac_info, resources.getStringArray(R.array.ac_modes)[state.mode.ordinal],
                resources.getStringArray(R.array.ac_fans)[state.fan]))
            if (state.sleep) append(" ☾")
            if (protocol == null) append("\n≈ ${AcCodec.libraryKey(state.copy(temp = t)).first()}")
        }
    }

    private fun send(signal: IrSignal?, padRepeats: Boolean = true) {
        if (signal == null) return toast(R.string.toast_code_key_missing)
        lifecycleScope.launch { emit(signal, padRepeats) }
    }

    /** Emite con el LED del dibujo encendido. */
    private suspend fun emit(signal: IrSignal, padRepeats: Boolean) {
        val led = findViewById<View>(R.id.viewLedIr)
        led?.setBackgroundResource(R.drawable.led_indicator_active)
        if (!irHelper.transmit(signal, padRepeats = padRepeats)) toast(R.string.toast_send_failed)
        delay(120)
        led?.setBackgroundResource(R.drawable.led_indicator)
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private val ALIASES = mapOf("Mode" to "Source", "Source" to "Mode", "Play" to "Pause", "Pause" to "Play")
}
