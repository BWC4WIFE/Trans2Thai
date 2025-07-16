package com.Trans2Thai

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.gemweblive.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import okhttp3.Response
import java.lang.StringBuilder


// --- Data classes for parsing server responses ---
data class ServerResponse(
    @SerializedName("serverContent") val serverContent: ServerContent?,
    @SerializedName("inputTranscription") val inputTranscription: Transcription?,
    @SerializedName("outputTranscription") val outputTranscription: Transcription?,
    @SerializedName("setupComplete") val setupComplete: SetupComplete?,
    @SerializedName("sessionResumptionUpdate") val sessionResumptionUpdate: SessionResumptionUpdate?,
    @SerializedName("goAway") val goAway: GoAway?
)
data class ServerContent(
    @SerializedName("parts") val parts: List<Part>?,
    @SerializedName("modelTurn") val modelTurn: ModelTurn?,
    @SerializedName("inputTranscription") val inputTranscription: Transcription?,
    @SerializedName("outputTranscription") val outputTranscription: Transcription?,
    @SerializedName("turnComplete") val turnComplete: Boolean? // Added to capture turn completion
)
data class ModelTurn(@SerializedName("parts") val parts: List<Part>?)
data class Part(@SerializedName("text") val text: String?, @SerializedName("inlineData") val inlineData: InlineData?)
data class InlineData(@SerializedName("mime_type") val mimeType: String?, @SerializedName("data") val data: String?)
data class Transcription(@SerializedName("text") val text: String?)
data class SetupComplete(val dummy: String? = null)
data class SessionResumptionUpdate(@SerializedName("newHandle") val newHandle: String?, @SerializedName("resumable") val resumable: Boolean?)
data class GoAway(@SerializedName("timeLeft") val timeLeft: String?)


class MainActivity : AppCompatActivity() {

	// Instance Variables ---
    private lateinit var binding: ActivityMainBinding
    private lateinit var audioHandler: AudioHandler
    private var webSocketClient: WebSocketClient? = null
    private lateinit var translationAdapter: TranslationAdapter
    private lateinit var audioPlayer: AudioPlayer
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val gson = Gson()
   
// --- State Management ---
    private var sessionHandle: String? = null
    private val outputTranscriptBuffer = StringBuilder()
    @Volatile private var isListening = false
    @Volatile private var isProcessing = false // New state for processing
    @Volatile private var isSessionActive = false
    @Volatile private var isServerReady = false
    private var sourceLanguage = "en-US" // Default source language
    private var targetLanguage = "th-TH" // Default target language

    // --- Configuration ---
    private val models = listOf(
        "gemini-2.5-flash-preview-native-audio-dialog",
        "gemini-2.5-flash-exp-native-audio-thinking-dialog",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.5-flash-lite-preview",
        "gemini-2.5-flash-lite-preview-06-17",
        "gemini-2.0-flash-lite"
    )
    private var selectedModel: String = ""
    private var apiKeys: List<ApiKeyInfo> = emptyList()
    private var selectedApiKeyInfo: ApiKeyInfo? = null
    private var sourceLanguage: Locale = Locale.ENGLISH
    private var targetLanguage: Locale = Locale("th", "TH")

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var micPulseAnimator: ObjectAnimator? = null // For mic icon animation

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

        geminiApiClient = GeminiApiClient()
        ttsManager = TextToSpeechManager(this) {
            isTtsReady = true
            updateTtsLanguage()
        }

  
                initializeAudioHandler()

        audioPlayer = AudioPlayer()
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(TAG, "RECORD_AUDIO permission granted.")
                initializeComponentsDependentOnAudio()

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
        val models = resources.getStringArray(R.array.models).toList()
        selectedModel = prefs.getString("selected_model", models.first()) ?: models.first()
        val sourceLangTag = prefs.getString("source_language", Locale.ENGLISH.toLanguageTag())
        val targetLangTag = prefs.getString("target_language", Locale("th", "TH").toLanguageTag())
        sourceLanguage = Locale.forLanguageTag(sourceLangTag ?: "en")
        targetLanguage = Locale.forLanguageTag(targetLangTag ?: "th-TH")
        Log.d(TAG, "Loaded Prefs: Model='$selectedModel', Source='$sourceLanguage', Target='$targetLanguage'")
    }

private fun loadApiKeysFromResources() {
    val rawApiKeys = context.resources.getStringArray(R.array.api_keys)
    apiKeys = rawApiKeys.mapNotNull { itemString ->
        val parts = itemString.split(":", limit = 2)
        if (parts.size == 2) ApiKeyInfo(parts[0].trim(), parts[1].trim()) else null
    }.toList() // Convert to list to avoid mutable list issues
    val currentApiKeyValue = prefs.getString("api_key", null)
    selectedApiKeyInfo = apiKeys.firstOrNull { it.value == currentApiKeyValue } ?: apiKeys.firstOrNull()
    Log.d(TAG, "loadApiKeys: Loaded ${apiKeys.size} items. Initial selected: ${selectedApiKeyInfo?.displayName}")
}
        val storedKey = getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE).getString("api_key", null)
        selectedApiKeyInfo = apiKeys.firstOrNull { it.value == storedKey } ?: apiKeys.firstOrNull()
    }

