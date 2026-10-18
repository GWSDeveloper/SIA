package com.example.tools

import android.content.Context
import com.example.preferences.SiaPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Retrieves real web results for the LLM to summarize; it never opens a browser. */
class WebSearchAgent(context: Context) {
    private val preferences = SiaPreferences.getInstance(context.applicationContext)
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    fun search(query: String): ToolExecutionResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "A search query is required.")
        val apiKey = preferences.getTavilyApiKey()
        if (apiKey.isBlank()) return ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "TAVILY_KEY_MISSING: Add a Tavily API key in SIA Settings to enable web search.")
        return try {
            val payload = JSONObject().put("api_key", apiKey).put("query", cleanQuery).put("search_depth", "advanced").put("max_results", 5).put("include_answer", true).put("include_raw_content", false)
            val request = Request.Builder().url("https://api.tavily.com/search").post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val status = response.code
                    // Safe diagnostic from Tavily's response; never include the API key or request body.
                    val providerDetail = bodyText.replace(Regex("[\\r\\n\\t]+"), " ").take(240)
                    val message = when (status) {
                        400 -> "Tavily rejected the search request (HTTP 400): $providerDetail"
                        401, 403 -> "Tavily rejected the API key. Check it in SIA Settings."
                        429 -> "Tavily request limit reached. Try again later."
                        in 500..599 -> "Tavily server error (HTTP $status): $providerDetail"
                        else -> "Web search failed (HTTP $status): $providerDetail"
                    }
                    return ToolExecutionResult(if (status == 429 || status >= 500) ToolResultStatus.RETRYABLE_ERROR else ToolResultStatus.PERMANENT_ERROR, message)
                }
                val json = JSONObject(bodyText)
                val answer = json.optString("answer").trim()
                val results = json.optJSONArray("results")
                val excerpts = mutableListOf<String>()
                if (answer.isNotBlank()) excerpts.add("Search summary: $answer")
                if (results != null) for (i in 0 until minOf(results.length(), 5)) {
                    val item = results.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    val url = item.optString("url").trim()
                    val body = item.optString("content").trim()
                    if (title.isNotBlank() || body.isNotBlank()) excerpts.add("Source: $title\nURL: $url\nExcerpt: $body")
                }
                if (excerpts.isEmpty()) ToolExecutionResult(ToolResultStatus.RETRYABLE_ERROR, "No useful web results were returned for '$cleanQuery'. The search did not verify an answer; retry with a more specific, date-anchored query.")
                else ToolExecutionResult(ToolResultStatus.SUCCESS, "Web search results for: $cleanQuery\n\n" + excerpts.joinToString("\n\n"), data = mapOf("query" to cleanQuery, "result_count" to (results?.length() ?: 0).toString()))
            }
        } catch (_: IOException) {
            ToolExecutionResult(ToolResultStatus.RETRYABLE_ERROR, "I could not reach the web search service. Check your internet and try again.")
        } catch (_: Exception) {
            ToolExecutionResult(ToolResultStatus.RETRYABLE_ERROR, "The web search response could not be processed.")
        }
    }
}
