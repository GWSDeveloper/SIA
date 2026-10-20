package com.example.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** Captures one utterance as 16 kHz mono PCM and transcribes it through Gnani Vachana. */
internal class GnaniSttClient(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    suspend fun listenAndTranscribe(apiKey: String, onLevel: (Float) -> Unit): String {
        require(apiKey.isNotBlank()) { "Gnani API key is not configured." }
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IOException("This device cannot initialize 16 kHz microphone capture.")
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer * 2, 4096))
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IOException("Microphone recorder could not be initialized.")
        }

        val audio = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        var heardSpeech = false
        var speechStartAt = 0L
        var lastLoudAt = 0L
        val startedAt = SystemClock.elapsedRealtime()
        try {
            recorder.startRecording()
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(buffer, 0, buffer.size)
                if (read < 0) throw IOException("Microphone capture failed ($read).")
                if (read == 0) continue
                audio.write(buffer, 0, read)
                var sum = 0.0
                var samples = 0
                var i = 0
                while (i + 1 < read) {
                    val value = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)).toShort().toInt()
                    sum += value.toDouble() * value.toDouble()
                    samples++
                    i += 2
                }
                val rms = if (samples > 0) sqrt(sum / samples).toFloat() else 0f
                onLevel((rms / 6000f).coerceIn(0f, 1f))
                val now = SystemClock.elapsedRealtime()
                if (rms >= 550f) {
                    if (!heardSpeech) { heardSpeech = true; speechStartAt = now }
                    lastLoudAt = now
                }
                if (!heardSpeech && now - startedAt >= 8_000L) throw IOException("No speech detected. Try again closer to the microphone.")
                if (heardSpeech && now - lastLoudAt >= 1_050L && now - speechStartAt >= 450L) break
                if (now - startedAt >= 15_000L) break
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
        }

        val pcm = audio.toByteArray()
        if (!heardSpeech || pcm.size < sampleRate / 4) throw IOException("No usable speech captured.")
        val wavFile = File.createTempFile("gnani_stt_", ".wav", context.cacheDir)
        try {
            FileOutputStream(wavFile).use { it.write(toWav(pcm, sampleRate)) }
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("audio_file", wavFile.name, wavFile.asRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("language_code", "hi-IN")
                .addFormDataPart("preferred_language", "hi-IN")
                .addFormDataPart("format", "transcribe")
                .addFormDataPart("itn_native_numerals", "true")
                .build()
            val request = Request.Builder().url("https://api.vachana.ai/stt/v3")
                .header("X-API-Key-ID", apiKey).post(body).build()
            http.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = try {
                        val json = JSONObject(responseText)
                        json.optString("message").ifBlank { json.optString("detail") }
                    } catch (_: Exception) { "" }
                    throw IOException("Gnani STT request failed (HTTP ${response.code})${if (detail.isNotBlank()) ": $detail" else ""}.")
                }
                val json = JSONObject(responseText)
                val transcript = sequenceOf("transcript", "text", "result")
                    .map { json.optString(it).trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
                if (transcript.isBlank()) throw IOException("Gnani returned an empty transcript.")
                return transcript
            }
        } finally { wavFile.delete() }
    }

    private fun toWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun int(value: Int) { out.write(value and 0xff); out.write((value shr 8) and 0xff); out.write((value shr 16) and 0xff); out.write((value shr 24) and 0xff) }
        fun short(value: Int) { out.write(value and 0xff); out.write((value shr 8) and 0xff) }
        ascii("RIFF"); int(36 + pcm.size); ascii("WAVE"); ascii("fmt "); int(16); short(1); short(1)
        int(sampleRate); int(sampleRate * 2); short(2); short(16); ascii("data"); int(pcm.size); out.write(pcm)
        return out.toByteArray()
    }
}
