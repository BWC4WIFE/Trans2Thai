// app/src/main/java/com/Trans2Thai/SettingsDialog.kt
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
    // FIX: Imported MainActivity.LanguageOption
    private val languages: List<MainActivity.LanguageOption>
) : Dialog(context) {

    private lateinit var binding: DialogSettingsBinding
    private var apiKeysList: List<ApiKeyInfo> = emptyList()
    private var selectedModel: String = ""
    // FIX: Added missing member variable declaration
    private var selectedApiKeyInfo: ApiKeyInfo? = null
    
    private var selectedSourceLang: MainActivity.LanguageOption? = null
    private var selectedTargetLang: MainActivity.LanguageOption? = null
    
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

        val currentModel = prefs.getString("selected_model", models.firstOrNull())
        selectedModel = models.firstOrNull { it == currentModel } ?: models.first()

        val sourceLangTag = prefs.getString("source_language", Locale.ENGLISH.toLanguageTag())
        val targetLangTag = prefs.getString("target_language", Locale("th", "TH").toLanguageTag())
        selectedSourceLang = languages.find { it.locale.toLanguageTag() == sourceLangTag } ?: languages.first()
        selectedTargetLang = languages.find { it.locale.toLanguageTag() == targetLangTag } ?: languages.first { it.locale.language == "th" }
        
        setupViews()
    }
     

    private fun loadApiKeysFromResources() {
        val rawApiKeys = context.resources.getStringArray(R.array.api_keys)
        apiKeysList = rawApiKeys.mapNotNull { itemString ->
            val parts = itemString.split(":", limit = 2)
            if (parts.size == 2) ApiKeyInfo(parts[0].trim(), parts[1].trim()) else {
                Log.e(TAG, "Malformed API key item in resources: '$itemString'.")
                null
            }
        }
        val currentApiKeyValue = prefs.getString("api_key", null)
        selectedApiKeyInfo = apiKeysList.firstOrNull { it.value == currentApiKeyValue } ?: apiKeysList.firstOrNull()
        Log.d(TAG, "loadApiKeys: Loaded ${apiKeysList.size} items. Initial selected: ${selectedApiKeyInfo?.displayName}")
    }

    private fun setupViews() {
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

        // Setup Model Spinner
        binding.modelSpinnerSettings.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, models)
        val modelPosition = models.indexOf(selectedModel)
        if (modelPosition != -1) {
            binding.modelSpinnerSettings.setSelection(modelPosition)
        }
        binding.modelSpinnerSettings.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedModel = models[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
		
		val languageAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, languages)
		
	
        binding.sourceLanguageSpinner.adapter = languageAdapter
        val sourcePos = languages.indexOf(selectedSourceLang)
        if (sourcePos != -1) binding.sourceLanguageSpinner.setSelection(sourcePos)
        
        binding.targetLanguageSpinner.adapter = languageAdapter
        val targetPos = languages.indexOf(selectedTargetLang)
        if (targetPos != -1) binding.targetLanguageSpinner.setSelection(targetPos)

        // Hide API Version spinner as it's not needed for the standard v1beta endpoint
        // NOTE: This line will fail if the view ID is not added to the TextView in dialog_settings.xml
        binding.apiVersionSpinner.visibility = View.GONE
        binding.apiVersionLabel.visibility = View.GONE

        // Setup API Key Spinner
        binding.apiKeySpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, apiKeysList)
        selectedApiKeyInfo?.let { initialSelection ->
            val apiKeyPosition = apiKeysList.indexOf(initialSelection)
            if (apiKeyPosition != -1) {
                binding.apiKeySpinner.setSelection(apiKeyPosition)
            }
        }

        binding.saveSettingsBtn.setOnClickListener {
            prefs.edit().apply {
                putInt("vad_sensitivity_ms", binding.vadSensitivity.progress)
                putString("selected_model", models[binding.modelSpinnerSettings.selectedItemPosition])
                
                val sourceLang = languages[binding.sourceLanguageSpinner.selectedItemPosition]
                putString("source_language", sourceLang.locale.toLanguageTag())
                
                val targetLang = languages[binding.targetLanguageSpinner.selectedItemPosition]
                putString("target_language", targetLang.locale.toLanguageTag())
                
                if (apiKeysList.isNotEmpty()) {
                    val selectedApiKey = apiKeysList[binding.apiKeySpinner.selectedItemPosition]
                    putString("api_key", selectedApiKey.value)
                }
                
                apply()
            }
            dismiss()
        }
    }
}
