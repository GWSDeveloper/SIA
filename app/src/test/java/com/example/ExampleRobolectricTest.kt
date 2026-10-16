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
        assertTrue(toolNames.contains("control_media"))
        assertTrue(toolNames.contains("set_volume"))
        assertTrue(toolNames.contains("schedule_task"))
        assertTrue(toolNames.contains("list_all_commands"))
    }

    @Test
    fun `local intent matcher parses flashlight and app commands`() {
        val torchMatches = ToolRegistry.matchLocalIntent("torch on please")
        assertEquals(1, torchMatches.size)
        assertEquals("toggle_torch", torchMatches[0].toolName)
        assertEquals("on", torchMatches[0].toolParams["state"])

        val appMatches = ToolRegistry.matchLocalIntent("open youtube")
        assertEquals(1, appMatches.size)
        assertEquals("open_app", appMatches[0].toolName)
        assertEquals("youtube", appMatches[0].toolParams["app_name"])

        val volMatches = ToolRegistry.matchLocalIntent("volume to 75%")
        assertEquals(1, volMatches.size)
        assertEquals("set_volume", volMatches[0].toolName)
        assertEquals("75", volMatches[0].toolParams["level"])
    }

    @Test
    fun `multi action parsing extracts all requested tools`() {
        val multiMatches = ToolRegistry.matchLocalIntent(
            "open youtube, turn on the torch, and set volume to 30 percent"
        )
        assertEquals(3, multiMatches.size)
        assertEquals("open_app", multiMatches[0].toolName)
        assertEquals("youtube", multiMatches[0].toolParams["app_name"])
        assertEquals("toggle_torch", multiMatches[1].toolName)
        assertEquals("on", multiMatches[1].toolParams["state"])
        assertEquals("set_volume", multiMatches[2].toolName)
        assertEquals("30", multiMatches[2].toolParams["level"])
    }

    @Test
    fun `tool call parser strips raw tool markdown from user text`() {
        val rawLlmOutput = """
            ```tool_calls
            [{"tool": "toggle_torch", "params": {"state": "on"}}]
            ```
            Haanji, torch on kar di hai!
        """.trimIndent()

        val parsed = ToolCallParser.parse(rawLlmOutput)
        assertEquals(1, parsed.toolCalls.size)
        assertEquals("toggle_torch", parsed.toolCalls[0].toolName)
        assertFalse(parsed.textReply.contains("```"))
        assertFalse(parsed.textReply.contains("tool_calls"))
        assertFalse(parsed.textReply.contains("{"))
        assertEquals("Haanji, torch on kar di hai!", parsed.textReply)

        // Verify cleanUserFacingText
        val clean = cleanUserFacingText(rawLlmOutput)
        assertFalse(clean.contains("tool_call"))
    }

    @Test
    fun `torch open kardo resolves to torch on not app named kardo`() {
        val resolution = ToolRegistry.resolveCommand("torch open kardo")
        assertTrue("Expected LocalActions but got NeedsAiAgent", resolution is ToolRegistry.CommandResolution.LocalActions)
        val actions = (resolution as ToolRegistry.CommandResolution.LocalActions).actions
        assertEquals(1, actions.size)
        assertEquals("toggle_torch", actions[0].toolName)
        assertEquals("on", actions[0].toolParams["state"])
    }

    @Test
    fun `hinglish torch variations resolve accurately`() {
        val testCases = listOf(
            "Torch on karo" to "on",
            "Torch chalu kar do" to "on",
            "Torch band kar do" to "off",
            "Torch off" to "off"
        )
        for ((phrase, expectedState) in testCases) {
            val res = ToolRegistry.resolveCommand(phrase)
            assertTrue("Phrase '$phrase' should resolve to LocalActions", res is ToolRegistry.CommandResolution.LocalActions)
            val actions = (res as ToolRegistry.CommandResolution.LocalActions).actions
            assertEquals(1, actions.size)
            assertEquals("toggle_torch", actions[0].toolName)
            assertEquals(expectedState, actions[0].toolParams["state"])
        }
    }

    @Test
    fun `app open variations resolve accurately`() {
        val testCases = listOf(
            "YouTube kholo" to "youtube",
            "Open YouTube" to "youtube",
            "YouTube open kardo" to "youtube"
        )
        for ((phrase, expectedApp) in testCases) {
            val res = ToolRegistry.resolveCommand(phrase)
            assertTrue("Phrase '$phrase' should resolve to LocalActions", res is ToolRegistry.CommandResolution.LocalActions)
            val actions = (res as ToolRegistry.CommandResolution.LocalActions).actions
            assertEquals(1, actions.size)
            assertEquals("open_app", actions[0].toolName)
            assertEquals(expectedApp, actions[0].toolParams["app_name"])
        }
    }

    @Test
    fun `open kardo without target forwards to AI agent`() {
        val res = ToolRegistry.resolveCommand("open kardo")
        assertTrue("open kardo must not open an app called kardo, should forward to AI agent", res is ToolRegistry.CommandResolution.NeedsAiAgent)
    }

    @Test
    fun `conversational utterance with off forwards to AI agent`() {
        val res = ToolRegistry.resolveCommand("Aaj mood off hai")
        assertTrue("Conversational sentence must forward to AI agent", res is ToolRegistry.CommandResolution.NeedsAiAgent)
    }

    @Test
    fun `multi action preserves all actions and does not drop subsequent ones`() {
        // Issue 4: "WiFi on karo aur YouTube kholo"
        val wifiAndYt = ToolRegistry.resolveCommand("WiFi on karo aur YouTube kholo")
        assertTrue("Compound action must resolve both actions", wifiAndYt is ToolRegistry.CommandResolution.LocalActions)
        val actions = (wifiAndYt as ToolRegistry.CommandResolution.LocalActions).actions
        assertEquals(2, actions.size)
        assertEquals("open_system_settings", actions[0].toolName)
        assertEquals("wifi", actions[0].toolParams["type"])
        assertEquals("open_app", actions[1].toolName)
        assertEquals("youtube", actions[1].toolParams["app_name"])

        // Issue 4: "torch on karo aur YouTube kholo"
        val torchAndYt = ToolRegistry.resolveCommand("torch on karo aur YouTube kholo")
        assertTrue(torchAndYt is ToolRegistry.CommandResolution.LocalActions)
        val torchActions = (torchAndYt as ToolRegistry.CommandResolution.LocalActions).actions
        assertEquals(2, torchActions.size)
        assertEquals("toggle_torch", torchActions[0].toolName)
        assertEquals("on", torchActions[0].toolParams["state"])
        assertEquals("open_app", torchActions[1].toolName)
        assertEquals("youtube", torchActions[1].toolParams["app_name"])
    }

    @Test
    fun `semantic torch variations all map cleanly to toggle_torch`() {
        val testCases = listOf(
            "Torch on" to "on",
            "Torch chalu kar do" to "on",
            "Torch jala do" to "on",
            "Flashlight enable kar do" to "on",
            "Torch on kar dena" to "on",
            "Torch band kar do" to "off",
            "Torch off" to "off"
        )
        for ((phrase, expectedState) in testCases) {
            val res = ToolRegistry.resolveCommand(phrase)
            assertTrue("Phrase '$phrase' should resolve to Resolved", res is ToolRegistry.CommandResolution.Resolved)
            val action = (res as ToolRegistry.CommandResolution.Resolved).action
            assertEquals("toggle_torch", action.toolName)
            assertEquals(expectedState, action.toolParams["state"])
        }
    }

    @Test
    fun `conversational phrases and questions route to NeedsAiAgent`() {
        val conversational = listOf(
            "How are you?",
            "Who are you?",
            "Kaise ho?",
            "Aaj mood off hai",
            "dimag kharab hai"
        )
        for (phrase in conversational) {
            val res = ToolRegistry.resolveCommand(phrase)
            assertTrue("Phrase '$phrase' must be classified as NeedsAiAgent", res is ToolRegistry.CommandResolution.NeedsAiAgent)
        }
    }

    @Test
    fun `unsupported hardware commands route to NeedsAiAgent`() {
        // Mobile data cannot be programmatically toggled without root/system signature
        val res = ToolRegistry.resolveCommand("Mobile data off karo")
        assertTrue("Mobile data off karo must route to NeedsAiAgent", res is ToolRegistry.CommandResolution.NeedsAiAgent)
    }

    @Test
    fun `compound action with conversational part forwards entire request to AI agent`() {
        val res = ToolRegistry.resolveCommand("Torch on karo aur mujhe ek story sunao")
        assertTrue("Compound request containing conversational task must not drop story", res is ToolRegistry.CommandResolution.NeedsAiAgent)
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
