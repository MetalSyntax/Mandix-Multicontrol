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

    // Perfil y configuración activa
    private var activeAddress: Int = 0x00
    private var activeAddressHigh: Int? = 0xBF
    private var activeFrequency: Int = 38000
    private var activeBrandName: String = "Placa China CX-507 (014)"
    private var cmdPower: Int = RemoteConstants.CMD_POWER
    private var cmdVolUp: Int = RemoteConstants.CMD_VOL_UP
    private var cmdVolDown: Int = RemoteConstants.CMD_VOL_DOWN
    private var cmdChUp: Int = RemoteConstants.CMD_CH_UP
    private var cmdChDown: Int = RemoteConstants.CMD_CH_DOWN
    private var cmdMute: Int = RemoteConstants.CMD_MUTE

    // Variables de Auto-Scan
    private var scanJob: Job? = null
    private var isScanning = false
    private var isScanPaused = false
    private var scanCurrentAddress = 0x00
    private var scanFrequency = 38000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irHelper = IrManagerHelper(this)
        prefs = getSharedPreferences("rm014s_prefs_v3", Context.MODE_PRIVATE)

        // Configuración moderna de Vibrator para Android 12+ (Tecno Camon 30)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        loadSavedSettings()
        updateDisplayScreen()
        setupButtonListeners()
    }

    private fun loadSavedSettings() {
        activeAddress = prefs.getInt("active_address", 0x00)
        val high = prefs.getInt("active_address_high", -1)
        activeAddressHigh = if (high != -1) high else null
        activeFrequency = prefs.getInt("active_frequency", 38000)
        activeBrandName = prefs.getString("active_brand_name", "Placa China CX-507 (014)") ?: "Placa China CX-507 (014)"

        cmdPower = prefs.getInt("cmd_power", RemoteConstants.CMD_POWER)
        cmdVolUp = prefs.getInt("cmd_vol_up", RemoteConstants.CMD_VOL_UP)
        cmdVolDown = prefs.getInt("cmd_vol_down", RemoteConstants.CMD_VOL_DOWN)
        cmdChUp = prefs.getInt("cmd_ch_up", RemoteConstants.CMD_CH_UP)
        cmdChDown = prefs.getInt("cmd_ch_down", RemoteConstants.CMD_CH_DOWN)
        cmdMute = prefs.getInt("cmd_mute", RemoteConstants.CMD_MUTE)
    }

    private fun saveProfile(profile: RemoteProfile) {
        activeAddress = profile.address
        activeAddressHigh = profile.addressHigh
        activeFrequency = profile.frequency
        activeBrandName = profile.name

        cmdPower = profile.cmdPower
        cmdVolUp = profile.cmdVolUp
        cmdVolDown = profile.cmdVolDown
        cmdChUp = profile.cmdChUp
        cmdChDown = profile.cmdChDown
        cmdMute = profile.cmdMute

        prefs.edit()
            .putInt("active_address", activeAddress)
            .putInt("active_address_high", activeAddressHigh ?: -1)
            .putInt("active_frequency", activeFrequency)
            .putString("active_brand_name", activeBrandName)
            .putInt("cmd_power", cmdPower)
            .putInt("cmd_vol_up", cmdVolUp)
            .putInt("cmd_vol_down", cmdVolDown)
            .putInt("cmd_ch_up", cmdChUp)
            .putInt("cmd_ch_down", cmdChDown)
            .putInt("cmd_mute", cmdMute)
            .apply()

        updateDisplayScreen()
        flashLedThreeTimes()
    }

    private fun updateDisplayScreen() {
        val freqKhz = activeFrequency / 1000
        binding.tvDisplayFrequency.text = "${freqKhz} kHz"
        binding.tvActiveProfileName.text = "$activeBrandName • ${freqKhz}kHz"
    }

    /**
     * Feedback Háptico Realista: simula la resistencia mecánica y clic de un control físico
     */
    private fun triggerMechanicalFeedback(view: View? = null) {
        // 1. Háptica directa de la vista
        view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )

        // 2. Vibración háptica en el motor del dispositivo
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

        // 3. Destello visual en el LED virtual
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

    private fun sendCommand(command: Int, view: View? = null) {
        triggerMechanicalFeedback(view)
        lifecycleScope.launch {
            irHelper.transmitNec(
                address = activeAddress,
                command = command,
                frequency = activeFrequency,
                addressHigh = activeAddressHigh,
                doubleBurst = true
            )
        }
    }

    private fun setupButtonListeners() {
        // Visor OLED y placa serigrafiada inferior
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

        // Botón SET Físico
        binding.btnSet.setOnClickListener { v ->
            triggerMechanicalFeedback(v)
            showAutoScanDialog()
        }

        // 1. Cabecera (ImageButtons vectoriales)
        binding.btnPower.setOnClickListener { v -> sendCommand(cmdPower, v) }
        binding.btnMute.setOnClickListener { v -> sendCommand(cmdMute, v) }

        // 2. Teclado Numérico
        binding.btnNum1.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_1, v) }
        binding.btnNum2.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_2, v) }
        binding.btnNum3.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_3, v) }
        binding.btnNum4.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_4, v) }
        binding.btnNum5.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_5, v) }
        binding.btnNum6.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_6, v) }
        binding.btnNum7.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_7, v) }
        binding.btnNum8.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_8, v) }
        binding.btnNum9.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_9, v) }
        binding.btnNum0.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_NUM_0, v) }
        binding.btnChList.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_CH_LIST, v) }
        binding.btnReturn.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_RETURN, v) }

        // 3. Modos
        binding.btnPMode.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_P_MODE, v) }
        binding.btnGuide.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_GUIDE, v) }
        binding.btnSMode.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_S_MODE, v) }

        // 4. Menú y Source
        binding.btnMenu.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_MENU, v) }
        binding.btnSource.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_SOURCE, v) }

        // D-PAD
        binding.btnUp.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_UP, v) }
        binding.btnDown.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_DOWN, v) }
        binding.btnLeft.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_LEFT, v) }
        binding.btnRight.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_RIGHT, v) }
        binding.btnOk.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_OK, v) }

        binding.btnBack.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_BACK, v) }
        binding.btnExit.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_EXIT, v) }

        // 5. Volumen y Canales
        binding.btnVolUp.setOnClickListener { v -> sendCommand(cmdVolUp, v) }
        binding.btnVolDown.setOnClickListener { v -> sendCommand(cmdVolDown, v) }
        binding.btnChUp.setOnClickListener { v -> sendCommand(cmdChUp, v) }
        binding.btnChDown.setOnClickListener { v -> sendCommand(cmdChDown, v) }

        binding.btnDisplay.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_DISPLAY, v) }
        binding.btnSleep.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_SLEEP, v) }

        // 6. Multimedia
        binding.btnFav.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_FAV, v) }
        binding.btnInfo.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_INFO, v) }
        binding.btnAudio.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_AUDIO, v) }
        binding.btnHome.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_HOME, v) }

        binding.btnRewind.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_REWIND, v) }
        binding.btnPlayPause.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_PLAY_PAUSE, v) }
        binding.btnStop.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_STOP, v) }
        binding.btnFastFwd.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_FAST_FWD, v) }

        // 7. Colores
        binding.btnColorRed.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_COLOR_RED, v) }
        binding.btnColorGreen.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_COLOR_GREEN, v) }
        binding.btnColorYellow.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_COLOR_YELLOW, v) }
        binding.btnColorBlue.setOnClickListener { v -> sendCommand(RemoteConstants.CMD_COLOR_BLUE, v) }
    }

    /**
     * Diálogo de Búsqueda Automática con Spinners (Look & Feel del control)
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

        // 1. Configurar Spinner de Frecuencias
        val freqOptions = listOf(
            "38 kHz (Recomendada • Placas Chinas y TVs)",
            "36 kHz (Philips / RC-5 / Placas Chinas Alt.)",
            "40 kHz (Sony / SIRC)",
            "56 kHz (RCA / Thomson / Cable)"
        )
        val freqAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, freqOptions)
        spinnerFreq.adapter = freqAdapter
        when (activeFrequency) {
            36000 -> spinnerFreq.setSelection(1)
            40000 -> spinnerFreq.setSelection(2)
            56000 -> spinnerFreq.setSelection(3)
            else -> spinnerFreq.setSelection(0)
        }

        // 2. Configurar Spinner de Modos de Búsqueda
        val modeOptions = listOf(
            "1. Placas Chinas CX-507 / CV9203 / MStar (Rápido)",
            "2. Códigos Oficiales Manual RM-014S+ (001 - 250)",
            "3. Barrido Exhaustivo Total (0x00 a 0xFF)"
        )
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeOptions)
        spinnerMode.adapter = modeAdapter

        isScanning = false
        isScanPaused = false
        scanCurrentAddress = 0x00

        // Botón Test Cámara
        btnTestCamera.setOnClickListener {
            triggerMechanicalFeedback(it)
            Toast.makeText(this, "Emitiendo ráfaga... Mira el LED superior con la cámara de otro celular", Toast.LENGTH_LONG).show()
            lifecycleScope.launch {
                val selectedFreq = when (spinnerFreq.selectedItemPosition) {
                    1 -> 36000
                    2 -> 40000
                    3 -> 56000
                    else -> 38000
                }
                irHelper.transmitCameraTestSignal(selectedFreq)
            }
        }

        fun updateScanUI() {
            val hex = String.format("0x%02X", scanCurrentAddress)
            tvScanHex.text = hex
            val freqKhz = scanFrequency / 1000
            tvScanDetails.text = "Dir: $hex ($scanCurrentAddress/255) • ${freqKhz}kHz • Pulso: VOL+ (x2)"
            progressBar.progress = scanCurrentAddress
        }

        // Iniciar el escaneo según la selección
        btnStartScan.setOnClickListener {
            triggerMechanicalFeedback(it)
            scanFrequency = when (spinnerFreq.selectedItemPosition) {
                1 -> 36000
                2 -> 40000
                3 -> 56000
                else -> 38000
            }
            val scanMode = spinnerMode.selectedItemPosition

            isScanning = true
            isScanPaused = false
            btnStartScan.isEnabled = false
            tvScanStatus.text = "Buscando... Apunta al televisor"

            scanJob?.cancel()
            scanJob = lifecycleScope.launch {
                if (scanMode == 0) {
                    // MODO 1: Placas Chinas OEM (CX-507, CV9203, MStar)
                    val oemAddresses = intArrayOf(
                        0x00, 0x04, 0x40, 0x08, 0x80, 0x20, 0x01, 0x02, 0xBF, 0xEF, 0x7F, 0x55, 0x50, 0x1F, 0x0E, 0x41
                    )
                    for (addr in oemAddresses) {
                        if (!isScanning) break
                        while (isScanPaused) delay(100)

                        scanCurrentAddress = addr
                        updateScanUI()
                        triggerMechanicalFeedback()

                        // Prueba VOL+ estándar (0x1A)
                        irHelper.transmitNec(addr, RemoteConstants.CMD_VOL_UP, scanFrequency, null, true)
                        delay(140)
                        // Prueba VOL+ alternativo (0x02)
                        irHelper.transmitNec(addr, 0x02, scanFrequency, null, true)
                        delay(280)
                    }
                } else if (scanMode == 1) {
                    // MODO 2: Códigos oficiales de 3 dígitos del manual
                    for (profile in RemoteConstants.PROFILES) {
                        if (!isScanning) break
                        while (isScanPaused) delay(100)

                        scanCurrentAddress = profile.address
                        updateScanUI()
                        tvScanDetails.text = "Cód: ${profile.codeNumber} (${profile.name})"
                        triggerMechanicalFeedback()

                        irHelper.transmitNec(profile.address, profile.cmdVolUp, profile.frequency, profile.addressHigh, true)
                        delay(350)
                    }
                } else {
                    // MODO 3: Barrido Exhaustivo Total 0x00 .. 0xFF
                    scanCurrentAddress = 0x00
                    while (isScanning && scanCurrentAddress <= 0xFF) {
                        while (isScanPaused) delay(100)

                        updateScanUI()
                        triggerMechanicalFeedback()

                        irHelper.transmitNec(scanCurrentAddress, RemoteConstants.CMD_VOL_UP, scanFrequency, null, true)
                        delay(120)
                        irHelper.transmitNec(scanCurrentAddress, 0x02, scanFrequency, null, true)
                        delay(240)

                        if (scanCurrentAddress < 0xFF) {
                            scanCurrentAddress++
                        } else {
                            break
                        }
                    }
                }

                tvScanStatus.text = "Barrido completado"
                isScanning = false
                btnStartScan.isEnabled = true
            }
        }

        // Detener y Guardar
        btnStopAndSave.setOnClickListener {
            triggerMechanicalFeedback(it)
            isScanning = false
            scanJob?.cancel()

            val hex = String.format("0x%02X", scanCurrentAddress)
            activeAddress = scanCurrentAddress
            activeAddressHigh = null
            activeFrequency = scanFrequency
            activeBrandName = "Calibrado Auto ($hex)"

            prefs.edit()
                .putInt("active_address", activeAddress)
                .putInt("active_address_high", -1)
                .putInt("active_frequency", activeFrequency)
                .putString("active_brand_name", activeBrandName)
                .apply()

            updateDisplayScreen()
            flashLedThreeTimes()
            dialog.dismiss()
            Toast.makeText(this, "¡Calibrado exitoso! Código $hex a ${scanFrequency / 1000}kHz", Toast.LENGTH_LONG).show()
        }

        btnStepBack.setOnClickListener {
            triggerMechanicalFeedback(it)
            if (scanCurrentAddress > 0) {
                scanCurrentAddress--
                updateScanUI()
                lifecycleScope.launch {
                    irHelper.transmitNec(scanCurrentAddress, RemoteConstants.CMD_VOL_UP, scanFrequency, null, true)
                }
            }
        }

        btnPauseResume.setOnClickListener {
            triggerMechanicalFeedback(it)
            isScanPaused = !isScanPaused
            if (isScanPaused) {
                btnPauseResume.text = "▶ SEGUIR"
                tvScanStatus.text = "Pausado"
            } else {
                btnPauseResume.text = "⏸ PAUSAR"
                tvScanStatus.text = "Buscando... Apunta al televisor"
            }
        }

        btnTestVol.setOnClickListener {
            triggerMechanicalFeedback(it)
            lifecycleScope.launch {
                irHelper.transmitNec(scanCurrentAddress, RemoteConstants.CMD_VOL_UP, scanFrequency, null, true)
            }
        }

        btnCancelScan.setOnClickListener {
            isScanning = false
            scanJob?.cancel()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Diálogo de Selección de Marcas / Código de 3 dígitos
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

        btnApplyCode.setOnClickListener {
            triggerMechanicalFeedback(it)
            val codeStr = etCodeNumber.text.toString().trim()
            val matched = RemoteConstants.PROFILES.firstOrNull { it.codeNumber == codeStr }
            if (matched != null) {
                saveProfile(matched)
                dialog.dismiss()
                Toast.makeText(this, "Código ${matched.codeNumber} (${matched.name}) cargado", Toast.LENGTH_SHORT).show()
            } else {
                val codeInt = codeStr.toIntOrNull() ?: 0
                val addr = codeInt and 0xFF
                val profile = RemoteProfile("custom_$codeInt", "Código $codeStr", "SET + $codeStr", codeStr, addr, null, 38000)
                saveProfile(profile)
                dialog.dismiss()
                Toast.makeText(this, "Código personalizado $codeStr cargado", Toast.LENGTH_SHORT).show()
            }
        }

        rvBrands.layoutManager = LinearLayoutManager(this)
        rvBrands.adapter = BrandAdapter(
            profiles = RemoteConstants.PROFILES,
            onProfileSelected = { profile ->
                triggerMechanicalFeedback()
                saveProfile(profile)
                dialog.dismiss()
                Toast.makeText(this, "Perfil '${profile.name}' (${profile.brandButtonLabel}) activado", Toast.LENGTH_SHORT).show()
            },
            onTestProfile = { profile ->
                triggerMechanicalFeedback()
                lifecycleScope.launch {
                    irHelper.transmitNec(profile.address, profile.cmdVolUp, profile.frequency, profile.addressHigh, true)
                }
                Toast.makeText(this, "Probando VOL+ en ${profile.name}...", Toast.LENGTH_SHORT).show()
            }
        )

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Muestra el Manual y Especificaciones 1:1
     */
    private fun showManualDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_manual_specs)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialog.findViewById<Button>(R.id.btnCloseManual)
        val btnAccept = dialog.findViewById<Button>(R.id.btnAcceptManual)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnAccept.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        super.onDestroy()
    }
}
