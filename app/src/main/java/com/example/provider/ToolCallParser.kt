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
            if (parsed.isNotEmpty()) {
                calls.addAll(parsed)
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

        return LlmCallResult(
            textReply = cleaned.replace(Regex("\\n{3,}"), "\\n\\n").trim(),
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
        val toolName = obj.optString("tool").ifBlank { obj.optString("name") }
        if (toolName.isBlank()) return null

        val params = obj.optJSONObject("params")
            ?: obj.optJSONObject("arguments")
            ?: obj.optJSONObject("input")
            ?: JSONObject()
        val map = mutableMapOf<String, String>()
        val keys = params.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = params.opt(key)
            map[key] = when (value) {
                null, JSONObject.NULL -> ""
                is JSONObject, is JSONArray -> value.toString()
                else -> value.toString()
            }
        }
        return SingleToolCall(toolName = toolName, toolParams = map)
    }
}
