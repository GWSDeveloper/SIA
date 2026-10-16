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

class GeminiProvider(
    private val apiKey: String,
    private val modelName: String = "gemini-2.5-flash"
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
                        IllegalStateException("Gemini API key is not configured. Please add it in Settings.")
                    )
                }

                val effectiveModel = if (modelName.isBlank()) "gemini-2.5-flash" else modelName
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"

                // Construct full system prompt including permanent memory & tool instructions
                val toolsInstruction = buildString {
                    append("\nYou have access to the following Android tools:\n")
                    context.availableTools.forEach { tool ->
                        val paramList = tool.parameters.joinToString(", ") { "${it.name} (${it.type}): ${it.description}" }
                        append("- ${tool.name}($paramList): ${tool.description}\n")
                    }
                    append("\nIf the user's intent requires actions, you can invoke one or multiple tools using this JSON format at the start of your response:")
                    append("\n```tool_calls\n[{\"tool\": \"tool_name\", \"params\": {\"param_name\": \"value\"}}]\n```")
                    append("\nFollowed by a concise, friendly spoken response in Hindi/Hinglish/English depending on user context. Do not include raw json in your spoken response.")
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

                val rootJson = JSONObject()

                // System instruction
                val sysPart = JSONObject().put("text", fullSystemInstruction)
                val sysContent = JSONObject().put("parts", JSONArray().put(sysPart))
                rootJson.put("system_instruction", sysContent)

                // Contents
                val contentsArray = JSONArray()

                // Recent messages
                context.recentMessages.takeLast(10).forEach { msg ->
                    val role = if (msg.role == "user") "user" else "model"
                    val contentObj = JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                    }
                    contentsArray.put(contentObj)
                }

                // Current message
                val curContent = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", context.currentUserMessage)))
                }
                contentsArray.put(curContent)

                rootJson.put("contents", contentsArray)

                val body = rootJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errMsg = try {
                        val errJson = JSONObject(responseBody).optJSONObject("error")
                        errJson?.optString("message") ?: "Error ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $responseBody"
                    }
                    return@withContext Result.failure(Exception(errMsg))
                }

                val parsed = parseModelOutput(responseBody)
                Result.success(parsed)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseModelOutput(responseJsonStr: String): LlmCallResult {
        val root = JSONObject(responseJsonStr)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return LlmCallResult(textReply = "I am here, but received an empty response.")
        }
        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text") ?: ""

        return ToolCallParser.parse(text)
    }
}
