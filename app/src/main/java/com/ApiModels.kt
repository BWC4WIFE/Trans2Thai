// app/src/main/java/com/Trans2Thai/ApiModels.kt
package com.Trans2Thai

import java.util.Locale 
import com.google.gson.annotations.SerializedName

// --- Request Data Classes ---

data class GenerateContentRequest(
    val contents: List<Content>,
    // Make generationConfig nullable so it can be omitted
    @SerializedName("generationConfig") val generationConfig: GenerationConfig?
)

data class Content(
    val parts: List<Part>,
    val role: String = "user"
)

data class Part(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: Blob? = null
)

data class Blob(
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("data") val data: String
)

data class GenerationConfig(
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

data class ApiVersion(val displayName: String, val value: String) {
    override fun toString(): String = displayName
}

data class ApiKeyInfo(val displayName: String, val value: String) {
    override fun toString(): String = displayName
}

data class Language(val displayName: String, val locale: Locale) {
    override fun toString(): String = displayName
}
