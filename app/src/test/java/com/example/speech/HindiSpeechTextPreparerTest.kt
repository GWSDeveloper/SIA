package com.example.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HindiSpeechTextPreparerTest {

    @Test
    fun testRequiredExample1_torchBandKarDi() {
        val input = "torch band kar di"
        val expected = "टॉर्च बंद कर दी"
        val actual = HindiSpeechTextPreparer.prepareForSpeech(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testRequiredExample2_youtubeKholRahiHoon() {
        val input = "YouTube khol rahi hoon"
        val expected = "YouTube खोल रही हूँ"
        val actual = HindiSpeechTextPreparer.prepareForSpeech(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testRequiredExample3_volume30PercentKarDo() {
        val input = "Volume 30 percent kar do"
        val expected = "Volume 30 percent कर दो"
        val actual = HindiSpeechTextPreparer.prepareForSpeech(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testRequiredExample4_bluetoothSettingsKholo() {
        val input = "Bluetooth settings kholo"
        val expected = "Bluetooth settings खोलो"
        val actual = HindiSpeechTextPreparer.prepareForSpeech(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testPureDevanagariPreserved() {
        val input = "हाँ, टॉर्च ऑन कर दी।"
        val actual = HindiSpeechTextPreparer.prepareForSpeech(input)
        assertEquals(input, actual)
    }

    @Test
    fun testPureEnglishPreserved() {
        val input = "Turned on the flashlight."
        val actual = HindiSpeechTextPreparer.prepareForSpeech(input)
        assertEquals(input, actual)
    }

    @Test
    fun testNumberToHindiWords() {
        assertEquals("पचास", HindiSpeechTextPreparer.numberToHindiWords(50))
        assertEquals("तीस", HindiSpeechTextPreparer.numberToHindiWords(30))
        assertEquals("शून्य", HindiSpeechTextPreparer.numberToHindiWords(0))
        assertEquals("सौ", HindiSpeechTextPreparer.numberToHindiWords(100))
    }
}
