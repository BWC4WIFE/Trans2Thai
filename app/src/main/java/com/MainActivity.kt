// app/src/main/java/com/Trans2Thai/MainActivity.kt
package com.Trans2Thai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.Trans2Thai.databinding.ActivityMainBinding
import com.Trans2Thai.output.TextToSpeechManager
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // --- Instance Variables ---
    private lateinit var binding: ActivityMainBinding
    // FIX: Corrected import paths to use the consistent 'com.Trans2Thai' package
    private lateinit var audioHandler: AudioHandler
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var translationAdapter: TranslationAdapter
    private lateinit var geminiApiClient: GeminiApiClient
    private lateinit var ttsManager: TextToSpeechManager

    private val mainScope = CoroutineScope(Dispatchers.Main)

    // --- State Management ---
    @Volatile private var isListening = false
    @Volatile private var isProcessing = false
    @Volatile private var isTypingMode = false
    @Volatile private var isTtsReady = false
    private val audioBuffer = ByteArrayOutputStream()
    private val silenceHandler = Handler(Looper.getMainLooper())
    private var vadRunnable: Runnable? = null

    // --- Configuration ---
    // FIX: Removed hardcoded models list, will be loaded from resources.
    private var selectedModel: String = ""
    private var apiKeys: List<ApiKeyInfo> = emptyList()
    private var selectedApiKeyInfo: ApiKeyInfo? = null
    private var sourceLanguage: Locale = Locale.ENGLISH
    private var targetLanguage: Locale = Locale("th", "TH")

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // --- Constants ---
    companion object {
        private const val TAG = "MainActivity"
        private const val THAI_SYSTEM_PROMPT = "You are an expert translator. Translate the user's audio from English to Thai. Your response must include the translated audio in MP3 format and the translated text."
        private const val GENERIC_SYSTEM_PROMPT_TEMPLATE = "You are an expert translator. Translate the user's input from %s to %s. Your response must include the translated audio in MP3 format and the translated text."
        private const val REQUESTED_AUDIO_MIMETYPE = "audio/mp3"
        private const val RECORDED_AUDIO_MIMETYPE = "audio/wav"
    }

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate: Activity created.")

        loadApiKeysFromResources()
        loadPreferences()

        audioPlayer = AudioPlayer()
        geminiApiClient = GeminiApiClient()
        ttsManager = TextToSpeechManager(this) {
            isTtsReady = true
            updateTtsLanguage()
        }

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(TAG, "RECORD_AUDIO permission granted.")
                initializeAudioHandler()
            } else {
                Log.e(TAG, "RECORD_AUDIO permission denied.")
                showError("Microphone permission is required for this app to function.")
            }
        }

        checkPermissions()
        setupUI()
        updateDisplayInfo()
    }

    override fun onPause() {
        super.onPause()
        cancelSilenceTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "onDestroy: Activity is being destroyed.")
        cancelSilenceTimer()
        if (::audioHandler.isInitialized) audioHandler.stopRecording()
        audioPlayer.release()
        ttsManager.shutdown()
        mainScope.cancel()
    }

    // --- Initialization and Configuration ---
    private fun loadPreferences() {
        val prefs = getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE)
        // FIX: Ensure R.array.models exists in strings.xml
        val models = resources.getStringArray(R.array.models).toList()
        selectedModel = prefs.getString("selected_model", models.firstOrNull()) ?: models.firstOrNull() ?: ""
        val sourceLangTag = prefs.getString("source_language", Locale.ENGLISH.toLanguageTag())
        val targetLangTag = prefs.getString("target_language", Locale("th", "TH").toLanguageTag())
        sourceLanguage = Locale.forLanguageTag(sourceLangTag ?: "en")
        targetLanguage = Locale.forLanguageTag(targetLangTag ?: "th-TH")
        Log.d(TAG, "Loaded Prefs: Model='$selectedModel', Source='$sourceLanguage', Target='$targetLanguage'")
    }

    // FIX: Consolidated duplicated function and corrected variable name.
    private fun loadApiKeysFromResources() {
        val rawApiKeys = resources.getStringArray(R.array.api_keys)
        apiKeys = rawApiKeys.mapNotNull { itemString ->
            val parts = itemString.split(":", limit = 2)
            if (parts.size == 2) ApiKeyInfo(parts[0].trim(), parts[1].trim()) else null
        }.toList()

        val storedKey = getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE).getString("api_key", null)
        selectedApiKeyInfo = apiKeys.firstOrNull { it.value == storedKey } ?: apiKeys.firstOrNull()
        Log.d(TAG, "loadApiKeys: Loaded ${apiKeys.size} items. Initial selected: ${selectedApiKeyInfo?.displayName}")
    }

    private fun initializeAudioHandler() {
        if (!::audioHandler.isInitialized) {
            audioHandler = AudioHandler(this) { audioData ->
                if (isListening) {
                    mainScope.launch(Dispatchers.IO) {
                        audioBuffer.write(audioData)
                    }
                    resetSilenceTimer()
                }
            }
            Log.i(TAG, "AudioHandler initialized.")
            updateUI()
        }
    }

    // --- UI Setup and Updates ---
    private fun setupUI() {
        translationAdapter = TranslationAdapter()
        binding.transcriptLog.layoutManager = LinearLayoutManager(this).apply { reverseLayout = true }
        binding.transcriptLog.adapter = translationAdapter

        binding.englishButton.setOnClickListener {
            sourceLanguage = Locale.ENGLISH
            targetLanguage = Locale("th", "TH")
            updateDisplayInfo() // To reflect the change in the UI
            Toast.makeText(this, "Source: English, Target: Thai", Toast.LENGTH_SHORT).show()
        }
        
        binding.thaiButton.setOnClickListener {
            sourceLanguage = Locale("th", "TH")
            targetLanguage = Locale.ENGLISH
            updateDisplayInfo() // To reflect the change in the UI
            Toast.makeText(this, "Source: Thai, Target: English", Toast.LENGTH_SHORT).show()
    }
        // FIX: Changed binding.micBtn to binding.mainMicButton to match activity_main.xml
        binding.mainMicButton.setOnClickListener { handleMasterButton() }
        binding.sendTextBtn.setOnClickListener { handleSendText() }
        // FIX: Changed binding.settingsBtn to binding.settingsButton to match activity_main.xml
        binding.settingsButton.setOnClickListener { showSettingsDialog() }

        binding.typingModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            isTypingMode = isChecked
            if (isListening) {
                stopRecordingAndTranslate(isSwitchingMode = true)
            }
            updateUI()
        }
        updateUI()
    }

    // This function was defined but never called.
    // It is preserved here in case you intend to implement a feature that uses it.
    private fun updateTranscription(language: String, transcription: String) {
        when (language) {
            "Thai" -> binding.thaiTranscriptionTextView.text = transcription
            "English" -> binding.englishTranscriptionTextView.text = transcription
        }
    }

    private fun updateUI() {
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (isTypingMode) {
            binding.mainMicButton.visibility = View.GONE
            binding.textInputContainer.visibility = View.VISIBLE
            binding.sendTextBtn.isEnabled = !isProcessing
        } else {
            binding.mainMicButton.visibility = View.VISIBLE
            binding.textInputContainer.visibility = View.GONE
            binding.mainMicButton.isEnabled = hasPermission && !isProcessing

            // FIX: An ImageButton does not have a 'text' property.
 
            if (isListening) {
  
    binding.mainMicButton.setColorFilter(ContextCompat.getColor(this, R.color.listening_color), android.graphics.PorterDuff.Mode.SRC_IN)
   
    // binding.mainMicButton.startAnimation(pulseAnimation)
} else {
    binding.mainMicButton.clearColorFilter()
    // binding.mainMicButton.clearAnimation()
}
            if (isProcessing) {
                 binding.mainMicButton.isEnabled = false // Visually show it's busy
            } 
            binding.processingSpinner.visibility = if (isProcessing) View.VISIBLE else View.GONE
binding.dualLanguagePanel.alpha = if (isProcessing) 0.5f else 1.0f // Optional: make the panel semi-transparent
            else if (isListening) {
                // binding.mainMicButton.setImageResource(R.drawable.ic_stop)
            } else {
                // binding.mainMicButton.setImageResource(R.drawable.ic_microphone)
            }
        }
        binding.settingsButton.isEnabled = !isListening && !isProcessing
    }

    private fun updateStatus(message: String) {
        binding.statusText.text = "Status: $message"
        Log.i(TAG, "Status Updated: $message")
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateStatus("Alert: $message")
        Log.e(TAG, "showError: $message")
    }

    private fun updateDisplayInfo() {
        val prefs = getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE)
        val currentApiKey = apiKeys.firstOrNull { it.value == prefs.getString("api_key", null) } ?: apiKeys.firstOrNull()
        val infoText = "Model: $selectedModel | Key: ${currentApiKey?.displayName ?: "N/A"}"
        // FIX: The ID 'configDisplay' was not in the original XML.
        // This line will work once you add a TextView with @+id/configDisplay to your layout.
        // binding.configDisplay.text = infoText
        Log.d(TAG, "updateDisplayInfo: $infoText")
    }

    // --- Audio Recording and Processing ---
    private fun handleMasterButton() {
        if (isProcessing) return
        isListening = !isListening
        if (isListening) startRecording()
        else stopRecordingAndTranslate()
        updateUI()
    }

    private fun startRecording() {
        Log.i(TAG, "startRecording: Starting audio recording.")
        audioBuffer.reset()
        audioHandler.startRecording()
        updateStatus("Listening...")
        resetSilenceTimer()
    }

    private fun stopRecordingAndTranslate(isSwitchingMode: Boolean = false) {
        if (!isListening && !isProcessing) return
        isListening = false
        cancelSilenceTimer()
        audioHandler.stopRecording()

        val audioData = audioBuffer.toByteArray()
        if (audioData.isEmpty() || isSwitchingMode) {
            if (!isSwitchingMode) Log.w(TAG, "Audio buffer is empty, not sending request.")
            updateStatus("Ready")
            updateUI()
            return
        }

        isProcessing = true
        updateStatus("Translating audio...")
        updateUI()
        translationAdapter.addOrUpdateTranslation("(Your voice input)", true)
        sendApiRequest(textInput = null, audioData = audioData)
    }

    private fun resetSilenceTimer() {
        cancelSilenceTimer()
        vadRunnable = Runnable {
            if (isListening) {
                Log.i(TAG, "VAD: Silence detected, stopping recording.")
                mainScope.launch { // Ensure UI update is on the main thread
                    stopRecordingAndTranslate()
                }
            }
        }.also {
            silenceHandler.postDelayed(it, getVadSensitivity().toLong())
        }
    }

    private fun cancelSilenceTimer() {
        vadRunnable?.let { silenceHandler.removeCallbacks(it) }
        vadRunnable = null
    }

    private fun getVadSensitivity(): Int {
        return getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE).getInt("vad_sensitivity_ms", 1200)
    }

    // --- Text Input Processing ---
    private fun handleSendText() {
        val text = binding.textInputEdittext.text.toString().trim()
        if (text.isEmpty()) {
            showError("Please enter text to translate.")
            return
        }
        if (isProcessing) return

        binding.textInputEdittext.text.clear()
        isProcessing = true
        updateStatus("Translating text...")
        updateUI()
        translationAdapter.addOrUpdateTranslation(text, true)
        sendApiRequest(textInput = text, audioData = null)
    }

    // --- API Communication ---
    private fun generateSystemPrompt(): String {
        return String.format(
            GENERIC_SYSTEM_PROMPT_TEMPLATE,
            sourceLanguage.displayLanguage,
            targetLanguage.displayLanguage
        )
    }

    private fun sendApiRequest(textInput: String?, audioData: ByteArray?) {
        val apiKey = selectedApiKeyInfo?.value
        if (apiKey.isNullOrEmpty()) {
            showError("API Key is not set. Please configure it in Settings.")
            isProcessing = false
            updateUI()
            return
        }

        mainScope.launch {
            val parts = mutableListOf<Part>()
            if (textInput != null) {
                parts.add(Part(text = textInput))
            }
            if (audioData != null) {
                val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
                parts.add(Part(inlineData = Blob(mimeType = RECORDED_AUDIO_MIMETYPE, data = audioBase64)))
            }

            if (parts.isEmpty()) {
                showError("No input provided.")
                isProcessing = false
                updateUI()
                return@launch
            }

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = generateSystemPrompt())), role = "user"),
                    Content(parts = listOf(Part(text = "Understood. I am ready.")), role = "model"),
                    Content(parts = parts)
                ),
                generationConfig = GenerationConfig(responseMimeType = REQUESTED_AUDIO_MIMETYPE)
            )

            val result = geminiApiClient.generateContent(selectedApiKeyInfo!!.value, selectedModel, request)
            handleApiResponse(result)
        }
    }

    private fun handleApiResponse(result: Result<GenerateContentResponse>) {
        result.onSuccess { response ->
            val content = response.candidates?.firstOrNull()?.content ?: run {
                showError("Received an empty response from the API.")
                return@onSuccess
            }

            val textPart = content.parts.find { it.text != null }?.text
            if (textPart != null) {
                translationAdapter.addOrUpdateTranslation(textPart.trim(), false)
                if (isTypingMode && isTtsReady) {
                    ttsManager.speak(textPart)
                }
            } else {
                translationAdapter.addOrUpdateTranslation("(No text translation received)", false)
            }

            if (!isTypingMode) {
                val audioPart = content.parts.find { it.inlineData != null }?.inlineData
                if (audioPart != null && audioPart.mimeType.startsWith("audio/")) {
                    audioPlayer.playAudio(audioPart.data)
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "API Error", error)
            showError("Translation failed: ${error.message}")
            translationAdapter.addOrUpdateTranslation("(Translation failed)", false)
            updateStatus("Error")
        }

        isProcessing = false
        updateStatus("Ready")
        updateUI()
    }

    // --- Permissions ---
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "checkPermissions: RECORD_AUDIO permission already granted.")
            initializeAudioHandler()
        } else {
            Log.i(TAG, "checkPermissions: Requesting RECORD_AUDIO permission.")
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // --- Settings and Language Options ---
    private fun showSettingsDialog() {
        // FIX: The models list must be loaded from resources.
        val models = resources.getStringArray(R.array.models).toList()
        val languages = getLanguageOptions()
        val dialog = SettingsDialog(this, getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE), models, languages)
        dialog.setOnDismissListener {
            Log.d(TAG, "SettingsDialog dismissed.")
            loadPreferences()
            loadApiKeysFromResources() // Re-load keys and selection after settings change
            updateDisplayInfo()
            updateTtsLanguage()
        }
        dialog.show()
    }
    
    // This is a data class and should ideally be in its own file or ApiModels.kt,
    // but placing it here for simplicity based on the original code structure.
    data class LanguageOption(val displayName: String, val locale: Locale) {
        override fun toString(): String = displayName
    }

    private fun getLanguageOptions(): List<LanguageOption> {
        return listOf(
            LanguageOption("English", Locale.ENGLISH),
            LanguageOption("Thai", Locale("th", "TH")),
            LanguageOption("Spanish", Locale("es", "ES")),
            LanguageOption("French", Locale.FRENCH),
            LanguageOption("German", Locale.GERMAN),
            LanguageOption("Japanese", Locale.JAPANESE),
            LanguageOption("Korean", Locale.KOREAN)
        )
    }

    private fun updateTtsLanguage() {
        if (isTtsReady) {
            ttsManager.setLanguage(targetLanguage)
        }
    }
}
