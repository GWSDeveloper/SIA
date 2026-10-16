package com.example.speech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed class SarvamTtsResult {
    data class Success(val audioFile: File) : SarvamTtsResult()
    data class Error(
        val statusCode: Int,
        val message: String,
        val isAuthError: Boolean = false
    ) : SarvamTtsResult()
}

/**
 * Isolated client for Sarvam AI Text-to-Speech Streaming API.
 *
 * Endpoint: POST https://api.sarvam.ai/text-to-speech/stream
 *
 * Returns raw binary audio bytes which are saved to temporary cache storage for playback.
 */
object SarvamTtsClient {

    private const val TAG = "SarvamTtsClient"
    private const val STREAM_ENDPOINT = "https://api.sarvam.ai/text-to-speech/stream"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Synthesizes text using Sarvam AI streaming TTS and writes raw audio bytes to a temporary file.
     */
    suspend fun synthesizeSpeechToFile(
        context: Context,
        apiKey: String,
        text: String,
        speaker: String = "suman_hi_companion",
        model: String = "bulbul:v4-flash",
        targetLanguageCode: String = "hi-IN",
        pace: Float = 1.0f
    ): SarvamTtsResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext SarvamTtsResult.Error(
                statusCode = 401,
                message = "Sarvam API subscription key is not configured.",
                isAuthError = true
            )
        }

        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext SarvamTtsResult.Error(
                statusCode = 400,
                message = "Cannot synthesize empty text."
            )
        }

        // Build request payload according to Sarvam AI streaming specification
        val payload = JSONObject().apply {
            put("text", cleanText)
            put("target_language_code", targetLanguageCode)
            put("speaker", speaker)
            put("model", model)
            put("pace", pace.toDouble())
            put("speech_sample_rate", 24000)
            put("audio_format", "mp3")
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(STREAM_ENDPOINT)
            .addHeader("api-subscription-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val code = response.code

            if (!response.isSuccessful) {
                val errorBody = try {
                    response.body?.string()?.take(200) ?: "No error body"
                } catch (_: Exception) {
                    "Unable to read error body"
                }
                Log.w(TAG, "Sarvam TTS streaming error HTTP $code: $errorBody")

                return@withContext SarvamTtsResult.Error(
                    statusCode = code,
                    message = when (code) {
                        400 -> "Bad request: invalid voice or format ($errorBody)"
                        401, 403 -> "Authentication failed: invalid or unauthorized subscription key."
                        429 -> "Rate limit exceeded. Please wait a moment."
                        in 500..599 -> "Sarvam AI server error ($code)."
                        else -> "HTTP $code error: $errorBody"
                    },
                    isAuthError = (code == 401 || code == 403)
                )
            }

            val responseBody = response.body
            if (responseBody == null) {
                return@withContext SarvamTtsResult.Error(
                    statusCode = 204,
                    message = "Received empty response from Sarvam TTS stream."
                )
            }

            // Write the raw streaming bytes directly to a temporary file
            val tempAudioFile = File.createTempFile("sarvam_speech_", ".mp3", context.cacheDir)
            responseBody.byteStream().use { input ->
                tempAudioFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempAudioFile.length() <= 0L) {
                tempAudioFile.delete()
                return@withContext SarvamTtsResult.Error(
                    statusCode = 204,
                    message = "Synthesized audio file was empty."
                )
            }

            // Prune old audio temp files in background to prevent storage leaks
            cleanOldAudioCache(context.cacheDir)

            return@withContext SarvamTtsResult.Success(tempAudioFile)

        } catch (e: Exception) {
            Log.e(TAG, "Network failure calling Sarvam TTS stream: ${e.message}", e)
            return@withContext SarvamTtsResult.Error(
                statusCode = -1,
                message = "Network error connecting to Sarvam AI: ${e.message}"
            )
        }
    }

    /**
     * Cleans up orphaned temporary audio files in app cache.
     */
    fun cleanOldAudioCache(cacheDir: File) {
        try {
            val files = cacheDir.listFiles { _, name -> name.startsWith("sarvam_speech_") }
            val now = System.currentTimeMillis()
            files?.forEach { file ->
                // Clean up files older than 3 minutes
                if (now - file.lastModified() > 3 * 60 * 1000) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }
}
