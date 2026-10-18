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
                    append("\\nTool protocol: when actions are needed, emit exactly one tool_calls fenced block containing a JSON array of every required registered tool call. Each item has a tool name and params object. Do not include success claims or user-facing messages in the same response as tool calls. The app executes calls and returns actual results; only then give the final answer. For ordinary conversation without tools, reply normally.")
                    append("\nMusic: route music intent through media_agent and derive query/action from the actual request and conversation context. No hardcoded track names or phrase matching. Only report playback success when execution results confirm it.")
                    append("\nCurrent/external facts: use web_agent(action=search, query=the actual question) for current prices, exchange rates, news, and other facts requiring retrieval. Do not open Google or a browser instead. Do not fabricate results or answer a tool-required request with a generic acknowledgement.")
                    append("\nCompound requests: include all required independent tool calls in the same array. The number of calls depends on intent, not a fixed count. After results return, choose any further necessary tools or provide the final answer. Never repeat completed calls or claim success before actual results confirm it.")
                    append("\nReply in Roman-script Hinglish by default when the user speaks Hinglish; reply in English when the user speaks English. Use Devanagari Hindi only when the user clearly speaks/writes in Hindi or requests it. Keep spoken replies concise and natural, and never include raw JSON in the spoken response.")
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
        if (parts == null || parts.length() == 0) {
            return LlmCallResult(textReply = "I could not get a usable response from the AI provider. Please try again.")
        }

        // Read every text part; a tool call or final answer may not be in parts[0].
        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val partText = part.optString("text")
                if (partText.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(partText)
                }
            }
        }.trim()

        if (text.isBlank()) {
            return LlmCallResult(textReply = "I could not get a usable text response from the AI provider. Please try again.")
        }
        return ToolCallParser.parse(text)
    }
}
