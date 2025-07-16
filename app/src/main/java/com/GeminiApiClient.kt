package com.Trans2Thai

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import android.content.Context
import android.net.ConnectivityManager

class GeminiApiClient {
    

    private val client = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
    }

    /**
     * Sends a request to the Gemini API's generateContent endpoint.
     *
     * @param apiKey Your Google AI API key.
     * @param modelName The name of the model to use (e.g., "gemini-2.5-flash-preview-native-audio-dialog").
     * @param requestBody The structured request object.
     * @return A Result wrapper containing the parsed GenerateContentResponse on success or an Exception on failure.
     */
    suspend fun generateContent(
        context: Context,
        apiKey: String,
        modelName: String,
        requestBody: GenerateContentRequest
    ): Result<GenerateContentResponse> = withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        if (activeNetwork?.isConnectedOrConnecting != true) {
            Log.e(TAG, "Network check failed: No internet connection.")
            return@withContext Result.failure(IOException("No internet connection. Please check your network settings."))
        }
        val url = String.format(API_URL_TEMPLATE, modelName)
        val jsonBody = gson.toJson(requestBody)
        val request = Request.Builder()
            .url(url)
            .header("X-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "Request URL: $url")
        Log.d(TAG, "Request Body: $jsonBody")

        try {
            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string()

            if (response.isSuccessful && responseBodyString != null) {
                Log.d(TAG, "Response Success: ${response.code}")
                Log.d(TAG, "Response Body: $responseBodyString")
                val parsedResponse = gson.fromJson(responseBodyString, GenerateContentResponse::class.java)
                Result.success(parsedResponse)
            } else {
                val errorMsg = "API call failed with code ${response.code}: $responseBodyString"
                Log.e(TAG, errorMsg)
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network request failed", e)
            Result.failure(e)
        }
    }
}
