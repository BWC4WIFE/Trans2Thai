package com.Trans2Thai

import com.google.gson.annotations.SerializedName
import java.util.Locale

// --- Request Data Classes ---

data class GenerateContentRequest(
    val contents: List<Content>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig
)

data class Content(
    val parts: List<Part>,
    // Role can be "user" for your prompts or "model" for previous turns in a conversation
    val role: String = "user"
)

data class Part(
    // You can send either text or inlineData (or both) in a single Part.
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: Blob? = null
)

data class Blob(
    @SerializedName("mime_type") val mimeType: String,
    // The data should be a Base64 encoded string
    @SerializedName("data") val data: String
)

data class GenerationConfig(
    // Requesting a specific MIME type for the response, e.g., "audio/mp3"
    @SerializedName("response_mime_type") val responseMimeType: String
)


// --- Response Data Classes ---

data class GenerateContentResponse(
    val candidates: List<Candidate>?,
    val promptFeedback: PromptFeedback?
)

data class Candidate(
    val content: Content,
    val finishReason: String?,
    val index: Int,
    val safetyRatings: List<SafetyRating>?
)

data class PromptFeedback(
    val safetyRatings: List<SafetyRating>?
)

data class SafetyRating(
    val category: String,
    val probability: String
)

// --- Data classes for local use in Settings ---
// These were moved here from MainActivity for better organization.
data class ApiVersion(val displayName: String, val value: String) {
    override fun toString(): String = displayName
}

data class ApiKeyInfo(val displayName: String, val value: String) {
    override fun toString(): String = displayName
}

// --- NEW: Data class for language selection spinners ---
data class LanguageOption(val displayName: String, val locale: Locale) {
    // This override is crucial for the ArrayAdapter to display the name correctly in the spinner.
    override fun toString(): String = displayName
}