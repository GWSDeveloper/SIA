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
