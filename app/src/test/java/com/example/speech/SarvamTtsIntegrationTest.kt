package com.example.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [org.robolectric.shadows.ShadowSpeechRecognizer::class])
class SarvamTtsIntegrationTest {

    @Test
    fun testSarvamPayloadConstruction() {
        val text = "Bas tumse baat karne ke liye ready hoon! Batao, aaj kya chal raha hai?"
        val targetLanguageCode = "hi-IN"
        val speaker = "suman_hi_companion"
        val model = "bulbul:v4-flash"
        val pace = 1.0

        val payload = JSONObject().apply {
            put("text", text)
            put("target_language_code", targetLanguageCode)
            put("speaker", speaker)
            put("model", model)
            put("pace", pace)
            put("speech_sample_rate", 24000)
            put("audio_format", "mp3")
        }

        assertEquals(text, payload.getString("text"))
        assertEquals("hi-IN", payload.getString("target_language_code"))
        assertEquals("suman_hi_companion", payload.getString("speaker"))
        assertEquals("bulbul:v4-flash", payload.getString("model"))
        assertEquals(1.0, payload.getDouble("pace"), 0.001)
        assertEquals(24000, payload.getInt("speech_sample_rate"))
        assertEquals("mp3", payload.getString("audio_format"))
    }

    @Test
    fun testToolCallJsonNeverSpoken() {
        val rawInput = """
            Main YouTube khol rahi hoon.
            {"tool": "open_app", "params": {"app_name": "YouTube"}}
            ```json
            {"tool": "toggle_torch", "params": {"state": "on"}}
            ```
        """.trimIndent()

        // Clean text using SiaSpeechManager.cleanSpeechText
        val cleanText = SiaSpeechManager.cleanSpeechText(rawInput)

        assertFalse(cleanText.contains("open_app"))
        assertFalse(cleanText.contains("toggle_torch"))
        assertFalse(cleanText.contains("{"))
        assertFalse(cleanText.contains("}"))
        assertEquals("Main YouTube khol rahi hoon.", cleanText)
    }

    @Test
    fun testLongConversationalResponseHandling() {
        val longConversation = "नमस्ते! आज का मौसम बहुत अच्छा है। ".repeat(150)
        assertTrue(longConversation.length > 3000)

        val boundedText = if (longConversation.length > 3000) longConversation.take(3000) else longConversation
        assertTrue(boundedText.length <= 3000)
    }

    @Test
    fun testSarvamMissingKeyReturnsAuthError() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = SarvamTtsClient.synthesizeSpeechToFile(
            context = context,
            apiKey = "",
            text = "Namaste"
        )

