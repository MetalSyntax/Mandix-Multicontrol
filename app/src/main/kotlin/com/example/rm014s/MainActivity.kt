package com.example.rm014s

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rm014s.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var irHelper: IrManagerHelper
    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null

    // Código activo (marca del manual, código de la librería universal o dirección NEC barrida)
    private lateinit var profile: RemoteProfile
    // 0 = AUTO: cada protocolo usa su portadora (NEC 38k, Sony 40k, Philips 36k...)
    private var freqOverride: Int = 0

    // Variables de Auto-Scan
    private var scanJob: Job? = null
    private var isScanning = false
    private var isScanPaused = false
    private var scanIndex = 0
    // Pulsación larga (mantener VOL/CH repite como el mando físico)
    private var holdJob: Job? = null

    private val freqValues = intArrayOf(0, 36000, 38000, 40000, 56000)
    private fun freqLabels() = listOf(getString(R.string.freq_auto)) + freqValues.drop(1).map { "${it / 1000} kHz" }

    /** Nombre real del equipo donde corre la app (nunca hardcodeado). */
    private fun deviceName(): String {
        val manu = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: getString(R.string.unknown_device)
        return if (model.startsWith(manu, ignoreCase = true)) model else "$manu $model".trim()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irHelper = IrManagerHelper(this)
        prefs = getSharedPreferences("rm014s_prefs_v4", Context.MODE_PRIVATE)

        // Configuración moderna de Vibrator según la versión de Android del equipo en uso
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        loadSavedSettings()
        updateDisplayScreen()
        setupMainFrequencySelector()
        setupButtonListeners()
        bindDeviceTabs(binding.tabs, "tv")
        checkIrHardware()
    }

    /** Aviso si este equipo no tiene emisor IR. */
    private fun checkIrHardware() {
        binding.tvDeviceName.text = deviceName()
        if (!irHelper.hasIrEmitter) {
            Toast.makeText(this, getString(R.string.toast_no_ir, deviceName()), Toast.LENGTH_LONG).show()
        }
    }

    /** Selector de portadora: AUTO (por protocolo) o forzada para diagnóstico. */
    private fun setupMainFrequencySelector() {
        binding.spinnerMainFreq.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, freqLabels())
        binding.spinnerMainFreq.setSelection(freqValues.indexOf(freqOverride).coerceAtLeast(0), false)
        binding.spinnerMainFreq.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val freq = freqValues[position]
                if (freq == freqOverride) return
                freqOverride = freq
                prefs.edit().putInt("freq_override", freqOverride).apply()
                updateDisplayScreen()
                if (freq > 0 && !irHelper.isFrequencySupported(freq)) {
                    Toast.makeText(this@MainActivity,
                        getString(R.string.toast_freq_unsupported, deviceName(), irHelper.getHardwareInfo(this@MainActivity), freq / 1000),
                        Toast.LENGTH_LONG).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun txModeTexts() = when (irHelper.mode) {
        IrManagerHelper.Mode.ORIGINAL -> R.string.btn_mode_original to R.string.tx_mode_original
        IrManagerHelper.Mode.ALT -> R.string.btn_mode_alt to R.string.tx_mode_alt
        IrManagerHelper.Mode.ALT_INV -> R.string.btn_mode_alt_inv to R.string.tx_mode_alt_inv
    }

    private fun updateTxModeButton() { binding.btnTxMode.setText(txModeTexts().first) }

    private fun loadSavedSettings() {
        irHelper.mode = IrManagerHelper.Mode.values().firstOrNull { it.name == prefs.getString("tx_mode", null) }
            ?: IrManagerHelper.Mode.ORIGINAL
        updateTxModeButton()
        freqOverride = prefs.getInt("freq_override", 0)
        profile = prefs.getString("remote_id", null)?.let { RemoteLibrary.find(this, it) }
            ?: RemoteConstants.PROFILES.first()
    }

    private fun saveProfile(p: RemoteProfile) {
        profile = p
        prefs.edit().putString("remote_id", p.id).apply()
        updateDisplayScreen()
        flashLedThreeTimes()
    }

    private fun effectiveFrequency(p: RemoteProfile = profile) = if (freqOverride > 0) freqOverride else p.frequency

    private fun updateDisplayScreen() {
        val khz = effectiveFrequency() / 1000.0
        val khzText = if (khz % 1.0 == 0.0) "${khz.toInt()}" else "%.1f".format(khz)
        binding.tvDisplayFrequency.text = if (freqOverride > 0) "$khzText kHz" else "$khzText kHz AUTO"
        binding.tvActiveProfileName.text = "${profile.codeNumber} • ${profile.name}"
        if (::binding.isInitialized && binding.spinnerMainFreq.adapter != null) {
            val pos = freqValues.indexOf(freqOverride).coerceAtLeast(0)
            if (binding.spinnerMainFreq.selectedItemPosition != pos) binding.spinnerMainFreq.setSelection(pos, false)
        }
    }

    /**
     * Feedback Háptico Realista: simula la resistencia mecánica y clic de un control físico
     */
    private fun triggerMechanicalFeedback(view: View? = null) {
        view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(45, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45)
            }
        } catch (_: Exception) {}

        binding.viewLedIr.setBackgroundResource(R.drawable.led_indicator_active)
        binding.viewLedIr.postDelayed({
            binding.viewLedIr.setBackgroundResource(R.drawable.led_indicator)
        }, 110)
    }

    private fun flashLedThreeTimes() {
        lifecycleScope.launch {
            repeat(3) {
                binding.viewLedIr.setBackgroundResource(R.drawable.led_indicator_active)
                delay(120)
                binding.viewLedIr.setBackgroundResource(R.drawable.led_indicator)
                delay(120)
            }
        }
    }

    private fun sendKey(key: String, view: View? = null) {
        triggerMechanicalFeedback(view)
        val signal = profile.signal(key)
        if (signal == null) {
            Toast.makeText(this, getString(R.string.toast_key_missing, profile.codeNumber), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            if (!irHelper.transmit(signal, freqOverride)) {
                Toast.makeText(this@MainActivity, getString(R.string.toast_tx_failed, deviceName()), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Mantener presionado VOL±/CH± repite la tecla como el mando físico.
     * (POWER y MUTE no se repiten: encenderían y apagarían en bucle.)
     */
    private fun setupHoldRepeat(view: View, key: String) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    holdJob?.cancel()
                    holdJob = lifecycleScope.launch {
                        delay(450)
                        val signal = profile.signal(key) ?: return@launch
                        while (true) {
                            irHelper.transmit(signal, freqOverride)
                            delay(60)
                        }
                    }
                    v.isPressed = true
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    holdJob?.cancel()
                    v.isPressed = false
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtonListeners() {
        binding.layoutOledScreen.setOnClickListener { v ->
            triggerMechanicalFeedback(v)
            showBrandSelectorDialog()
        }
        binding.btnPlateSettings.setOnClickListener { v ->
            triggerMechanicalFeedback(v)
            showAutoScanDialog()
        }
        binding.btnPlateManual.setOnClickListener { v ->
            triggerMechanicalFeedback(v)
            showManualDialog()
        }
        binding.btnTxMode.setOnClickListener { v ->
            triggerMechanicalFeedback(v)
            val modes = IrManagerHelper.Mode.values()
            irHelper.mode = modes[(irHelper.mode.ordinal + 1) % modes.size]
            prefs.edit().putString("tx_mode", irHelper.mode.name).apply()
            updateTxModeButton()
            val (label, desc) = txModeTexts()
            Toast.makeText(this, getString(R.string.toast_tx_mode, getString(label), getString(desc)), Toast.LENGTH_SHORT).show()
        }
        binding.btnSet.setOnClickListener { v ->
            triggerMechanicalFeedback(v)
            showAutoScanDialog()
        }

        val keys = mapOf(
            binding.btnPower to "Power", binding.btnMute to "Mute",
            binding.btnNum1 to "1", binding.btnNum2 to "2", binding.btnNum3 to "3",
            binding.btnNum4 to "4", binding.btnNum5 to "5", binding.btnNum6 to "6",
            binding.btnNum7 to "7", binding.btnNum8 to "8", binding.btnNum9 to "9",
            binding.btnNum0 to "0", binding.btnChList to "Ch_list", binding.btnReturn to "Return",
            binding.btnPMode to "P_mode", binding.btnGuide to "Guide", binding.btnSMode to "S_mode",
            binding.btnMenu to "Menu", binding.btnSource to "Source",
            binding.btnUp to "Up", binding.btnDown to "Down", binding.btnLeft to "Left",
            binding.btnRight to "Right", binding.btnOk to "Ok",
            binding.btnBack to "Back", binding.btnExit to "Exit",
            binding.btnVolUp to "Vol_up", binding.btnVolDown to "Vol_dn",
            binding.btnChUp to "Ch_next", binding.btnChDown to "Ch_prev",
            binding.btnDisplay to "Display", binding.btnSleep to "Sleep",
            binding.btnFav to "Fav", binding.btnInfo to "Info", binding.btnAudio to "Audio", binding.btnHome to "Home",
            binding.btnRewind to "Rew", binding.btnPlayPause to "Play", binding.btnStop to "Stop", binding.btnFastFwd to "Ffwd",
            binding.btnColorRed to "Red", binding.btnColorGreen to "Green",
            binding.btnColorYellow to "Yellow", binding.btnColorBlue to "Blue"
        )
        keys.forEach { (view, key) -> view.setOnClickListener { v -> sendKey(key, v) } }
        listOf(binding.btnVolUp, binding.btnVolDown, binding.btnChUp, binding.btnChDown)
            .forEach { setupHoldRepeat(it, keys.getValue(it)) }
    }

    /** Un paso del barrido: una o varias señales; [profile] null = lote de la búsqueda profunda (se refina). */
    private class ScanCand(val code: String, val label: String, val signals: List<IrSignal>, val profile: RemoteProfile?)

    /**
     * Búsqueda automática: como el RM-014S+ físico, recorre los códigos de su memoria
     * (aquí la librería universal, cada uno en su protocolo real) hasta que la TV reacciona.
     */
    private fun showAutoScanDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_auto_scan)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val spinnerFreq = dialog.findViewById<Spinner>(R.id.spinnerFrequency)
        val spinnerMode = dialog.findViewById<Spinner>(R.id.spinnerScanMode)
        val btnStartScan = dialog.findViewById<Button>(R.id.btnStartScan)
        val btnTestCamera = dialog.findViewById<Button>(R.id.btnTestCameraLed)
        val tvScanStatus = dialog.findViewById<TextView>(R.id.tvScanStatus)
        val tvScanHex = dialog.findViewById<TextView>(R.id.tvScanHex)
        val tvScanDetails = dialog.findViewById<TextView>(R.id.tvScanDetails)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBarScan)
        val btnStopAndSave = dialog.findViewById<Button>(R.id.btnStopAndSave)
        val btnStepBack = dialog.findViewById<Button>(R.id.btnStepBack)
        val btnPauseResume = dialog.findViewById<Button>(R.id.btnPauseResume)
        val btnTestVol = dialog.findViewById<Button>(R.id.btnTestVol)
        val btnCancelScan = dialog.findViewById<Button>(R.id.btnCancelScan)
        val tvHwInfo = dialog.findViewById<TextView>(R.id.tvScanHwInfo)
        tvHwInfo.text = getString(R.string.scan_hw_info, deviceName(), irHelper.getHardwareInfo(this))

        spinnerFreq.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, freqLabels())
        spinnerFreq.setSelection(freqValues.indexOf(freqOverride).coerceAtLeast(0))

        val library = RemoteLibrary.codes(this)
        fun lib(list: List<RemoteProfile>, key: String) =
            list.map { p -> ScanCand(p.codeNumber, p.name, listOf(p.signal(key) ?: p.base), p) }
        val deep = RemoteLibrary.deepBatches(this)
        // Cada modo = (etiqueta, candidatos, pausa tras cada paso en ms)
        val modes = listOf(
            Triple(getString(R.string.scan_mode_lib_power, library.size), lib(library, "Power"), 800L),
            library.filter { it.keys.containsKey("Vol_dn") }.let { Triple(getString(R.string.scan_mode_lib_vol, it.size), lib(it, "Vol_dn"), 800L) },
            Triple(getString(R.string.scan_mode_brands, RemoteConstants.PROFILES.size), lib(RemoteConstants.PROFILES, "Power"), 800L),
            Triple(getString(R.string.scan_mode_deep, deep.size), deep.mapIndexed { i, b ->
                val hex = if (b[0].protocol == "NEC") "%02X".format(b[0].address) else "%04X".format(b[0].address)
                ScanCand("$hex", getString(R.string.scan_deep_batch, b[0].protocol, hex, b.size, if (i < deep.size / 3) 1 else 2), b, null)
            }, 350L),
            Triple(getString(R.string.scan_mode_nec), (0L..0xFFL).map { a ->
                val p = RemoteLibrary.necSweep(a)
                ScanCand(p.codeNumber, p.name, listOf(p.signal("Vol_up")!!, p.base.copy(command = 0x02)), p)
            }, 350L)
        )
        val DEEP = 3
        spinnerMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes.map { it.first })

        isScanning = false
        isScanPaused = false
        scanIndex = 0
        var mode = 0
        var candidates = modes[0].second
        var wait = modes[0].third
        var refining = false
        var scanFreq = freqOverride

        btnTestCamera.setOnClickListener {
            triggerMechanicalFeedback(it)
            Toast.makeText(this, R.string.toast_camera_test, Toast.LENGTH_LONG).show()
            lifecycleScope.launch {
                irHelper.transmitCameraTestSignal(freqValues[spinnerFreq.selectedItemPosition].takeIf { f -> f > 0 } ?: 38000)
            }
        }

        fun current() = candidates[scanIndex.coerceIn(0, candidates.lastIndex)]
        // La búsqueda profunda dura varios minutos: se recuerda dónde quedó
        fun saveDeepPos() { if (mode == DEEP && !refining) prefs.edit().putInt("deep_pos", scanIndex).apply() }

        fun updateScanUI() {
            val c = current()
            val sig = c.signals[0]
            val khz = (if (scanFreq > 0) scanFreq else IrCodec.encode(sig).frequency) / 1000
            tvScanHex.text = c.code
            tvScanDetails.text = getString(R.string.scan_details, scanIndex + 1, candidates.size, c.label, sig.describe(), khz)
            progressBar.progress = scanIndex + 1
        }

        /** Emite el paso actual (mismo protocolo que usará al guardarlo). */
        suspend fun fireCurrent() {
            val c = current()
            c.signals.forEach { irHelper.transmit(it, scanFreq, firstFrameOnly = c.profile == null) }
        }

        fun runScan(startAt: Int) {
            scanIndex = startAt.coerceIn(0, candidates.lastIndex)
            progressBar.max = candidates.size
            isScanning = true
            isScanPaused = false
            btnStartScan.isEnabled = false
            btnPauseResume.text = getString(R.string.btn_pause)
            tvScanStatus.text = getString(if (refining) R.string.scan_refining else R.string.scan_searching)
            scanJob?.cancel()
            scanJob = lifecycleScope.launch {
                while (isScanning && scanIndex < candidates.size) {
                    while (isScanPaused) delay(100)
                    updateScanUI()
                    triggerMechanicalFeedback()
                    fireCurrent()
                    delay(wait)
                    if (!isScanPaused) { scanIndex++; if (scanIndex % 10 == 0) saveDeepPos() }
                }
                if (mode == DEEP && !refining && scanIndex >= candidates.size) prefs.edit().remove("deep_pos").apply()
                scanIndex = scanIndex.coerceAtMost(candidates.lastIndex)
                tvScanStatus.text = getString(R.string.scan_done)
                isScanning = false
                btnStartScan.isEnabled = true
            }
        }

        btnStartScan.setOnClickListener {
            triggerMechanicalFeedback(it)
            if (!irHelper.hasIrEmitter) {
                tvScanStatus.text = getString(R.string.scan_no_ir, deviceName())
                return@setOnClickListener
            }
            mode = spinnerMode.selectedItemPosition
            candidates = modes[mode].second
            wait = modes[mode].third
            refining = false
            scanFreq = freqValues[spinnerFreq.selectedItemPosition]
            val resume = if (mode == DEEP) prefs.getInt("deep_pos", 0) else 0
            if (resume > 0) Toast.makeText(this, getString(R.string.toast_deep_resume, resume + 1, candidates.size), Toast.LENGTH_LONG).show()
            runScan(resume)
        }

        btnStopAndSave.setOnClickListener {
            triggerMechanicalFeedback(it)
            isScanning = false
            scanJob?.cancel()
            val c = current()
            if (c.profile == null) {
                // Lote de la búsqueda profunda: probar uno a uno este lote y el anterior (por la demora al reaccionar)
                saveDeepPos()
                val batches = listOfNotNull(candidates.getOrNull(scanIndex - 1), c)
                candidates = batches.flatMap { b -> b.signals }.map { s ->
                    val p = RemoteLibrary.deepProfile(this, s)
                    ScanCand(p.codeNumber, p.name, listOf(s), p)
                }
                wait = 2500L
                refining = true
                Toast.makeText(this, R.string.toast_refine, Toast.LENGTH_LONG).show()
                runScan(0)
                return@setOnClickListener
            }
            val p = c.profile
            saveProfile(p)
            if (refining) prefs.edit().remove("deep_pos").apply()
            dialog.dismiss()
            Toast.makeText(this, getString(R.string.toast_saved, p.codeNumber, p.name), Toast.LENGTH_LONG).show()
        }

        btnStepBack.setOnClickListener {
            triggerMechanicalFeedback(it)
            // Retroceder pausa el barrido para poder confirmar el código con PROBAR
            if (isScanning) {
                isScanPaused = true
                btnPauseResume.text = getString(R.string.btn_resume)
                tvScanStatus.text = getString(R.string.scan_paused)
            }
            if (scanIndex > 0) scanIndex--
            updateScanUI()
            lifecycleScope.launch { fireCurrent() }
        }

        btnPauseResume.setOnClickListener {
            triggerMechanicalFeedback(it)
            isScanPaused = !isScanPaused
            if (isScanPaused) saveDeepPos()
            btnPauseResume.text = getString(if (isScanPaused) R.string.btn_resume else R.string.btn_pause)
            tvScanStatus.text = getString(if (isScanPaused) R.string.scan_paused else if (refining) R.string.scan_refining else R.string.scan_searching)
        }

        btnTestVol.setOnClickListener {
            triggerMechanicalFeedback(it)
            updateScanUI()
            lifecycleScope.launch { fireCurrent() }
        }

        btnCancelScan.setOnClickListener {
            if (isScanning) saveDeepPos()
            isScanning = false
            scanJob?.cancel()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Diálogo de Selección de Marcas / Código
     */
    private fun showBrandSelectorDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_brand_selector)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etCodeNumber = dialog.findViewById<EditText>(R.id.etCodeNumber)
        val btnApplyCode = dialog.findViewById<Button>(R.id.btnApplyCode)
        val rvBrands = dialog.findViewById<RecyclerView>(R.id.rvBrands)
        val btnClose = dialog.findViewById<Button>(R.id.btnCloseBrandDialog)
        val library = RemoteLibrary.codes(this)
        dialog.findViewById<TextView>(R.id.tvBrandSubtitle).text =
            getString(R.string.brand_subtitle, library.first().codeNumber, library.last().codeNumber)

        btnApplyCode.setOnClickListener {
            triggerMechanicalFeedback(it)
            val code = etCodeNumber.text.toString().trim().trimStart('0').padStart(3, '0')
            val matched = (RemoteConstants.PROFILES + library).firstOrNull { p -> p.codeNumber == code }
            if (matched != null) {
                saveProfile(matched)
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.toast_code_loaded, matched.codeNumber, matched.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.toast_code_unknown, code), Toast.LENGTH_SHORT).show()
            }
        }

        rvBrands.layoutManager = LinearLayoutManager(this)
        rvBrands.adapter = BrandAdapter(
            profiles = RemoteConstants.PROFILES,
            onProfileSelected = { p ->
                triggerMechanicalFeedback()
                saveProfile(p)
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.toast_profile_on, p.name, p.brandButtonLabel), Toast.LENGTH_SHORT).show()
            },
            onTestProfile = { p ->
                triggerMechanicalFeedback()
                lifecycleScope.launch { irHelper.transmit(p.signal("Vol_up") ?: p.base, freqOverride) }
                Toast.makeText(this, getString(R.string.toast_testing, p.name), Toast.LENGTH_SHORT).show()
            }
        )

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Muestra el Manual y Especificaciones.
     * El nombre del equipo se resuelve en tiempo de ejecución (Build.MODEL).
     */
    private fun showManualDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_manual_specs)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.findViewById<TextView>(R.id.tvManualLineOfSight).text = getString(R.string.manual_tip_sight, deviceName())
        dialog.findViewById<TextView>(R.id.tvManualDevice).text =
            getString(R.string.manual_device, deviceName(), irHelper.getHardwareInfo(this))

        val close = View.OnClickListener { v ->
            triggerMechanicalFeedback(v)
            dialog.dismiss()
        }
        dialog.findViewById<Button>(R.id.btnCloseManual).setOnClickListener(close)
        dialog.findViewById<Button>(R.id.btnAcceptManual).setOnClickListener(close)

        dialog.show()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        holdJob?.cancel()
        super.onDestroy()
    }
}
