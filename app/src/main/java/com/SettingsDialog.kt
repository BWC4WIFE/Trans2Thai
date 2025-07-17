package com.Trans2Thai

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import com.Trans2Thai.databinding.DialogSettingsBinding
import java.util.Locale

class SettingsDialog(
    context: Context,
    private val prefs: SharedPreferences,
    private val models: List<String>,
    private val languages: List<MainActivity.LanguageOption>
) : Dialog(context) {

    private lateinit var binding: DialogSettingsBinding
    private var apiKeysList: List<ApiKeyInfo> = emptyList()
    private var apiVersions: List<String> = emptyList()
    private var selectedModel: String = ""
    private var selectedApiKeyInfo: ApiKeyInfo? = null
    private var selectedSourceLang: MainActivity.LanguageOption? = null
    private var selectedTargetLang: MainActivity.LanguageOption? = null
    private var selectedApiVersion: String = ""
    
    companion object {
        private const val TAG = "SettingsDialog"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCancelable(true)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        loadApiKeysFromResources()
        loadApiVersionsFromResources()

        val currentModel = prefs.getString("selected_model", models.firstOrNull())
        selectedModel = models.firstOrNull { it == currentModel } ?: models.first()

        val sourceLangTag = prefs.getString("source_language", Locale.ENGLISH.toLanguageTag())
        val targetLangTag = prefs.getString("target_language", Locale("th", "TH").toLanguageTag())
        selectedSourceLang = languages.find { it.locale.toLanguageTag() == sourceLangTag }
        selectedTargetLang = languages.find { it.locale.toLanguageTag() == targetLangTag }
        selectedApiVersion = prefs.getString("api_version", apiVersions.firstOrNull() ?: "") ?: ""

        setupViews()
    }
     
    private fun loadApiKeysFromResources() {
        val rawApiKeys = context.resources.getStringArray(R.array.api_keys)
        apiKeysList = rawApiKeys.mapNotNull { itemString ->
            val parts = itemString.split(":", limit = 2)
            if (parts.size == 2) ApiKeyInfo(parts[0].trim(), parts[1].trim()) else null
        }
        val currentApiKeyValue = prefs.getString("api_key", null)
        selectedApiKeyInfo = apiKeysList.firstOrNull { it.value == currentApiKeyValue } ?: apiKeysList.firstOrNull()
    }

        private fun loadApiVersionsFromResources() {
        apiVersions = context.resources.getStringArray(R.array.api_versions).toList()
    }

    private fun setupViews() {
        // VAD Sensitivity
        val currentVad = prefs.getInt("vad_sensitivity_ms", 1200)
        binding.vadSensitivity.progress = currentVad
        binding.vadValue.text = "$currentVad ms"
        binding.vadSensitivity.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.vadValue.text = "$progress ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Model Spinner
        binding.modelSpinnerSettings.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, models)
        models.indexOf(selectedModel).takeIf { it != -1 }?.let { binding.modelSpinnerSettings.setSelection(it) }

        // Language Spinners
        val languageAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, languages)
        binding.sourceLanguageSpinner.adapter = languageAdapter
        languages.indexOf(selectedSourceLang).takeIf { it != -1 }?.let { binding.sourceLanguageSpinner.setSelection(it) }
        binding.targetLanguageSpinner.adapter = languageAdapter
        languages.indexOf(selectedTargetLang).takeIf { it != -1 }?.let { binding.targetLanguageSpinner.setSelection(it) }

        // API Key Spinner
        binding.apiKeySpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, apiKeysList)
        apiKeysList.indexOf(selectedApiKeyInfo).takeIf { it != -1 }?.let { binding.apiKeySpinner.setSelection(it) }

        // API Version Spinner
        binding.apiVersionSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, apiVersions)
        apiVersions.indexOf(selectedApiVersion).takeIf { it != -1}?.let { binding.apiVersionSpinner.setSelection(it)}

        // ** NEW: Load state for the Thai Prompt Switch **
        val useThaiPrompt = prefs.getBoolean("use_thai_prompt", false)
        binding.thaiPromptSwitch.isChecked = useThaiPrompt

        // Save Button
        binding.saveSettingsBtn.setOnClickListener {
            prefs.edit().apply {
                putInt("vad_sensitivity_ms", binding.vadSensitivity.progress)
                putString("selected_model", models[binding.modelSpinnerSettings.selectedItemPosition])
                putString("source_language", languages[binding.sourceLanguageSpinner.selectedItemPosition].locale.toLanguageTag())
                putString("target_language", languages[binding.targetLanguageSpinner.selectedItemPosition].locale.toLanguageTag())
                if (apiKeysList.isNotEmpty()) {
                    putString("api_key", apiKeysList[binding.apiKeySpinner.selectedItemPosition].value)
                }
                if(apiVersions.isNotEmpty()){
                    putString("api_version", apiVersions[binding.apiVersionSpinner.selectedItemPosition])
                }
                // ** NEW: Save state for the Thai Prompt Switch **
                putBoolean("use_thai_prompt", binding.thaiPromptSwitch.isChecked)
                apply()
            }
            dismiss()
        }
    }
}
