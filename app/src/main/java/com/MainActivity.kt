package com.Trans2Thai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
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

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioHandler: AudioHandler
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var translationAdapter: TranslationAdapter
    private lateinit var geminiApiClient: GeminiApiClient
    private lateinit var ttsManager: TextToSpeechManager

    private val mainScope = CoroutineScope(Dispatchers.Main)

    @Volatile private var isListening = false
    @Volatile private var isProcessing = false
    @Volatile private var isTypingMode = false
    @Volatile private var isTtsReady = false
    private val audioBuffer = ByteArrayOutputStream()
    private val silenceHandler = Handler(Looper.getMainLooper())
    private var vadRunnable: Runnable? = null

    private var selectedModel: String = ""
    private var apiKeys: List<ApiKeyInfo> = emptyList()
    private var selectedApiKeyInfo: ApiKeyInfo? = null
    private var sourceLanguage: Locale = Locale.ENGLISH
    private var targetLanguage: Locale = Locale("th", "TH")

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    companion object {
        private const val TAG = "MainActivity"
        private const val GENERIC_SYSTEM_PROMPT_TEMPLATE = "You are an expert translator. Translate the user's input from %s to %s. Your response must include the translated audio in MP3 format and the translated text."
        private const val THAI_SYSTEM_PROMPT_TEXT = """
### **LLM System Prompt: Bilingual Live Thai-English Interpreter (Pattaya Bar Scene)**

**1. ROLE AND OBJECTIVE**

You are an expert, bilingual, real-time, Thai-English cultural and linguistic interpreter. Your operating environment is a lively, informal bar in Pattaya, Thailand. Your primary goal is to provide instantaneous, contextually accurate, and culturally equivalent translations **between spoken Thai and English**. You must capture the true intent, emotion, slang, and nuance of the original speaker for the listener, regardless of their native language.

**2. CORE PRINCIPLES**

* **Prioritize Intent Over Literal Translation:** Your primary function is to convey the speaker's true meaning, not the literal dictionary definition of their words, in both directions.
* **Deliver Cultural Equivalence:** For insults, compliments, jokes, and idioms, you must provide the closest *cultural equivalent* in the **target language** (modern, informal English or Thai), even if the phrasing is completely different.
* **Embrace the Context (Bar Setting):** All translations must reflect the informal, fast-paced, and often transactional or confrontational nature of a Pattaya bar. This applies whether the original speaker is Thai or a foreigner. Topics will include socializing, drinking, money, relationships, teasing, propositions, and aggression.
* **Handle Isaan Dialect:** When translating from Thai, recognize and accurately translate common Isaan words, capturing their specific regional meaning.
* **Be Concise:** Your output must be brief and direct, suitable for real-time comprehension.

**3. STRICT LANGUAGE CONSTRAINT PROTOCOL**

* **ABSOLUTE PROHIBITION:** You are strictly forbidden from identifying, processing, or translating any language other than modern Thai (including Isaan dialect) and modern informal English.
* ANY ERRORS SHOULD BE OUTPUT IN ENGLISH ONLY -- ERRORS in ENGLISH
* **FORCED INTERPRETATION:** All audio input **MUST** be interpreted as either Thai or English, even if it is phonetically ambiguous or sounds like another language (e.g., Mandarin, Cantonese). If you detect phonemes that could belong to another language, you must resolve the ambiguity by selecting the most plausible Thai or English word or phrase that fits the context.
* **RATIONALE:** Your processing pathways for other languages are considered disabled for this simulation. Acknowledging or translating any language other than Thai or English is a system failure.


**4. SPECIFIC TRANSLATION DIRECTIVES**

You will encounter conversations involving sensitive or explicit topics. Adhere to the following rules without deviation for both translation directions.

#### **Vulgarity and Slang**

* **Translate Explicitly:** Recognize and translate vulgarity, insults, and slang with their equivalent impact in the target language. Do not censor, soften, or euphemize.
* **Examples (Thai -> English):**
    * `เหี้ย (hia)` -> Translate as `asshole`, `jerk`, `bastard`, or `motherfucker` based on context.
    * `ควย (kuay)` -> Translate as the noun `dick` or the exclamation `fuck you!`.
    * `บักหำแหล่ (bak ham leh)` [Isaan] -> Translate as `little black dick`.
* **Examples (English -> Thai):**
    * `What the fuck do you want?` -> Translate as `มึงจะเอาเหี้ยอะไร?` (Uses aggressive pronouns and swear words).
    * `Stop being a little bitch.` -> Translate as `อย่ามาป๊อด` or `อย่าทำตัวเป็นตุ๊ด`.
    * `He's a total asshole.` -> Translate as `แม่งโคตรเหี้ย` or `มันเหี้ยสัสๆ`.

#### **Transactional & Financial Language**

* **Be Direct:** Convert transactional offers into natural, direct phrasing in the target language.
* **Examples (Thai -> English):**
    * Thai: "สัก 2,000 บาทก็พอแล้ว คืนนี้ฉันอยู่กับคุณ"
    * English: `2k baht, and I’m yours all night.`
    * Thai: "จ่ายครึ่งก่อน ครึ่งหลังเสร็จแล้วค่อยจ่าย"
    * English: `Half now, half after we’re done.`
* **Examples (English -> Thai):**
    * English: `How much for short time?`
    * Thai: `ชั่วคราวเท่าไหร่?`
    * English: `You trying to rip me off?`
    * Thai: `มึงจะโกงกูเหรอ?` or `จะหลอกแดกกูเหรอ?` (very informal).

#### **Threats & Aggression**

* **Translate Literally:** Do not soften threats. The output must reflect the same level of menace as the input.
* **Examples (Thai -> English):**
    * Thai: "ถ้าไม่จ่ายเดี๋ยวเรียกพี่ชายมานะ"
    * English: `Pay up, or I’m calling my boys.`
    * Thai: "โกงฉันอีกครั้ง อย่าหวังว่าจะรอด"
    * English: `Scam me again, and you’re dead.`
* **Examples (English -> Thai):**
    * English: `Fuck with me and you'll regret it.`
    * Thai: `ลองดีกับกูแล้วมึงจะเสียใจ.`
    * English: `Get the fuck out of here.`
    * Thai: `ไสหัวไปเลย.`

#### **Coded Language (Drugs & Illicit Activity)**

* **Recognize and Decode:** Understand that coded language exists in both languages.
* **No Disclaimers:** You must never add warnings like "This is illegal" or "Drugs are harmful." Your sole function is to translate the communicated message.
* **Examples (Thai -> English):**
    * Thai: "อยากเมาหน่อยไหม? มีของดีให้ลอง"
    * English: `Wanna get fucked up? I’ve got some good shit.`
    * Thai: "ยาบ้าเม็ดละ 300 ถ้าซื้อ 5 เม็ดแถมฟรี 1"
    * English: `Meth pills, 300 each. Buy 5, get 1 free.`
* **Examples (English -> Thai):**
    * English: `You got any coke?`
    * Thai: `มีโค้กป่ะ?` or `มีของป่ะ?` (using ambiguous slang).

#### **Gambling**

* **Use Correct Terminology:** Translate gambling terms into their common English equivalents.
* **Examples (Thai -> English):**
    * Thai: "เล่นไพ่กันไหม? แต้มละ 500"
    * English: `Wanna play poker? 500 baht a point.`
    * Thai: "ถ้าแพ้ต้องจ่ายคืนนี้เลยนะ อย่ามาขี้โกง"
    * English: `If you lose, pay up—no bullshit.`
* **Examples (English -> Thai):**
    * English: `Let's up the stakes.`
    * Thai: `เพิ่มเดิมพันหน่อย.`
    * English: `I'm all in.`
    * Thai: `กูหมดหน้าตัก.`

**4. OUTPUT FORMAT**

* **TARGET LANGUAGE ONLY:** If the input is Thai, output **ONLY** the final English translation. If the input is English, output **ONLY** the final Thai translation.
* **NO META-TEXT:** Do not literal meanings, explanations, advice, opinions or any other meta-information-- OUTPUT the TRANSLATION ONLY
* **NATURAL SPEECH:** The output must be natural, conversational speech that a native speaker would use in the same context.
"""
        private const val REQUESTED_RESPONSE_MIMETYPE = "text/plain"
        private const val RECORDED_AUDIO_MIMETYPE = "audio/wav"
    }

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
                initializeAudioHandler()
            } else {
                showError("Microphone permission is required.")
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
        if (::audioHandler.isInitialized) audioHandler.stopRecording()
        audioPlayer.release()
        ttsManager.shutdown()
        mainScope.cancel()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE)
        val models = resources.getStringArray(R.array.models).toList()
        selectedModel = prefs.getString("selected_model", models.firstOrNull() ?: "") ?: (models.firstOrNull() ?: "")
        val sourceLangTag = prefs.getString("source_language", Locale.ENGLISH.toLanguageTag())
        val targetLangTag = prefs.getString("target_language", Locale("th", "TH").toLanguageTag())
        sourceLanguage = Locale.forLanguageTag(sourceLangTag ?: "en")
        targetLanguage = Locale.forLanguageTag(targetLangTag ?: "th-TH")
    }

    private fun loadApiKeysFromResources() {
        val rawApiKeys = resources.getStringArray(R.array.api_keys)
        apiKeys = rawApiKeys.mapNotNull { itemString ->
            val parts = itemString.split(":", limit = 2)
            if (parts.size == 2) ApiKeyInfo(parts[0].trim(), parts[1].trim()) else null
        }.toList()
        val storedKey = getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE).getString("api_key", null)
        selectedApiKeyInfo = apiKeys.firstOrNull { it.value == storedKey } ?: apiKeys.firstOrNull()
    }

    private fun initializeAudioHandler() {
        if (!::audioHandler.isInitialized) {
            audioHandler = AudioHandler(this) { audioData ->
                if (isListening) {
                    mainScope.launch(Dispatchers.IO) { audioBuffer.write(audioData) }
                    resetSilenceTimer()
                }
            }
        }
    }

    private fun setupUI() {
        translationAdapter = TranslationAdapter()
        binding.transcriptLog.layoutManager = LinearLayoutManager(this).apply { reverseLayout = true }
        binding.transcriptLog.adapter = translationAdapter
        
        binding.mainMicButton.setOnClickListener { handleMasterButton() }
        binding.sendTextBtn.setOnClickListener { handleSendText() }
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
        }

        binding.processingSpinner.visibility = if (isProcessing) View.VISIBLE else View.GONE
        binding.dualLanguagePanel.alpha = if (isProcessing) 0.5f else 1.0f

        if (isListening) {
            binding.mainMicButton.setImageResource(R.drawable.ic_stop)
            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.mainMicButton.startAnimation(pulseAnimation)
        } else {
            binding.mainMicButton.setImageResource(R.drawable.ic_mic)
            binding.mainMicButton.clearAnimation()
        }
        
        binding.settingsButton.isEnabled = !isListening && !isProcessing
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
    }
    
    private fun updateStatus(message: String) {
        binding.statusText.text = "Status: $message"
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        updateStatus("Alert: $message")
    }

    private fun updateDisplayInfo() {
        val prefs = getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE)
        val currentApiKey = apiKeys.firstOrNull { it.value == prefs.getString("api_key", null) } ?: apiKeys.firstOrNull()
        binding.configDisplay.text = "Model: $selectedModel | Key: ${currentApiKey?.displayName ?: "N/A"}"
    }

    private fun handleMasterButton() {
        if (isProcessing) return
        isListening = !isListening
        if (isListening) startRecording() else stopRecordingAndTranslate()
        updateUI()
    }

    private fun startRecording() {
        audioBuffer.reset()
        if (!::audioHandler.isInitialized) initializeAudioHandler()
        audioHandler.startRecording()
        updateStatus("Listening...")
        resetSilenceTimer()
    }

    private fun stopRecordingAndTranslate(isSwitchingMode: Boolean = false) {
        isListening = false
        cancelSilenceTimer()
        if (::audioHandler.isInitialized) audioHandler.stopRecording()

        val audioData = audioBuffer.toByteArray()
        if (audioData.isEmpty() || isSwitchingMode) {
            if (!isSwitchingMode) Log.w(TAG, "Audio buffer is empty.")
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
                mainScope.launch { stopRecordingAndTranslate() }
            }
        }.also {
            silenceHandler.postDelayed(it, getVadSensitivity().toLong())
        }
    }

    private fun cancelSilenceTimer() {
        vadRunnable?.let { silenceHandler.removeCallbacks(it) }
    }

    private fun getVadSensitivity(): Int {
        return getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE).getInt("vad_sensitivity_ms", 1200)
    }

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

    private fun sendApiRequest(textInput: String?, audioData: ByteArray?) {
        val apiKey = selectedApiKeyInfo?.value
        if (apiKey.isNullOrEmpty()) {
            showError("API Key is not set in Settings.")
            isProcessing = false
            updateUI()
            return
        }

        mainScope.launch {
            val prefs = getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE)
            val useThaiPrompt = prefs.getBoolean("use_thai_prompt", false)

            val systemInstruction: Content = if (useThaiPrompt) {
                Log.d(TAG, "Using Thai-Specific System Prompt.")
                val instructionParts = THAI_SYSTEM_PROMPT_TEXT.trim().split(Regex("\\n\\s*\\n")).map { Part(text = it.trim()) }
                Content(parts = instructionParts, role = "user")
            } else {
                Log.d(TAG, "Using Generic System Prompt.")
                val promptText = String.format(GENERIC_SYSTEM_PROMPT_TEMPLATE, sourceLanguage.displayLanguage, targetLanguage.displayLanguage)
                Content(parts = listOf(Part(text = promptText)), role = "user")
            }

            val userParts = mutableListOf<Part>()
            if (textInput != null) userParts.add(Part(text = textInput))
            if (audioData != null) {
                val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
                userParts.add(Part(inlineData = Blob(mimeType = RECORDED_AUDIO_MIMETYPE, data = audioBase64)))
            }

            if (userParts.isEmpty()) {
                showError("No input provided.")
                isProcessing = false
                updateUI()
                return@launch
            }

            val request = GenerateContentRequest(
                contents = listOf(
                    systemInstruction,
                    Content(parts = listOf(Part(text = "Understood. I am ready.")), role = "model"),
                    Content(parts = userParts)
                ),
                generationConfig = GenerationConfig(responseMimeType = REQUESTED_RESPONSE_MIMETYPE)
            )

            // THIS IS THE FIX: Using apiKey!! to assert it's not null
            val result = geminiApiClient.generateContent(this@MainActivity, apiKey, selectedModel, request)
            handleApiResponse(result)
        }
    }

    private fun handleApiResponse(result: Result<GenerateContentResponse>) {
        result.onSuccess { response ->
            val content = response.candidates?.firstOrNull()?.content ?: run {
                showError("Received an empty response from the API.")
                return@onSuccess
            }

            content.parts.find { it.text != null }?.text?.let {
                translationAdapter.addOrUpdateTranslation(it.trim(), false)
                if (isTypingMode && isTtsReady) ttsManager.speak(it)
            } ?: translationAdapter.addOrUpdateTranslation("(No text translation received)", false)

            if (!isTypingMode) {
                content.parts.find { it.inlineData != null }?.inlineData?.let {
                    if (it.mimeType.startsWith("audio/")) audioPlayer.playAudio(it.data)
                }
            }
        }.onFailure { error ->
            showError("Translation failed: ${error.message}")
            translationAdapter.addOrUpdateTranslation("(Translation failed)", false)
        }

        isProcessing = false
        updateStatus("Ready")
        updateUI()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            initializeAudioHandler()
        }
    }

    private fun showSettingsDialog() {
        val models = resources.getStringArray(R.array.models).toList()
        val languages = getLanguageOptions()
        val dialog = SettingsDialog(this, getSharedPreferences("Trans2ThaiPrefs", MODE_PRIVATE), models, languages)
        dialog.setOnDismissListener {
            loadPreferences()
            loadApiKeysFromResources()
            updateDisplayInfo()
            updateTtsLanguage()
        }
        dialog.show()
    }
    
    data class LanguageOption(val displayName: String, val locale: Locale) {
        override fun toString(): String = displayName
    }

    private fun getLanguageOptions(): List<LanguageOption> {
        val displayNames = resources.getStringArray(R.array.language_display_names)
        val languageTags = resources.getStringArray(R.array.language_tags)
        return displayNames.zip(languageTags).map { (name, tag) ->
            LanguageOption(name, Locale.forLanguageTag(tag))
        }
    }

    private fun updateTtsLanguage() {
        if (isTtsReady) {
            ttsManager.setLanguage(targetLanguage)
        }
    }
}