<<<<<<< HEAD
    private fun initializeAudioHandler() {
=======
    private fun loadApiKeysFromResources() {
        val rawApiKeys = resources.getStringArray(R.array.api_keys)
        val parsedList = mutableListOf<ApiKeyInfo>()
        for (itemString in rawApiKeys) {
            val parts = itemString.split(":", limit = 2)
            if (parts.size == 2) parsedList.add(ApiKeyInfo(parts[0].trim(), parts[1].trim()))
        }
        apiKeys = parsedList
        selectedApiKeyInfo = parsedList.firstOrNull { it.value == getSharedPreferences("GemWebLivePrefs", MODE_PRIVATE).getString("api_key", null) } ?: apiKeys.firstOrNull()
        Log.d(TAG, "loadApiKeysFromResources: Loaded ${apiKeys.size} API keys. Selected: ${selectedApiKeyInfo?.displayName}")
    }

private fun setupUI() {
        translationAdapter = TranslationAdapter()
        binding.transcriptLog.layoutManager = LinearLayoutManager(this)
        binding.transcriptLog.adapter = translationAdapter

        binding.micBtn.setOnClickListener {
            Log.d(TAG, "Master button clicked.")
            handleMasterButton()
        }

        binding.englishButton.setOnClickListener {
            sourceLanguage = "en-US"
            targetLanguage = "th-TH"
            updateLanguageButtons()
            Toast.makeText(this, "Translating from English to Thai", Toast.LENGTH_SHORT).show()
        }

        binding.thaiButton.setOnClickListener {
            sourceLanguage = "th-TH"
            targetLanguage = "en-US"
            updateLanguageButtons()
            Toast.makeText(this, "Translating from Thai to English", Toast.LENGTH_SHORT).show()
        }
        updateUI()
        updateLanguageButtons()
        Log.d(TAG, "setupUI: UI components initialized.")
    }

private fun updateLanguageButtons() {
        binding.englishButton.isActivated = sourceLanguage == "en-US"
        binding.thaiButton.isActivated = sourceLanguage == "th-TH"
    }

    private fun initializeComponentsDependentOnAudio() {
>>>>>>> f5db74e12ea86a52eb0819dee0753a7bc6c1fd31
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
<<<<<<< HEAD
=======
        }
        prepareNewClient()
    }

    private fun prepareNewClient() {
        webSocketClient?.disconnect()
        loadPreferences()
        selectedApiVersionObject = apiVersions.firstOrNull { it.value == getSharedPreferences("GemWebLivePrefs", MODE_PRIVATE).getString("api_version", null) } ?: apiVersions.firstOrNull()
        selectedApiKeyInfo = apiKeys.firstOrNull { it.value == getSharedPreferences("GemWebLivePrefs", MODE_PRIVATE).getString("api_key", null) } ?: apiKeys.firstOrNull()

        webSocketClient = WebSocketClient(
            context = applicationContext,
            modelName = selectedModel,
            vadSilenceMs = getVadSensitivity(),
            apiVersion = selectedApiVersionObject?.value ?: "v1beta",
            apiKey = selectedApiKeyInfo?.value ?: "",
            sessionHandle = sessionHandle,
            onOpen = { mainScope.launch {
                Log.i(TAG, "WebSocket onOpen callback received.")
                isSessionActive = true
                reconnectAttempts = 0 // Reset on successful connection
                updateStatus("Connected, configuring server...")
                updateUI()
            } },
            onMessage = { text -> mainScope.launch { processServerMessage(text) } },
            onClosing = { code, reason -> mainScope.launch {
                Log.w(TAG, "WebSocket onClosing callback received: Code=$code, Reason=$reason")
                teardownSession(reconnect = true)
            } },
            onFailure = { t, response -> mainScope.launch {
                Log.e(TAG, "WebSocket onFailure callback received.", t)
                var errorMessage = "Connection error: ${t.message}"
                if (response != null) {
                    errorMessage += "\n(Code: ${response.code})"
                    if (response.code == 404) {
                        errorMessage = "Error: The server endpoint was not found (404). Please check the API version and key."
                    }
                }
                showError(errorMessage)
                teardownSession()
            } },
            onSetupComplete = { mainScope.launch {
                Log.i(TAG, "WebSocket onSetupComplete callback received.")
                isServerReady = true
                updateStatus("Ready to listen")
                updateUI()
            } }
        )
        Log.i(TAG, "New WebSocketClient prepared.")
    }

     private fun handleMasterButton() {
        if (!isNetworkAvailable()) {
            showError("No internet connection.")
            return
        }
        if (!isServerReady && !isSessionActive) {
            Log.d(TAG, "handleMasterButton: No active session, connecting.")
            connect()
            return
        }
        if (!isServerReady) {
            Log.w(TAG, "handleMasterButton: Server not ready, ignoring.")
            return
        }

        isListening = !isListening
        Log.i(TAG, "handleMasterButton: Toggling listening state to: $isListening")
        if (isListening) {
            startAudio()
        } else {
            stopAudio()
        }
        updateUI()
    }

    private fun handleSettingsDisconnectButton() {
        if (isSessionActive) {
            Log.d(TAG, "handleSettingsDisconnectButton: Disconnecting session.")
            teardownSession()
        } else {
            Log.d(TAG, "handleSettingsDisconnectButton: Showing settings dialog.")
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val dialog = SettingsDialog(this, getSharedPreferences("GemWebLivePrefs", MODE_PRIVATE), models)
        dialog.setOnDismissListener {
            Log.d(TAG, "SettingsDialog dismissed.")
            loadPreferences()
            updateDisplayInfo()
            if (isSessionActive) {
                Toast.makeText(this, "Settings saved. Please Disconnect and reconnect to apply.", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun getVadSensitivity(): Int {
        val sensitivity = getSharedPreferences("GemWebLivePrefs", MODE_PRIVATE).getInt("vad_sensitivity_ms", 800)
        Log.d(TAG, "getVadSensitivity: VAD sensitivity is $sensitivity ms.")
        return sensitivity
    }

   private fun connect() {
        if (!isNetworkAvailable()) {
            showError("No internet connection. Please check your network settings.")
            return
        }
        if (isSessionActive) {
            Log.w(TAG, "connect: Already connected or connecting.")
            return
        }
        // Reset reconnect attempts on a new manual connection
        reconnectAttempts = 0
        Log.i(TAG, "connect: Attempting to establish WebSocket connection.")
        updateStatus("Connecting...")
        updateUI()
        webSocketClient?.connect()
    }

       private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    private fun processServerMessage(text: String) {
        Log.v(TAG, "processServerMessage: Received raw message: ${text.take(500)}...")
        try {
            val response = gson.fromJson(text, ServerResponse::class.java)

            // --- Session and Connection Management ---
            response.sessionResumptionUpdate?.let {
                if (it.resumable == true && it.newHandle != null) {
                    sessionHandle = it.newHandle
                    getSharedPreferences("GemWebLivePrefs", MODE_PRIVATE).edit().putString("session_handle", sessionHandle).apply()
                    Log.i(TAG, "Session handle updated and saved.")
                }
            }
            response.goAway?.timeLeft?.let {
                Log.w(TAG, "Received GO_AWAY message. Time left: $it. Will reconnect.")
                showError("Connection closing in $it. Will reconnect.")
            }

            // --- Transcript and Audio Processing ---
            val outputText = response.outputTranscription?.text ?: response.serverContent?.outputTranscription?.text
            if (outputText != null) {
                outputTranscriptBuffer.append(outputText)
            }

            val inputText = response.inputTranscription?.text ?: response.serverContent?.inputTranscription?.text
            if (inputText != null && inputText.isNotBlank()) {
                if (outputTranscriptBuffer.isNotEmpty()) {
                    val fullTranslation = outputTranscriptBuffer.toString().trim()
                    Log.d(TAG, "Displaying full translation: '$fullTranslation'")
                    translationAdapter.addOrUpdateTranslation(fullTranslation, false)
                    outputTranscriptBuffer.clear()
                }
                Log.d(TAG, "Displaying user input: '$inputText'")
                translationAdapter.addOrUpdateTranslation(inputText.trim(), true)
            }
            response.serverContent?.modelTurn?.parts?.forEach { part ->
                part.inlineData?.data?.let {
                    Log.d(TAG, "Playing received audio chunk.")
                    audioPlayer.playAudio(it)
                }
            }

            // --- REMOVED: The turnComplete block is no longer needed for full-duplex ---

        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: $text", e)
        }
    }

private fun startAudio() {
        if (!::audioHandler.isInitialized) {
            Log.d(TAG, "startAudio: Initializing audio components first.")
            initializeComponentsDependentOnAudio()
        }
        Log.i(TAG, "startAudio: Starting audio recording.")
        audioHandler.startRecording()
        updateStatus("Listening...")
        isListening = true
        updateUI()
    }

    private fun stopAudio() {
        if (::audioHandler.isInitialized) {
            Log.i(TAG, "stopAudio: Stopping audio recording.")
            audioHandler.stopRecording()
        }
        // (Rest of the function remains the same)
        isListening = false
        updateUI()
    }

    private fun setProcessingState(processing: Boolean) {
        isProcessing = processing
        binding.processingSpinner.visibility = if (processing) View.VISIBLE else View.GONE
        binding.dualLanguagePanel.alpha = if (processing) 0.5f else 1.0f
    }

    private fun teardownSession(reconnect: Boolean = false) {
        if (!isSessionActive) return
        Log.w(TAG, "teardownSession: Tearing down session. Reconnect: $reconnect")
        isListening = false
        isSessionActive = false
        isServerReady = false
        if (::audioHandler.isInitialized) audioHandler.stopRecording()
        webSocketClient?.disconnect()
        mainScope.launch {
            if (!reconnect) updateStatus("Disconnected")
>>>>>>> f5db74e12ea86a52eb0819dee0753a7bc6c1fd31
            updateUI()
        }
    }

    // --- UI Setup and Updates ---
private fun setupUI() {
        translationAdapter = TranslationAdapter()
        binding.transcriptLog.layoutManager = LinearLayoutManager(this)
        binding.transcriptLog.adapter = translationAdapter

        binding.micBtn.setOnClickListener {
            Log.d(TAG, "Master button clicked.")
            handleMasterButton()
        }

        binding.englishButton.setOnClickListener {
            sourceLanguage = "en-US"
            targetLanguage = "th-TH"
            updateLanguageButtons()
            Toast.makeText(this, "Translating from English to Thai", Toast.LENGTH_SHORT).show()
        }

        binding.thaiButton.setOnClickListener {
            sourceLanguage = "th-TH"
            targetLanguage = "en-US"
            updateLanguageButtons()
            Toast.makeText(this, "Translating from Thai to English", Toast.LENGTH_SHORT).show()
        }

	  updateUI()
        updateLanguageButtons()
        Log.d(TAG, "setupUI: UI components initialized.")
    }



    private fun updateTranscription(language: String, transcription: String) {
        when (language) {
            "Thai" -> binding.thaiTranscriptionTextView.text = transcription
            "English" -> binding.englishTranscriptionTextView.text = transcription
        }
    }


	
	

    private fun updateUI() {
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (isTypingMode) {
            binding.micBtn.visibility = View.GONE
            binding.textInputContainer.visibility = View.VISIBLE
            binding.sendTextBtn.isEnabled = !isProcessing
        } else {
            binding.micBtn.visibility = View.VISIBLE
            binding.textInputContainer.visibility = View.GONE
            binding.micBtn.isEnabled = hasPermission && !isProcessing
            binding.micBtn.text = when {
                isProcessing -> "Processing..."
                isListening -> "Stop"
                else -> "Start Listening"
            }
        }
<<<<<<< HEAD
        binding.settingsBtn.isEnabled = !isListening && !isProcessing
=======
        binding.micBtn.isEnabled = (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        binding.debugConnectBtn.isEnabled = !isSessionActive
        binding.interimDisplay.visibility = if (isListening) View.VISIBLE else View.GONE
        
        if (isListening) {
            if (micPulseAnimator == null) {
                micPulseAnimator = ObjectAnimator.ofFloat(binding.micBtn, "alpha", 1f, 0.5f, 1f).apply {
                    duration = 1500
                    repeatCount = ObjectAnimator.INFINITE
                    start()
                }
            }
        } else {
            micPulseAnimator?.cancel()
            micPulseAnimator = null
            binding.micBtn.alpha = 1f
        }
        
        Log.d(TAG, "updateUI: UI updated with state - isSessionActive=$isSessionActive, isServerReady=$isServerReady, isListening=$isListening")
>>>>>>> f5db74e12ea86a52eb0819dee0753a7bc6c1fd31
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
        binding.configDisplay.text = infoText
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
                stopRecordingAndTranslate()
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
        return if (targetLanguage.language == "th") {
            THAI_SYSTEM_PROMPT
        } else {
            String.format(
                GENERIC_SYSTEM_PROMPT_TEMPLATE,
                sourceLanguage.displayLanguage,
                targetLanguage.displayLanguage
            )
        }
    }

    private fun sendApiRequest(textInput: String?, audioData: ByteArray?) {
        val apiKey = selectedApiKeyInfo?.value
        if (apiKey.isNullOrEmpty()) {
            showError("API Key is not not set. Please configure it in Settings.")
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

    // --- Settings and Language Options --- //val dialog = SettingsDialog(this, prefs, models, getLanguageOptions()) //
    private fun showSettingsDialog() {
        val languages = getLanguageOptions()
        val dialog = SettingsDialog(this, getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE), resources.getStringArray(R.array.models).toList(), languages)
        dialog.setOnDismissListener {
            Log.d(TAG, "SettingsDialog dismissed.")
            loadPreferences()
            updateDisplayInfo()
            updateTtsLanguage()
        }
        dialog.show()
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
