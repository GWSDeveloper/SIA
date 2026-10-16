package com.example.provider

import org.json.JSONArray
import org.json.JSONObject

object ToolCallParser {

    /**
     * Parses raw model text into clean user-facing text and a list of structured tool calls.
     * Guaranteed never to leak raw ```tool_call blocks or tool JSON into textReply.
     */
    fun parse(rawText: String): LlmCallResult {
        val toolCalls = mutableListOf<SingleToolCall>()
        var cleanedText = rawText

        // 1. Look for ```tool_calls [...] ``` blocks
        val arrayRegex = Regex("""```(?:tool_calls|tools|json)?\s*(\[\s*\{.*?\}\s*\])\s*```""", RegexOption.DOT_MATCHES_ALL)
        arrayRegex.findAll(rawText).forEach { match ->
            val jsonArrayStr = match.groupValues[1]
            try {
                val array = JSONArray(jsonArrayStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tool = parseSingleToolObject(obj)
                    if (tool != null) {
                        toolCalls.add(tool)
                    }
                }
            } catch (_: Exception) {}
            cleanedText = cleanedText.replace(match.value, "")
        }

        // 2. Look for ```tool_call {...} ``` individual blocks
        val singleRegex = Regex("""```(?:tool_call|tool|json)?\s*(\{\s*"tool".*?\})\s*```""", RegexOption.DOT_MATCHES_ALL)
        singleRegex.findAll(cleanedText).forEach { match ->
            val jsonStr = match.groupValues[1]
            try {
                val obj = JSONObject(jsonStr)
                val tool = parseSingleToolObject(obj)
                if (tool != null) {
                    toolCalls.add(tool)
                }
            } catch (_: Exception) {}
            cleanedText = cleanedText.replace(match.value, "")
        }

        // 3. Fallback: look for inline JSON object {"tool": "...", "params": {...}} without markdown code fence
        val inlineRegex = Regex("""(\{\s*"tool"\s*:\s*"[^"]+"\s*,\s*"params"\s*:\s*\{.*?\}\s*\})""", RegexOption.DOT_MATCHES_ALL)
        inlineRegex.findAll(cleanedText).forEach { match ->
            val jsonStr = match.groupValues[1]
            try {
                val obj = JSONObject(jsonStr)
                val tool = parseSingleToolObject(obj)
                if (tool != null) {
                    toolCalls.add(tool)
                }
                cleanedText = cleanedText.replace(match.value, "")
            } catch (_: Exception) {}
        }

        // Remove any leftover empty markdown blocks
        cleanedText = cleanedText
            .replace(Regex("""```(?:tool_call|tool_calls|json)?\s*```"""), "")
            .trim()

        return LlmCallResult(
            textReply = cleanedText,
            toolCalls = toolCalls
        )
    }

    private fun parseSingleToolObject(obj: JSONObject): SingleToolCall? {
        val toolName = obj.optString("tool").ifEmpty { obj.optString("name") }
        if (toolName.isBlank()) return null

        val paramsJson = obj.optJSONObject("params") ?: obj.optJSONObject("arguments") ?: obj.optJSONObject("input")
        val paramsMap = mutableMapOf<String, String>()

        if (paramsJson != null) {
            val keys = paramsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                paramsMap[key] = paramsJson.optString(key)
            }
        }

        return SingleToolCall(toolName = toolName, toolParams = paramsMap)
    }
}
