package com.erinlkolp.equalizer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var spinnerPresets: Spinner
    private lateinit var btnSavePreset: Button
    private lateinit var btnRenamePreset: Button
    private lateinit var btnDeletePreset: Button
    private lateinit var bandsContainer: LinearLayout

    private lateinit var tvPreampGain: TextView
    private lateinit var seekBarPreamp: SeekBar
    private lateinit var switchAutoHeadroom: SwitchCompat
    private lateinit var btnDrawCurve: Button
    private lateinit var btnSmoothCurve: Button
    private lateinit var scrollBands: HorizontalScrollView
    private lateinit var drawOverlay: View

    private var presetsList = listOf<EqPreset>()
    private var isUpdatingBandsProgrammatically = false
    private var isFirstSpinnerSelection = true

    private var isDrawMode = false
    private var isAutoHeadroom = false
    private var lastDrawBandIndex: Int? = null
    private var lastDrawProgress: Int? = null
    private var defaultDrawButtonBg: Drawable? = null

    private val edgeScrollHandler = Handler(Looper.getMainLooper())
    private var edgeScrollDelta = 0
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f

    private val edgeScrollRunnable = object : Runnable {
        override fun run() {
            if (edgeScrollDelta != 0 && isDrawMode) {
                scrollBands.smoothScrollBy(edgeScrollDelta, 0)
                handleDrawTouch(lastTouchRawX, lastTouchRawY)
                edgeScrollHandler.postDelayed(this, 30)
            }
        }
    }

    // Standard 31-band 1/3 octave frequencies in Hz
    private val frequencies = intArrayOf(
        20, 25, 31, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 
        1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000
    )

    private val seekBars = mutableListOf<SeekBar>()
    private val tvGains = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)

        val switchEnable = findViewById<SwitchCompat>(R.id.switch_enable_eq)
        bandsContainer = findViewById(R.id.eq_bands_container)
        spinnerPresets = findViewById(R.id.spinner_presets)
        btnSavePreset = findViewById(R.id.btn_save_preset)
        btnRenamePreset = findViewById(R.id.btn_rename_preset)
        btnDeletePreset = findViewById(R.id.btn_delete_preset)
        val btnReset = findViewById<Button>(R.id.btn_reset)

        tvPreampGain = findViewById(R.id.tv_preamp_gain)
        seekBarPreamp = findViewById(R.id.seek_bar_preamp)
        switchAutoHeadroom = findViewById(R.id.switch_auto_headroom)
        btnDrawCurve = findViewById(R.id.btn_draw_curve)
        btnSmoothCurve = findViewById(R.id.btn_smooth_curve)
        scrollBands = findViewById(R.id.scroll_view_bands)
        drawOverlay = findViewById(R.id.draw_overlay)

        defaultDrawButtonBg = btnDrawCurve.background

        // Load saved state
        val isEnabled = sharedPrefs.getBoolean("eq_enabled", false)
        switchEnable.isChecked = isEnabled

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("eq_enabled", isChecked).apply()
            val intent = Intent(this, EqService::class.java)
            if (isChecked) {
                intent.action = "ACTION_START"
                startForegroundService(intent)
            } else {
                intent.action = "ACTION_STOP"
                startService(intent)
            }
        }

        initVibrator()

        // Setup bands
        val inflater = LayoutInflater.from(this)
        for (i in frequencies.indices) {
            val bandView = inflater.inflate(R.layout.eq_band_item, bandsContainer, false)
            val tvGain = bandView.findViewById<TextView>(R.id.tv_gain)
            val tvFreq = bandView.findViewById<TextView>(R.id.tv_frequency)
            val seekBar = bandView.findViewById<SeekBar>(R.id.seek_bar_band)

            // Format frequency label
            val freq = frequencies[i]
            tvFreq.text = EqHelper.formatFrequency(freq)

            // SeekBar range: 0 to 240, representing -12.0 dB to +12.0 dB
            // Center is 120 (0.0 dB)
            val savedGain = sharedPrefs.getInt("band_$i", 120)
            seekBar.progress = savedGain
            seekBar.isHapticFeedbackEnabled = true
            updateGainLabel(tvGain, savedGain)

            seekBars.add(seekBar)
            tvGains.add(tvGain)

            var lastUserProgress = savedGain
            var wasInDetent = (savedGain in 118..122)

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && !isUpdatingBandsProgrammatically) {
                        var effectiveProgress = progress
                        val isNearCenter = progress in 118..122
                        val crossedCenter = (lastUserProgress < 120 && progress > 120) || (lastUserProgress > 120 && progress < 120)

                        if (isNearCenter) {
                            effectiveProgress = 120
                            if (!wasInDetent) {
                                triggerCenterDetentHaptic(seekBar ?: bandView)
                                wasInDetent = true
                            }
                            if (progress != 120) {
                                seekBar?.progress = 120
                                return
                            }
                        } else if (crossedCenter) {
                            triggerCenterDetentHaptic(seekBar ?: bandView)
                            wasInDetent = false
                        } else {
                            wasInDetent = false
                        }

                        lastUserProgress = effectiveProgress
                        updateGainLabel(tvGain, effectiveProgress)
                        sharedPrefs.edit().putInt("band_$i", effectiveProgress).apply()
                        EqService.updateBand(i, EqHelper.progressToGainDb(effectiveProgress))
                        if (isAutoHeadroom) {
                            updateAutoHeadroom()
                        }
                    } else {
                        updateGainLabel(tvGain, progress)
                        lastUserProgress = progress
                        wasInDetent = (progress in 118..122)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    lastUserProgress = seekBar?.progress ?: 120
                    wasInDetent = (lastUserProgress in 118..122)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            bandsContainer.addView(bandView)
        }

        // Setup Controls
        setupPresetControls()
        setupPreampControls()
        setupDrawControls()
        setupSmoothControls()

        // Reset to flat button
        btnReset.setOnClickListener {
            applyPresetByName("Flat")
        }

        // Dynamically set SeekBar width to match its container's height so it stretches perfectly
        bandsContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                bandsContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                for (i in frequencies.indices) {
                    val bandView = bandsContainer.getChildAt(i)
                    val frameLayout = bandView.findViewById<FrameLayout>(R.id.seek_bar_container)
                    val seekBar = bandView.findViewById<SeekBar>(R.id.seek_bar_band)
                    val lp = seekBar.layoutParams
                    lp.width = frameLayout.height
                    seekBar.layoutParams = lp
                }
            }
        })

        // Start service if enabled on launch
        if (isEnabled) {
            val intent = Intent(this, EqService::class.java)
            intent.action = "ACTION_START"
            startForegroundService(intent)
        }
    }

    private fun setupPresetControls() {
        refreshPresetsSpinner()

        spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in presetsList.indices) {
                    val preset = presetsList[position]
                    btnRenamePreset.isEnabled = preset.isCustom
                    btnDeletePreset.isEnabled = preset.isCustom
                    if (isFirstSpinnerSelection) {
                        isFirstSpinnerSelection = false
                        return
                    }
                    applyPreset(preset)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSavePreset.setOnClickListener {
            showPresetTitleDialog(isRename = false)
        }

        btnRenamePreset.setOnClickListener {
            val currentPos = spinnerPresets.selectedItemPosition
            if (currentPos in presetsList.indices) {
                val preset = presetsList[currentPos]
                if (preset.isCustom) {
                    showPresetTitleDialog(isRename = true, existingPreset = preset)
                }
            }
        }

        btnDeletePreset.setOnClickListener {
            val currentPos = spinnerPresets.selectedItemPosition
            if (currentPos in presetsList.indices) {
                val preset = presetsList[currentPos]
                if (preset.isCustom) {
                    showDeletePresetDialog(preset)
                } else {
                    Toast.makeText(this, "Default presets cannot be deleted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshPresetsSpinner(presetToSelect: String? = null) {
        presetsList = PresetManager.getAllPresets(sharedPrefs)
        val names = presetsList.map { it.name }

        val adapter = ArrayAdapter(this, R.layout.item_preset_spinner, names)
        adapter.setDropDownViewResource(R.layout.item_preset_dropdown)
        spinnerPresets.adapter = adapter

        val targetName = presetToSelect ?: sharedPrefs.getString(PresetManager.PREF_KEY_CURRENT_PRESET, "Flat")
        val selectIndex = presetsList.indexOfFirst { it.name.equals(targetName, ignoreCase = true) }
        val targetIndex = if (selectIndex >= 0) selectIndex else 0
        spinnerPresets.setSelection(targetIndex)
        val isCustom = presetsList.getOrNull(targetIndex)?.isCustom == true
        btnRenamePreset.isEnabled = isCustom
        btnDeletePreset.isEnabled = isCustom
    }

    private fun applyPresetByName(name: String) {
        val index = presetsList.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (index >= 0) {
            spinnerPresets.setSelection(index)
            applyPreset(presetsList[index])
        } else {
            applyGains(IntArray(31) { 120 })
        }
    }

    private fun applyPreset(preset: EqPreset) {
        sharedPrefs.edit().putString(PresetManager.PREF_KEY_CURRENT_PRESET, preset.name).apply()
        applyGains(preset.gains)
    }

    private fun applyGains(gains: IntArray) {
        isUpdatingBandsProgrammatically = true
        val editor = sharedPrefs.edit()
        for (i in 0 until minOf(gains.size, seekBars.size)) {
            val progress = gains[i]
            seekBars[i].progress = progress
            updateGainLabel(tvGains[i], progress)
            editor.putInt("band_$i", progress)
        }
        editor.apply()
        isUpdatingBandsProgrammatically = false

        if (isAutoHeadroom) {
            updateAutoHeadroom()
        }

        // Update DSP service
        val updateIntent = Intent(this, EqService::class.java)
        updateIntent.action = "ACTION_UPDATE_BANDS"
        startService(updateIntent)
    }

    private fun getCurrentGains(): IntArray {
        return IntArray(seekBars.size) { i -> seekBars[i].progress }
    }

    private fun setupPreampControls() {
        isAutoHeadroom = sharedPrefs.getBoolean("auto_headroom", false)
        switchAutoHeadroom.isChecked = isAutoHeadroom
        seekBarPreamp.isEnabled = !isAutoHeadroom

        val savedPreamp = sharedPrefs.getInt("preamp_gain", 120)
        seekBarPreamp.progress = savedPreamp
        updatePreampLabel(savedPreamp)

        switchAutoHeadroom.setOnCheckedChangeListener { _, isChecked ->
            isAutoHeadroom = isChecked
            sharedPrefs.edit().putBoolean("auto_headroom", isChecked).apply()
            seekBarPreamp.isEnabled = !isChecked
            if (isChecked) {
                updateAutoHeadroom()
            } else {
                val current = seekBarPreamp.progress
                updatePreampLabel(current)
                EqService.updatePreamp(EqHelper.progressToGainDb(current))
            }
        }

        var lastPreampUserProgress = savedPreamp
        var wasPreampInDetent = (savedPreamp in 118..122)

        seekBarPreamp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isAutoHeadroom) {
                    var effectiveProgress = progress
                    val isNearCenter = progress in 118..122
                    val crossedCenter = (lastPreampUserProgress < 120 && progress > 120) || (lastPreampUserProgress > 120 && progress < 120)

                    if (isNearCenter) {
                        effectiveProgress = 120
                        if (!wasPreampInDetent) {
                            triggerCenterDetentHaptic(seekBar ?: tvPreampGain)
                            wasPreampInDetent = true
                        }
                        if (progress != 120) {
                            seekBar?.progress = 120
                            return
                        }
                    } else if (crossedCenter) {
                        triggerCenterDetentHaptic(seekBar ?: tvPreampGain)
                        wasPreampInDetent = false
                    } else {
                        wasPreampInDetent = false
                    }

                    lastPreampUserProgress = effectiveProgress
                    updatePreampLabel(effectiveProgress)
                    sharedPrefs.edit().putInt("preamp_gain", effectiveProgress).apply()
                    EqService.updatePreamp(EqHelper.progressToGainDb(effectiveProgress))
                } else {
                    updatePreampLabel(progress)
                    lastPreampUserProgress = progress
                    wasPreampInDetent = (progress in 118..122)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                lastPreampUserProgress = seekBar?.progress ?: 120
                wasPreampInDetent = (lastPreampUserProgress in 118..122)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        if (isAutoHeadroom) {
            updateAutoHeadroom()
        }
    }

    private fun updatePreampLabel(progress: Int) {
        val gainDb = EqHelper.progressToGainDb(progress)
        tvPreampGain.text = String.format("Preamp: %.1f dB", gainDb)
    }

    private fun updateAutoHeadroom() {
        val currentGains = getCurrentGains()
        val autoProgress = EqHelper.calculateAutoHeadroomProgress(currentGains)
        val autoGainDb = EqHelper.progressToGainDb(autoProgress)

        seekBarPreamp.progress = autoProgress
        tvPreampGain.text = String.format("Preamp: %.1f dB", autoGainDb)
        sharedPrefs.edit().putInt("preamp_gain", autoProgress).apply()
        EqService.updatePreamp(autoGainDb)
    }

    private fun setupDrawControls() {
        btnDrawCurve.setOnClickListener {
            isDrawMode = !isDrawMode
            if (isDrawMode) {
                btnDrawCurve.text = "✓ Drawing"
                btnDrawCurve.setBackgroundResource(R.drawable.draw_active_bg)
                drawOverlay.visibility = View.VISIBLE
                Toast.makeText(this, "Drag across bands to sculpt curve", Toast.LENGTH_SHORT).show()
            } else {
                stopDrawingMode()
            }
        }

        drawOverlay.setOnTouchListener { _, event ->
            if (!isDrawMode) return@setOnTouchListener false

            lastTouchRawX = event.rawX
            lastTouchRawY = event.rawY

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastDrawBandIndex = null
                    lastDrawProgress = null
                    handleDrawTouch(event.rawX, event.rawY)
                    checkEdgeScroll(event.x)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    handleDrawTouch(event.rawX, event.rawY)
                    checkEdgeScroll(event.x)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopEdgeScroll()
                    lastDrawBandIndex = null
                    lastDrawProgress = null
                    onDrawTouchFinished()
                    true
                }
                else -> false
            }
        }
    }

    private fun stopDrawingMode() {
        isDrawMode = false
        btnDrawCurve.text = "✏️ Draw"
        if (defaultDrawButtonBg != null) {
            btnDrawCurve.background = defaultDrawButtonBg
        } else {
            btnDrawCurve.setBackgroundResource(android.R.drawable.btn_default)
        }
        drawOverlay.visibility = View.GONE
        stopEdgeScroll()
    }

    private fun checkEdgeScroll(localX: Float) {
        val overlayWidth = drawOverlay.width
        if (overlayWidth <= 0) return
        val edgeThreshold = 120 // px from edge

        if (localX > overlayWidth - edgeThreshold) {
            val speedFactor = ((localX - (overlayWidth - edgeThreshold)) / edgeThreshold).coerceIn(0.1f, 1.0f)
            edgeScrollDelta = (speedFactor * 30).toInt().coerceAtLeast(8)
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
            edgeScrollHandler.post(edgeScrollRunnable)
        } else if (localX < edgeThreshold) {
            val speedFactor = ((edgeThreshold - localX) / edgeThreshold).coerceIn(0.1f, 1.0f)
            edgeScrollDelta = -(speedFactor * 30).toInt().coerceAtLeast(8)
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
            edgeScrollHandler.post(edgeScrollRunnable)
        } else {
            stopEdgeScroll()
        }
    }

    private fun stopEdgeScroll() {
        edgeScrollDelta = 0
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable)
    }

    private fun handleDrawTouch(rawX: Float, rawY: Float) {
        if (bandsContainer.childCount == 0) return
        val sampleChild = bandsContainer.getChildAt(0) ?: return
        val sampleContainer = sampleChild.findViewById<View>(R.id.seek_bar_container) ?: return

        val bandsLocation = IntArray(2)
        bandsContainer.getLocationOnScreen(bandsLocation)

        val containerLocation = IntArray(2)
        sampleContainer.getLocationOnScreen(containerLocation)

        val bandWidth = sampleChild.width.toFloat().coerceAtLeast(1f)
        val relativeX = rawX - bandsLocation[0]
        val bandIndex = (relativeX / bandWidth).toInt().coerceIn(0, frequencies.size - 1)

        val containerTop = containerLocation[1].toFloat()
        val containerHeight = sampleContainer.height.toFloat().coerceAtLeast(1f)
        val relativeY = (rawY - containerTop).coerceIn(0f, containerHeight)
        val rawProgress = (240f * (1f - (relativeY / containerHeight))).roundToInt().coerceIn(0, 240)

        var effectiveProgress = rawProgress
        if (effectiveProgress in 118..122) {
            effectiveProgress = 120
        }

        val prevBand = lastDrawBandIndex ?: bandIndex
        val prevProgress = lastDrawProgress ?: effectiveProgress

        if (prevBand == bandIndex) {
            applyBandProgress(bandIndex, effectiveProgress)
        } else {
            val step = if (bandIndex > prevBand) 1 else -1
            var b = prevBand
            while (b != bandIndex) {
                b += step
                val fraction = (b - prevBand).toFloat() / (bandIndex - prevBand).toFloat()
                var interpProg = (prevProgress + fraction * (effectiveProgress - prevProgress)).roundToInt().coerceIn(0, 240)
                if (interpProg in 118..122) interpProg = 120
                applyBandProgress(b, interpProg)
            }
        }

        lastDrawBandIndex = bandIndex
        lastDrawProgress = effectiveProgress
    }

    private fun applyBandProgress(bandIndex: Int, progress: Int) {
        if (bandIndex !in seekBars.indices) return
        val seekBar = seekBars[bandIndex]
        val tvGain = tvGains[bandIndex]

        if (seekBar.progress != progress) {
            val oldProg = seekBar.progress
            seekBar.progress = progress
            updateGainLabel(tvGain, progress)
            sharedPrefs.edit().putInt("band_$bandIndex", progress).apply()
            EqService.updateBand(bandIndex, EqHelper.progressToGainDb(progress))

            if (progress == 120 && oldProg != 120) {
                triggerCenterDetentHaptic(seekBar)
            }

            if (isAutoHeadroom) {
                updateAutoHeadroom()
            }
        }
    }

    private fun onDrawTouchFinished() {
        val editor = sharedPrefs.edit()
        for (i in seekBars.indices) {
            editor.putInt("band_$i", seekBars[i].progress)
        }
        editor.apply()

        if (isAutoHeadroom) {
            updateAutoHeadroom()
        }

        val updateIntent = Intent(this, EqService::class.java)
        updateIntent.action = "ACTION_UPDATE_BANDS"
        startService(updateIntent)
    }

    private fun setupSmoothControls() {
        btnSmoothCurve.setOnClickListener {
            val currentGains = getCurrentGains()
            val smoothed = EqHelper.smoothGains(currentGains)
            applyGains(smoothed)
            Toast.makeText(this, "Curve smoothed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        stopEdgeScroll()
        super.onDestroy()
    }

    private fun showPresetTitleDialog(isRename: Boolean, existingPreset: EqPreset? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_preset_name, null)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_preset_title)

        if (isRename) {
            tvMessage.text = "Enter a new title for '${existingPreset?.name}':"
            etTitle.setText(existingPreset?.name ?: "")
        } else {
            val currentPos = spinnerPresets.selectedItemPosition
            val currentPreset = presetsList.getOrNull(currentPos)
            val defaultName = if (currentPreset?.isCustom == true) currentPreset.name else ""
            tvMessage.text = "Enter a title for your custom preset:"
            etTitle.setText(defaultName)
        }
        etTitle.selectAll()

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isRename) "Rename Preset" else "Save Preset")
            .setView(dialogView)
            .setPositiveButton(if (isRename) "Rename" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            etTitle.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etTitle, InputMethodManager.SHOW_IMPLICIT)

            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveBtn.setOnClickListener {
                val title = etTitle.text.toString().trim()
                if (title.isEmpty()) {
                    etTitle.error = "Title cannot be empty"
                    return@setOnClickListener
                }
                val isDefaultName = PresetManager.DEFAULT_PRESETS.any { it.name.equals(title, ignoreCase = true) }
                if (isDefaultName) {
                    etTitle.error = "Cannot use default preset name '$title'"
                    return@setOnClickListener
                }

                if (isRename && existingPreset != null) {
                    PresetManager.renameCustomPreset(sharedPrefs, existingPreset.name, title)
                    Toast.makeText(this, "Preset renamed to '$title'", Toast.LENGTH_SHORT).show()
                } else {
                    val currentGains = getCurrentGains()
                    PresetManager.saveCustomPreset(sharedPrefs, title, currentGains)
                    Toast.makeText(this, "Preset '$title' saved", Toast.LENGTH_SHORT).show()
                }

                refreshPresetsSpinner(presetToSelect = title)
                dialog.dismiss()
            }
        }

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    private fun showDeletePresetDialog(preset: EqPreset) {
        AlertDialog.Builder(this)
            .setTitle("Delete Preset")
            .setMessage("Are you sure you want to delete '${preset.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                PresetManager.deleteCustomPreset(sharedPrefs, preset.name)
                refreshPresetsSpinner(presetToSelect = "Flat")
                applyPresetByName("Flat")
                Toast.makeText(this, "Preset '${preset.name}' deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGainLabel(tvGain: TextView, progress: Int) {
        val gainDb = EqHelper.progressToGainDb(progress)
        tvGain.text = String.format("%.1f dB", gainDb)
    }

    private var vibrator: Vibrator? = null

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun triggerCenterDetentHaptic(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    return
                }
            }
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
