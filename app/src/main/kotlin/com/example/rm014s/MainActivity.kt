package com.example.rm014s

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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

    private var currentAddress: Int = 0x00
    private var currentBrandName: String = "Genérico / CX-507 (0x00)"

    // Variables de Auto-Scan
    private var scanJob: Job? = null
    private var isScanning = false
    private var isScanPaused = false
    private var scanCurrentAddress = 0x00

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irHelper = IrManagerHelper(this)
        prefs = getSharedPreferences("rm014s_prefs", Context.MODE_PRIVATE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        loadSavedSettings()
        setupHardwareIndicator()
        setupButtonListeners()
    }

    private fun loadSavedSettings() {
        currentAddress = prefs.getInt("saved_address", 0x00)
        currentBrandName = prefs.getString("saved_brand_name", "Genérico / CX-507 (0x00)") ?: "Genérico / CX-507 (0x00)"
        binding.tvActiveBrand.text = currentBrandName
    }

    private fun saveSettings(address: Int, brandName: String) {
        currentAddress = address
        currentBrandName = brandName
        prefs.edit()
            .putInt("saved_address", address)
            .putString("saved_brand_name", brandName)
            .apply()
        binding.tvActiveBrand.text = brandName
    }

    private fun setupHardwareIndicator() {
        if (irHelper.hasIrEmitter) {
            binding.tvHardwareStatus.text = "IR Activo (Hardware)"
            binding.tvHardwareStatus.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            binding.tvHardwareStatus.text = "Simulación (Sin IR)"
            binding.tvHardwareStatus.setTextColor(getColor(android.R.color.holo_orange_light))
        }
    }

    private fun triggerFeedback() {
        // 1. Destello visual realista en el LED del control
        binding.viewLedIr.setBackgroundResource(R.drawable.led_indicator_active)
        binding.viewLedIr.postDelayed({
            binding.viewLedIr.setBackgroundResource(R.drawable.led_indicator)
        }, 120)

        // 2. Vibración háptica sutil
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25)
        }
    }

    private fun sendCommand(command: Int) {
        triggerFeedback()
        irHelper.transmitNec(currentAddress, command)
    }

    private fun setupButtonListeners() {
        // Barra Superior
        binding.btnOpenScanner.setOnClickListener { showAutoScanDialog() }
        binding.btnOpenBrands.setOnClickListener { showBrandSelectorDialog() }

        // 1. Cabecera
        binding.btnPower.setOnClickListener { sendCommand(RemoteConstants.CMD_POWER) }
        binding.btnMute.setOnClickListener { sendCommand(RemoteConstants.CMD_MUTE) }

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
        binding.btnVolUp.setOnClickListener { sendCommand(RemoteConstants.CMD_VOL_UP) }
        binding.btnVolDown.setOnClickListener { sendCommand(RemoteConstants.CMD_VOL_DOWN) }
        binding.btnChUp.setOnClickListener { sendCommand(RemoteConstants.CMD_CH_UP) }
        binding.btnChDown.setOnClickListener { sendCommand(RemoteConstants.CMD_CH_DOWN) }

        // Botones centrales
        binding.btnSet.setOnClickListener { showAutoScanDialog() }
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

        // 8. Placa serigrafiada inferior
        binding.layoutPlateBottom.setOnClickListener { showAutoScanDialog() }
    }

    /**
     * Muestra el Diálogo de Búsqueda Automática
     */
    private fun showAutoScanDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_auto_scan)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val tvScanStatus = dialog.findViewById<TextView>(R.id.tvScanStatus)
        val tvScanHex = dialog.findViewById<TextView>(R.id.tvScanHex)
        val tvScanProgress = dialog.findViewById<TextView>(R.id.tvScanProgress)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBarScan)
        val btnStopAndSave = dialog.findViewById<Button>(R.id.btnStopAndSave)
        val btnStepBack = dialog.findViewById<Button>(R.id.btnStepBack)
        val btnPauseResume = dialog.findViewById<Button>(R.id.btnPauseResume)
        val btnTestVol = dialog.findViewById<Button>(R.id.btnTestVol)
        val btnCancelScan = dialog.findViewById<Button>(R.id.btnCancelScan)

        scanCurrentAddress = 0x00
        isScanning = true
        isScanPaused = false

        fun updateUI() {
            val hex = String.format("0x%02X", scanCurrentAddress)
            tvScanHex.text = hex
            tvScanProgress.text = "Dirección: $scanCurrentAddress / 255 (Chasis NEC)"
            progressBar.progress = scanCurrentAddress
        }

        // Bucle en segundo plano con corrutinas
        scanJob = lifecycleScope.launch {
            while (isScanning && scanCurrentAddress <= 0xFF) {
                if (!isScanPaused) {
                    updateUI()
                    triggerFeedback()
                    irHelper.transmitNec(scanCurrentAddress, RemoteConstants.CMD_VOL_UP)

                    delay(280) // Intervalo de 280ms
                    if (scanCurrentAddress < 0xFF) {
                        scanCurrentAddress++
                    } else {
                        tvScanStatus.text = "Barrido completado (0x00 - 0xFF)"
                        isScanning = false
                    }
                } else {
                    delay(100)
                }
            }
        }

        // Botón ¡DETENER / GUARDAR!
        btnStopAndSave.setOnClickListener {
            isScanning = false
            scanJob?.cancel()
            val hexCode = String.format("0x%02X", scanCurrentAddress)
            saveSettings(scanCurrentAddress, "Calibrado Auto ($hexCode)")
            dialog.dismiss()
            Toast.makeText(this, "¡Calibrado exitoso! Código $hexCode guardado.", Toast.LENGTH_LONG).show()
        }

        // Retroceder 1 dirección
        btnStepBack.setOnClickListener {
            if (scanCurrentAddress > 0) {
                scanCurrentAddress--
                updateUI()
                irHelper.transmitNec(scanCurrentAddress, RemoteConstants.CMD_VOL_UP)
            }
        }

        // Pausar / Continuar
        btnPauseResume.setOnClickListener {
            isScanPaused = !isScanPaused
            if (isScanPaused) {
                btnPauseResume.text = "Continuar"
                tvScanStatus.text = "Escaneo pausado"
            } else {
                btnPauseResume.text = "Pausar"
                tvScanStatus.text = "Transmitiendo comando VOL+..."
            }
        }

        // Probar VOL+
        btnTestVol.setOnClickListener {
            triggerFeedback()
            irHelper.transmitNec(scanCurrentAddress, RemoteConstants.CMD_VOL_UP)
        }

        // Cancelar
        btnCancelScan.setOnClickListener {
            isScanning = false
            scanJob?.cancel()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Muestra el Diálogo de Selector de Marcas y Dirección Manual
     */
    private fun showBrandSelectorDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_brand_selector)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etHexAddress = dialog.findViewById<EditText>(R.id.etHexAddress)
        val btnApplyHex = dialog.findViewById<Button>(R.id.btnApplyHex)
        val rvBrands = dialog.findViewById<RecyclerView>(R.id.rvBrands)
        val btnClose = dialog.findViewById<Button>(R.id.btnCloseBrandDialog)

        etHexAddress.setText(String.format("%02X", currentAddress))

        btnApplyHex.setOnClickListener {
            val text = etHexAddress.text.toString().trim()
            try {
                val parsed = text.toInt(16)
                if (parsed in 0x00..0xFF) {
                    saveSettings(parsed, "Manual (0x${text.uppercase()})")
                    dialog.dismiss()
                    Toast.makeText(this, "Dirección 0x${text.uppercase()} aplicada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Debe ser entre 00 y FF", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Valor hexadecimal inválido", Toast.LENGTH_SHORT).show()
            }
        }

        rvBrands.layoutManager = LinearLayoutManager(this)
        rvBrands.adapter = BrandAdapter(RemoteConstants.PRESETS) { preset ->
            saveSettings(preset.defaultAddress, preset.name)
            dialog.dismiss()
            Toast.makeText(this, "Perfil '${preset.name}' activado", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        super.onDestroy()
    }
}