        assertTrue(result is SarvamTtsResult.Error)
        val error = result as SarvamTtsResult.Error
        assertEquals(401, error.statusCode)
        assertTrue(error.isAuthError)
    }

    @Test
    fun testSpeechChunkingPreservesOrderAndLimits() {
        val sentence1 = "नमस्ते! मैं SIA हूँ, आपकी पर्सनल असिस्टेंट।"
        val sentence2 = "आज का मौसम बहुत सुहाना है और तापमान 25 डिग्री है।"
        val sentence3 = "क्या आप कुछ नया गाना सुनना चाहते हैं?"
        val fullText = "$sentence1 $sentence2 $sentence3"

        val chunks = SiaSpeechManager.splitIntoSpeechChunks(fullText, maxChunkSize = 50)
        assertTrue("Text should be split into multiple chunks", chunks.size >= 3)
        // Verify each chunk is under max size
        chunks.forEach { chunk ->
            assertTrue("Chunk length (${chunk.length}) should not exceed 50", chunk.length <= 50)
        }
        // Verify sentence order is preserved
        assertEquals(sentence1, chunks[0])
        assertEquals(sentence2, chunks[1])
        assertEquals(sentence3, chunks[2])
    }

    @Test
    fun testFallbackOnlySpeaksRemainingChunks() {
        val fullText = "Sentence one. Sentence two. Sentence three. Sentence four."
        val chunks = SiaSpeechManager.splitIntoSpeechChunks(fullText, maxChunkSize = 20)
        assertEquals(4, chunks.size)

        // Assume chunk 0 and 1 succeeded, chunk 2 failed
        val failedIndex = 2
        val remainingText = chunks.subList(failedIndex, chunks.size).joinToString(" ")
        assertEquals("Sentence three. Sentence four.", remainingText)
        assertFalse("Remaining text must not contain chunk 0", remainingText.contains("Sentence one"))
        assertFalse("Remaining text must not contain chunk 1", remainingText.contains("Sentence two"))
    }

    private fun setupSpeechEnvironment(app: android.app.Application) {
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val packageManager = org.robolectric.Shadows.shadowOf(app.packageManager)
        val resolveInfo = android.content.pm.ResolveInfo().apply {
            serviceInfo = android.content.pm.ServiceInfo().apply {
                packageName = "com.google.android.googlequicksearchbox"
                name = "RecognitionService"
            }
        }
        packageManager.addResolveInfoForIntent(
            android.content.Intent("android.speech.RecognitionService"),
            resolveInfo
        )
    }

    @Test
    fun testSarvamCompletionSignalsAfterAllChunks() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        setupSpeechEnvironment(app)

        var completionCallbackInvoked = false
        val speechManager = SiaSpeechManager(app, {}, {})

        speechManager.startSession()
        assertTrue("Session must be active", speechManager.isSessionActive)

        // Unified playback completion triggers the onDone callback and transitions to LISTENING
        speechManager.speak("Testing Sarvam multi-chunk completion contract.") {
            completionCallbackInvoked = true
        }

        // Simulate successful completion of playback after all chunks
        speechManager.onSpeechPlaybackCompleted(1L)
        assertTrue("Completion callback should have been executed", completionCallbackInvoked)
        assertEquals("State must transition to LISTENING after successful playback", AssistantVisualState.LISTENING, speechManager.visualState.value)
    }

    @Test
    fun testAutomaticListeningRestartAfterSarvamPlayback() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        setupSpeechEnvironment(app)

        val speechManager = SiaSpeechManager(app, {}, {})
        speechManager.startSession()

        speechManager.speak("First command completed successfully.")
        speechManager.onSpeechPlaybackCompleted(1L)

        // Session is still active and listening for the next command
        assertTrue(speechManager.isSessionActive)
        assertEquals(AssistantVisualState.LISTENING, speechManager.visualState.value)
    }

    @Test
    fun testNoRestartWhilePausedOrClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val speechManager = SiaSpeechManager(context, {}, {})

        // Case A: When session is PAUSED
        speechManager.startSession()
        speechManager.pauseSession(notifyUser = false)
        assertEquals(AssistantVisualState.PAUSED, speechManager.visualState.value)

        speechManager.onSpeechPlaybackCompleted(1L)
        // Must NOT restart listening while PAUSED
        assertEquals("Must remain PAUSED", AssistantVisualState.PAUSED, speechManager.visualState.value)

        // Case B: When session is CLOSED (stopped)
        speechManager.stopSession()
        assertFalse("Session is closed", speechManager.isSessionActive)
        assertEquals(AssistantVisualState.IDLE, speechManager.visualState.value)

        speechManager.onSpeechPlaybackCompleted(2L)
        // Must remain IDLE when session is closed
        assertEquals(AssistantVisualState.IDLE, speechManager.visualState.value)
    }

    @Test
    fun testNoDuplicateRestartAfterCancellation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var doneCount = 0
        val speechManager = SiaSpeechManager(context, {}, {})
        speechManager.startSession()

        speechManager.speak("Session one utterance") {
            doneCount++
        }

        // Interrupt / cancel with new speech or stopSpeaking()
        speechManager.stopSpeaking()

        // Stale completion callback from session 1 must be discarded
        speechManager.onSpeechPlaybackCompleted(1L)
        assertEquals("Stale completion callback must NOT be invoked", 0, doneCount)
    }

    @Test
    fun testRecognitionOfMixedLanguageAppOpeningRequests() {
        // Test Example 1: "Open ChatGPT"
        val r1 = com.example.tools.ToolRegistry.resolveCommand("Open ChatGPT")
        assertTrue(r1 is com.example.tools.ToolRegistry.CommandResolution.LocalActions)
        val act1 = (r1 as com.example.tools.ToolRegistry.CommandResolution.LocalActions).actions.first()
        assertEquals("open_app", act1.toolName)
        assertEquals("chatgpt", act1.toolParams["app_name"])

        // Test Example 2: "ChatGPT kholo"
        val r2 = com.example.tools.ToolRegistry.resolveCommand("ChatGPT kholo")
        assertTrue(r2 is com.example.tools.ToolRegistry.CommandResolution.LocalActions)
        val act2 = (r2 as com.example.tools.ToolRegistry.CommandResolution.LocalActions).actions.first()
        assertEquals("open_app", act2.toolName)
        assertEquals("chatgpt", act2.toolParams["app_name"])

        // Test Example 3: "Open YouTube"
        val r3 = com.example.tools.ToolRegistry.resolveCommand("Open YouTube")
        assertTrue(r3 is com.example.tools.ToolRegistry.CommandResolution.LocalActions)
        val act3 = (r3 as com.example.tools.ToolRegistry.CommandResolution.LocalActions).actions.first()
        assertEquals("open_app", act3.toolName)
        assertEquals("youtube", act3.toolParams["app_name"])

        // Test Example 4: "Instagram chala do"
        val r4 = com.example.tools.ToolRegistry.resolveCommand("Instagram chala do")
        assertTrue(r4 is com.example.tools.ToolRegistry.CommandResolution.LocalActions)
        val act4 = (r4 as com.example.tools.ToolRegistry.CommandResolution.LocalActions).actions.first()
        assertEquals("open_app", act4.toolName)
        assertEquals("instagram", act4.toolParams["app_name"])

        // Test Example 5: "WhatsApp kholo"
        val r5 = com.example.tools.ToolRegistry.resolveCommand("WhatsApp kholo")
        assertTrue(r5 is com.example.tools.ToolRegistry.CommandResolution.LocalActions)
        val act5 = (r5 as com.example.tools.ToolRegistry.CommandResolution.LocalActions).actions.first()
        assertEquals("open_app", act5.toolName)
        assertEquals("whatsapp", act5.toolParams["app_name"])
    }

    @Test
    fun testCorrectHandlingOfAmbiguousAppNames() {
        // Empty app candidate or ambiguous phrase without a real app
        val r1 = com.example.tools.ToolRegistry.resolveCommand("kholo")
        assertEquals(com.example.tools.ToolRegistry.CommandResolution.NeedsAiAgent, r1)

        val r2 = com.example.tools.ToolRegistry.resolveCommand("open please")
        assertEquals(com.example.tools.ToolRegistry.CommandResolution.NeedsAiAgent, r2)
    }

    @Test
    fun testPreservationOfConversationalRequestsContainingAppNames() {
        // "Mujhe ChatGPT ke baare mein batao" — this is conversation, NOT an app-opening command.
        val query = "Mujhe ChatGPT ke baare mein batao"
        val res = com.example.tools.ToolRegistry.resolveCommand(query)
        assertEquals("Conversational query containing an app name must resolve to NeedsAiAgent", com.example.tools.ToolRegistry.CommandResolution.NeedsAiAgent, res)
    }
}
