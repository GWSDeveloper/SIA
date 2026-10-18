package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.provider.ToolCallParser
import com.example.tools.ToolRegistry
import com.example.ui.screens.cleanUserFacingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches SIA`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SIA", appName)
    }

    @Test
    fun `tool registry contains essential common tools`() {
        val tools = ToolRegistry.getAllTools()
        val toolNames = tools.map { it.name }
        assertTrue(toolNames.contains("open_app"))
        assertTrue(toolNames.contains("web_search"))
        assertTrue(toolNames.contains("toggle_torch"))
        assertTrue(toolNames.contains("play"))
        assertTrue(toolNames.contains("pause"))
        assertFalse(toolNames.contains("control_media"))
        assertEquals(ToolRegistry.getAgentTools().map { it.name }, toolNames)
        assertTrue(toolNames.contains("set_volume"))
        assertTrue(toolNames.contains("schedule_task"))
        assertTrue(toolNames.contains("list_all_commands"))
    }

    @Test
    fun `agent registry exposes simple individual Android functions`() {
        val names = ToolRegistry.getAgentTools().map { it.name }
        assertTrue(names.contains("toggle_torch"))
        assertTrue(names.contains("set_brightness"))
        assertTrue(names.contains("set_volume"))
        assertTrue(names.contains("open_app"))
        assertTrue(names.contains("call"))
        assertTrue(names.contains("web_search"))
        assertTrue(names.contains("play_music"))
        assertTrue(names.contains("send_sms"))
        assertTrue(names.contains("vibrate"))
        assertTrue(names.contains("user_perma_memory"))
        assertFalse(names.contains("device_agent"))
        assertFalse(names.contains("app_agent"))
        assertFalse(names.contains("contact_agent"))
        assertFalse(names.contains("media_agent"))
        assertFalse(names.contains("web_agent"))
    }

    @Test
    fun `shared prompt refers only to registered tool names`() {
        val prompt = com.example.provider.SiaSystemPrompt.build("SIA")
        assertTrue(prompt.contains("web_search"))
        assertTrue(prompt.contains("play_music"))
        assertTrue(prompt.contains("call with the saved contact name"))
        assertFalse(prompt.contains("web_agent"))
        assertFalse(prompt.contains("media_agent"))
        assertFalse(prompt.contains("contact_agent"))
    }

    @Test
    fun `compound tool protocol preserves nested parameter text and every action`() {
        val raw = """Preparing the requested actions.
```tool_calls
[{"tool":"send_sms","params":{"number":"1234567890","message":"Hello {friend}, use } carefully"}},{"tool":"open_app","params":{"app_name":"WhatsApp"}},{"tool":"set_volume","params":{"level":37.0}}]
```
"""
        val parsed = ToolCallParser.parse(raw)

        assertEquals(3, parsed.toolCalls.size)
        assertEquals("send_sms", parsed.toolCalls[0].toolName)
        assertEquals("Hello {friend}, use } carefully", parsed.toolCalls[0].toolParams["message"])
        assertEquals("WhatsApp", parsed.toolCalls[1].toolParams["app_name"])
        assertEquals("37", parsed.toolCalls[2].toolParams["level"])
        assertEquals("Preparing the requested actions.", parsed.textReply)
    }

    @Test
    fun `ordinary JSON with a name field is not treated as a tool call`() {
        val raw = """{"name":"Shantanu","age":18}"""
        val parsed = ToolCallParser.parse(raw)

        assertTrue(parsed.toolCalls.isEmpty())
        assertEquals(raw, parsed.textReply)
    }

    @Test
    fun `wake phrase and stop phrase dynamic settings take effect`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.preferences.SiaPreferences.getInstance(context)

        prefs.setWakeWordPhrase("Chitti")
        assertEquals("Chitti", prefs.getWakeWordPhrase())

        prefs.setStopPhrase("Alvida")
        assertEquals("Alvida", prefs.getStopPhrase())

        // Reset to default
        prefs.setWakeWordPhrase("Hey SIA")
        prefs.setStopPhrase("Stop listening")
    }
}
