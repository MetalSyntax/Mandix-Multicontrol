package com.example.rm014s

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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
        prefs = getSharedPreferences("rm014s_prefs_v2", Context.MODE_PRIVATE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

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

    private fun triggerFeedback() {
        // Destello visual en el LED del control
        binding.viewLedIr.setBackgroundResource(R.drawable.led_indicator_active)
        binding.viewLedIr.postDelayed({
            binding.viewLedIr.setBackgroundResource(R.drawable.led_indicator)
        }, 120)

        // Vibración háptica
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25)
        }
    }

    /**
     * Parpadea el LED 3 veces al vincular marca, como describe el manual oficial
     */
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

    private fun sendCommand(command: Int) {
        triggerFeedback()
        irHelper.transmitNec(
            address = activeAddress,
            command = command,
            frequency = activeFrequency,
            addressHigh = activeAddressHigh,
            sendRepeat = true
        )
    }

    private var isSystemLocked = false
    private var lastSetPressTime: Long = 0

    private fun setupButtonListeners() {
        // Visor OLED y placa serigrafiada inferior abren opciones de configuración
        binding.layoutOledScreen.setOnClickListener { showBrandSelectorDialog() }
        binding.layoutPlateBottom.setOnClickListener { showAutoScanDialog() }
        binding.btnPlateSettings.setOnClickListener { showAutoScanDialog() }
        binding.btnPlateManual.setOnClickListener { showManualDialog() }

        // Botón SET Físico: registra tiempo para combinación con OK o abre el diálogo de escaneo
        binding.btnSet.setOnClickListener {
            val now = System.currentTimeMillis()
            lastSetPressTime = now
            showAutoScanDialog()
        }

        // 1. Cabecera (Power & Mute integrados)
        binding.btnPower.setOnClickListener { sendCommand(cmdPower) }
        binding.btnMute.setOnClickListener { sendCommand(cmdMute) }

        // 2. Teclado Numérico
        binding.btnNum1.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_1) }
        binding.btnNum2.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_2) }
        binding.btnNum3.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_3) }
        binding.btnNum4.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_4) }
        binding.btnNum5.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_5) }
        binding.btnNum6.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_6) }
        binding.btnNum7.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_7) }
        binding.btnNum8.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_8) }
        binding.btnNum9.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_9) }
        binding.btnNum0.setOnClickListener { sendCommand(RemoteConstants.CMD_NUM_0) }
        binding.btnChList.setOnClickListener { sendCommand(RemoteConstants.CMD_CH_LIST) }
        binding.btnReturn.setOnClickListener { sendCommand(RemoteConstants.CMD_RETURN) }

        // 3. Modos
        binding.btnPMode.setOnClickListener { sendCommand(RemoteConstants.CMD_P_MODE) }
        binding.btnGuide.setOnClickListener { sendCommand(RemoteConstants.CMD_GUIDE) }
        binding.btnSMode.setOnClickListener { sendCommand(RemoteConstants.CMD_S_MODE) }

        // 4. Menú y Source
        binding.btnMenu.setOnClickListener { sendCommand(RemoteConstants.CMD_MENU) }
        binding.btnSource.setOnClickListener { sendCommand(RemoteConstants.CMD_SOURCE) }

        // D-PAD
        binding.btnUp.setOnClickListener { sendCommand(RemoteConstants.CMD_UP) }
        binding.btnDown.setOnClickListener { sendCommand(RemoteConstants.CMD_DOWN) }
        binding.btnLeft.setOnClickListener { sendCommand(RemoteConstants.CMD_LEFT) }
        binding.btnRight.setOnClickListener { sendCommand(RemoteConstants.CMD_RIGHT) }
        binding.btnOk.setOnClickListener { sendCommand(RemoteConstants.CMD_OK) }

        binding.btnBack.setOnClickListener { sendCommand(RemoteConstants.CMD_BACK) }
        binding.btnExit.setOnClickListener { sendCommand(RemoteConstants.CMD_EXIT) }

        // 5. Volumen y Canales
        binding.btnVolUp.setOnClickListener { sendCommand(cmdVolUp) }
        binding.btnVolDown.setOnClickListener { sendCommand(cmdVolDown) }
        binding.btnChUp.setOnClickListener { sendCommand(cmdChUp) }
        binding.btnChDown.setOnClickListener { sendCommand(cmdChDown) }

        binding.btnDisplay.setOnClickListener { sendCommand(RemoteConstants.CMD_DISPLAY) }
        binding.btnSleep.setOnClickListener { sendCommand(RemoteConstants.CMD_SLEEP) }

        // 6. Multimedia
        binding.btnFav.setOnClickListener { sendCommand(RemoteConstants.CMD_FAV) }
        binding.btnInfo.setOnClickListener { sendCommand(RemoteConstants.CMD_INFO) }
        binding.btnAudio.setOnClickListener { sendCommand(RemoteConstants.CMD_AUDIO) }
        binding.btnHome.setOnClickListener { sendCommand(RemoteConstants.CMD_HOME) }

        binding.btnRewind.setOnClickListener { sendCommand(RemoteConstants.CMD_REWIND) }
        binding.btnPlayPause.setOnClickListener { sendCommand(RemoteConstants.CMD_PLAY_PAUSE) }
        binding.btnStop.setOnClickListener { sendCommand(RemoteConstants.CMD_STOP) }
        binding.btnFastFwd.setOnClickListener { sendCommand(RemoteConstants.CMD_FAST_FWD) }

        // 7. Colores
        binding.btnColorRed.setOnClickListener { sendCommand(RemoteConstants.CMD_COLOR_RED) }
        binding.btnColorGreen.setOnClickListener { sendCommand(RemoteConstants.CMD_COLOR_GREEN) }
        binding.btnColorYellow.setOnClickListener { sendCommand(RemoteConstants.CMD_COLOR_YELLOW) }
        binding.btnColorBlue.setOnClickListener { sendCommand(RemoteConstants.CMD_COLOR_BLUE) }
    }

    /**
     * Diálogo de Búsqueda Extendida (con selector de frecuencia y barrido multi-comando)
     */
    private fun showAutoScanDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_auto_scan)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val rgFreq = dialog.findViewById<RadioGroup>(R.id.rgFrequency)
        val tvScanStatus = dialog.findViewById<TextView>(R.id.tvScanStatus)
        val tvScanHex = dialog.findViewById<TextView>(R.id.tvScanHex)
        val tvScanDetails = dialog.findViewById<TextView>(R.id.tvScanDetails)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBarScan)
        val btnStopAndSave = dialog.findViewById<Button>(R.id.btnStopAndSave)
        val btnStepBack = dialog.findViewById<Button>(R.id.btnStepBack)
        val btnPauseResume = dialog.findViewById<Button>(R.id.btnPauseResume)
        val btnTestVol = dialog.findViewById<Button>(R.id.btnTestVol)
        val btnCancelScan = dialog.findViewById<Button>(R.id.btnCancelScan)

        scanCurrentAddress = 0x00
        scanFrequency = activeFrequency
        isScanning = true
        isScanPaused = false

        // Ajustar frecuencia según selección de radio
        when (scanFrequency) {
            36000 -> dialog.findViewById<RadioButton>(R.id.rb36k)?.isChecked = true
            40000 -> dialog.findViewById<RadioButton>(R.id.rb40k)?.isChecked = true
            56000 -> dialog.findViewById<RadioButton>(R.id.rb56k)?.isChecked = true
            else -> dialog.findViewById<RadioButton>(R.id.rb38k)?.isChecked = true
        }

        rgFreq.setOnCheckedChangeListener { _, checkedId ->
            scanFrequency = when (checkedId) {
                R.id.rb36k -> 36000
                R.id.rb40k -> 40000
                R.id.rb56k -> 56000
                else -> 38000
            }
        }

        fun updateScanUI() {
            val hex = String.format("0x%02X", scanCurrentAddress)
            tvScanHex.text = hex
            val freqKhz = scanFrequency / 1000
            tvScanDetails.text = "Dir: $hex ($scanCurrentAddress/255) • ${freqKhz}kHz • Pulso: VOL+ (x2)"
            progressBar.progress = scanCurrentAddress
        }

        // Bucle de búsqueda extendida enviando doble ráfaga
        scanJob = lifecycleScope.launch {
            while (isScanning && scanCurrentAddress <= 0xFF) {
                if (!isScanPaused) {
                    updateScanUI()
                    triggerFeedback()

                    // Envía el comando de VOL+ estándar
                    irHelper.transmitNec(
                        address = scanCurrentAddress,
                        command = RemoteConstants.CMD_VOL_UP,
                        frequency = scanFrequency,
                        sendRepeat = true
                    )

                    // Y envía también el comando de VOL+ alternativo (0x02) en la misma dirección para máxima compatibilidad
                    delay(150)
                    irHelper.transmitNec(
                        address = scanCurrentAddress,
                        command = 0x02,
                        frequency = scanFrequency,
                        sendRepeat = true
                    )

                    delay(260) // Pausa entre direcciones

                    if (scanCurrentAddress < 0xFF) {
                        scanCurrentAddress++
                    } else {
                        tvScanStatus.text = "Barrido completado en ${scanFrequency / 1000}kHz"
                        isScanning = false
                    }
                } else {
                    delay(100)
                }
            }
        }

        // Guardar la dirección calibrada
        btnStopAndSave.setOnClickListener {
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
            if (scanCurrentAddress > 0) {
                scanCurrentAddress--
                updateScanUI()
                irHelper.transmitNec(scanCurrentAddress, RemoteConstants.CMD_VOL_UP, scanFrequency, null, true)
            }
        }

        btnPauseResume.setOnClickListener {
            isScanPaused = !isScanPaused
            if (isScanPaused) {
                btnPauseResume.text = "Seguir"
                tvScanStatus.text = "Pausado"
            } else {
                btnPauseResume.text = "Pausar"
                tvScanStatus.text = "Transmitiendo..."
            }
        }

        btnTestVol.setOnClickListener {
            triggerFeedback()
            irHelper.transmitNec(scanCurrentAddress, RemoteConstants.CMD_VOL_UP, scanFrequency, null, true)
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

        // Cargar código de 3 dígitos (Método 2)
        btnApplyCode.setOnClickListener {
            val codeStr = etCodeNumber.text.toString().trim()
            val matched = RemoteConstants.PROFILES.firstOrNull { it.codeNumber == codeStr }
            if (matched != null) {
                saveProfile(matched)
                dialog.dismiss()
                Toast.makeText(this, "Código ${matched.codeNumber} (${matched.name}) cargado", Toast.LENGTH_SHORT).show()
            } else {
                // Si no coincide exactamente, calcula la dirección por el número
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
                saveProfile(profile)
                dialog.dismiss()
                Toast.makeText(this, "Perfil '${profile.name}' (${profile.brandButtonLabel}) activado", Toast.LENGTH_SHORT).show()
            },
            onTestProfile = { profile ->
                triggerFeedback()
                irHelper.transmitNec(profile.address, profile.cmdVolUp, profile.frequency, profile.addressHigh, true)
                Toast.makeText(this, "Probando VOL+ en ${profile.name}...", Toast.LENGTH_SHORT).show()
            }
        )

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Muestra la Guía Oficial, Especificaciones 1:1 del fabricante XingZhiHua y Consejos de Uso
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
