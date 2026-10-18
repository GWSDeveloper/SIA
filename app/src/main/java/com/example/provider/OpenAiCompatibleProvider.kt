package com.example.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val modelName: String,
    private val providerName: String
) : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generateResponse(context: PromptContext): Result<LlmCallResult> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("$providerName API key is missing. Please enter it in Settings.")
                    )
                }

                val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

                val toolsInstruction = buildString {
                    append("\nYou have access to the following Android tools:\n")
                    context.availableTools.forEach { tool ->
                        val paramList = tool.parameters.joinToString(", ") { "${it.name} (${it.type}): ${it.description}" }
                        append("- ${tool.name}($paramList): ${tool.description}\n")
                    }
                    append("\n```tool_calls\n[{\"tool\": \"tool_name\", \"params\": {\"param_name\": \"value\"}}]\n```")
                    append("\nWhen no tool is required, answer directly and naturally in the user’s language. Never claim an action succeeded before its execution result confirms it.")
                }

                val fullSystemInstruction = buildString {
                    append(context.systemPrompt)
                    if (context.permanentMemory.isNotBlank()) {
                        append("\n\n[USER PERMANENT MEMORY & PROFILE]:\n")
                        append(context.permanentMemory)
                    }
                    if (context.rollingSummary.isNotBlank()) {
                        append("\n\n[CONVERSATION ROLLING SUMMARY]:\n")
                        append(context.rollingSummary)
                    }
                    append(toolsInstruction)
                }

                val messagesArray = JSONArray()
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", fullSystemInstruction)
                })

                context.recentMessages.takeLast(10).forEach { msg ->
                    messagesArray.put(JSONObject().apply {
                        put("role", if (msg.role == "user") "user" else "assistant")
                        put("content", msg.content)
                    })
                }

                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", context.currentUserMessage)
                })

                val rootJson = JSONObject().apply {
                    put("model", modelName)
                    put("messages", messagesArray)
                    put("temperature", 0.7)
                }

                val body = rootJson.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .addHeader("Authorization", "Bearer $apiKey")

                if (providerName.contains("OpenRouter", ignoreCase = true)) {
                    requestBuilder.addHeader("HTTP-Referer", "https://github.com/fast/sia")
                    requestBuilder.addHeader("X-Title", "SIA Assistant")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errMsg = try {
                        val errObj = JSONObject(responseBody).optJSONObject("error")
                        errObj?.optString("message") ?: "HTTP ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $responseBody"
                    }
                    return@withContext Result.failure(Exception(errMsg))
                }

                val rootObj = JSONObject(responseBody)
                val choices = rootObj.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext Result.success(LlmCallResult("No response received from $providerName."))
                }
                val firstChoice = choices.getJSONObject(0)
                val msg = firstChoice.optJSONObject("message")
                val text = msg?.optString("content") ?: ""

                Result.success(ToolCallParser.parse(text))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
