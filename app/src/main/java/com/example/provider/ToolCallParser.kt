package com.example.provider

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the text-based tool protocol used by supported LLM providers.
 *
 * Uses balanced JSON scanning rather than non-greedy brace regexes, which can truncate
 * nested params and silently drop later calls in a compound request.
 */
object ToolCallParser {
    fun parse(rawText: String): LlmCallResult {
        val calls = mutableListOf<SingleToolCall>()
        val removedRanges = mutableListOf<IntRange>()

        val fenced = Regex("(?s)```(?:tool_calls|tools|tool_call|tool|json)?\\s*(.*?)\\s*```")
        for (match in fenced.findAll(rawText)) {
            val body = match.groupValues[1].trim()
            val parsed = parseJsonPayload(body)
            val isToolProtocolBlock = match.value.startsWith("```tool_calls") ||
                match.value.startsWith("```tool_call") ||
                match.value.startsWith("```tools") ||
                match.value.startsWith("```tool")

            if (parsed.isNotEmpty()) calls.addAll(parsed)

            // Never expose malformed or empty tool protocol to the chat UI.
            // A model can emit an incomplete block such as ```tool_calls [ ```.
            if (parsed.isNotEmpty() || isToolProtocolBlock) {
                removedRanges.add(match.range)
            }
        }

        // Some providers omit fences; scan balanced JSON objects/arrays and only accept
        // objects matching our explicit {tool/name, params/arguments/input} protocol.
        var i = 0
        while (i < rawText.length) {
            val c = rawText[i]
            if (c != '{' && c != '[') {
                i++
                continue
            }
            val end = findJsonEnd(rawText, i)
            if (end < 0) {
                i++
                continue
            }
            val candidate = rawText.substring(i, end + 1)
            val parsed = parseJsonPayload(candidate)
            if (parsed.isNotEmpty()) {
                calls.addAll(parsed)
                removedRanges.add(i..end)
                i = end + 1
            } else {
                i++
            }
        }

        val deduped = calls.distinctBy { it.toolName + "|" + it.toolParams.toSortedMap() }
        val cleaned = buildString {
            var cursor = 0
            for (range in removedRanges.sortedBy { it.first }) {
                if (range.first < cursor) continue
                append(rawText.substring(cursor, range.first))
                cursor = range.last + 1
            }
            if (cursor < rawText.length) append(rawText.substring(cursor))
        }.trim()

        val cleanReply = cleaned.replace(Regex("\\n{3,}"), "\n\n").trim()
        val containedToolProtocol = rawText.contains("```tool_calls") ||
            rawText.contains("```tool_call") ||
            rawText.contains("```tools") ||
            rawText.contains("```tool")

        return LlmCallResult(
            textReply = if (deduped.isEmpty() && cleanReply.isBlank() && containedToolProtocol) {
                "I couldn't form a valid action request. Please try again."
            } else {
                cleanReply
            },
            toolCalls = deduped
        )
    }

    private fun parseJsonPayload(payload: String): List<SingleToolCall> {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val array = JSONArray(trimmed)
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let(::parseSingleToolObject)
                    }
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val nestedCalls = obj.optJSONArray("tool_calls")
                    if (nestedCalls != null) {
                        (0 until nestedCalls.length()).mapNotNull { index ->
                            nestedCalls.optJSONObject(index)?.let(::parseSingleToolObject)
                        }
                    } else {
                        listOfNotNull(parseSingleToolObject(obj))
                    }
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findJsonEnd(text: String, start: Int): Int {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> stack.addLast('}')
                '[' -> stack.addLast(']')
                '}', ']' -> {
                    if (stack.isEmpty() || stack.removeLast() != c) return -1
                    if (stack.isEmpty()) return i
                }
            }
        }
        return -1
    }

    private fun parseSingleToolObject(obj: JSONObject): SingleToolCall? {
        val suppliedParams = obj.optJSONObject("params")
            ?: obj.optJSONObject("arguments")
            ?: obj.optJSONObject("input")
        // A generic JSON object with a "name" field is not automatically a tool call.
        // Accept the "name" alias only when an explicit argument object is present.
        val toolName = obj.optString("tool").ifBlank {
            if (suppliedParams != null) obj.optString("name") else ""
        }
        if (toolName.isBlank()) return null
        val params = suppliedParams ?: JSONObject()
        val map = mutableMapOf<String, String>()
        val keys = params.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = params.opt(key)
            map[key] = when (value) {
                null, JSONObject.NULL -> ""
                is JSONObject, is JSONArray -> value.toString()
                is Number -> {
                    val number = value.toDouble()
                    if (number.isFinite() && number % 1.0 == 0.0) number.toLong().toString()
                    else value.toString()
                }
                else -> value.toString()
            }
        }
        return SingleToolCall(toolName = toolName, toolParams = map)
    }
}
