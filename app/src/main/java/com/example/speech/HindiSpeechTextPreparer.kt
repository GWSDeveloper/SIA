package com.example.speech

import java.util.Locale

/**
 * Smart Hindi & Hinglish Speech-Text Preparation Layer for Android TextToSpeech.
 *
 * Runs 100% locally on-device at ₹0 budget without any external or paid APIs.
 *
 * Key Capabilities:
 * 1. Distinguishes between Devanagari Hindi, Romanized Hindi (Hinglish), and English text.
 * 2. Deterministically transliterates Romanized Hindi tokens into natural Devanagari script
 *    so that native Android Hindi TTS engines (e.g. Google Speech Services `hi-IN`) can
 *    pronounce them with authentic native phonetics.
 * 3. Preserves English product names, brands, technical terminology (YouTube, Bluetooth,
 *    Settings, Volume, etc.), numbers, percentages, and punctuation intact.
 * 4. Resolves ambiguities (such as English "the", "to", "do", "me", "is", "in" vs Hindi equivalents)
 *    so pure English sentences are never corrupted.
 * 5. Provides natural Hindi number word conversion (e.g. 50 -> पचास).
 */
object HindiSpeechTextPreparer {

    // Common technology, app, and brand terms preserved in Latin script for bilingual TTS
    private val PRESERVED_ENGLISH_WORDS = setOf(
        "youtube", "google", "whatsapp", "chrome", "spotify", "netflix",
        "facebook", "instagram", "twitter", "bluetooth", "wifi", "wi-fi",
        "hotspot", "settings", "volume", "percent", "flashlight",
        "app", "apps", "apk", "camera", "gallery", "calculator", "clock",
        "alarm", "timer", "reminder", "music", "play", "pause", "stop",
        "next", "previous", "screen", "brightness", "battery", "link",
        "web", "search", "browser", "device", "mobile", "phone", "audio",
        "video", "mic", "speaker", "android", "fast", "sia", "file", "files",
        "location", "gps", "map", "maps", "network", "call", "sms", "email", "gmail"
    )

    // Standard English function and helper words to prevent accidental English -> Hindi transliteration
    private val ENGLISH_COMMON_WORDS = setOf(
        "the", "is", "are", "am", "was", "were", "be", "been", "being",
        "in", "on", "at", "to", "for", "of", "with", "from", "by", "about",
        "an", "a", "this", "that", "these", "those", "it", "its",
        "have", "has", "had", "do", "does", "did", "will", "would", "shall", "should",
        "can", "could", "may", "might", "must",
        "you", "your", "yours", "they", "them", "their", "we", "us", "our",
        "he", "him", "his", "she", "her", "hers", "and", "or", "but", "not",
        "turned", "opened", "closed", "set", "scheduled", "completed", "failed",
        "playing", "paused", "searching", "sharing", "exporting", "flashlight",
        "done", "success", "error", "ready", "please", "yes", "no"
    )

    // Words that exist in both English and Romanized Hindi; only convert if strong Hindi context exists
    private val AMBIGUOUS_WORDS = setOf(
        "the", "to", "do", "me", "is", "in", "so", "us", "he", "hum", "mat", "par", "bar"
    )

    // Strong, unambiguous Romanized Hindi keywords
    private val UNAMBIGUOUS_HINDI_WORDS = setOf(
        "band", "khol", "kholo", "kholiye", "kholna", "kholti", "kholte",
        "rahi", "raha", "rahe", "hoon", "hun", "hai", "hain", "karo", "karein", "kijiye",
        "di", "diya", "diye", "dijiye", "gaya", "gayi", "gaye", "chalao", "chalu", "chalaye",
        "kya", "kaise", "kyun", "kyu", "kahan", "kaun", "kitna", "kitne", "kitni",
        "aap", "aapka", "aapki", "aapke", "tum", "tumhara", "tumhari", "mera", "meri", "mere",
        "mujhe", "mujhko", "hamara", "hamari", "humein", "yeh", "woh",
        "batao", "bataiye", "boliye", "bolo", "suno", "suniye", "samajh", "dhundo", "bhejo",
        "likho", "padho", "dekho", "aao", "jao", "ruko", "badhao", "ghatao", "kam", "zyada",
        "namaste", "namaskar", "haan", "haanji", "nahi", "nahin", "theek", "achha", "accha",
        "shukriya", "dhanyawad", "kripya", "alvida", "madad", "shuru", "khatam",
        "aawaz", "gana", "yaad", "baat", "kaam", "waqt", "subah", "shaam"
    )

    // Common Romanized Hindi words mapped to authentic Devanagari
    private val ROMAN_TO_HINDI_MAP = mapOf(
        // High-frequency verbs and verb particles
        "band" to "बंद",
        "kar" to "कर",
        "karo" to "करो",
        "karein" to "करें",
        "kijiye" to "कीजिए",
        "karna" to "करना",
        "karne" to "करने",
        "karni" to "करनी",
        "di" to "दी",
        "diya" to "दिया",
        "diye" to "दिए",
        "dijiye" to "दीजिए",
        "do" to "दो",
        "de" to "दे",
        "dena" to "देना",
        "khol" to "खोल",
        "kholo" to "खोलो",
        "kholiye" to "खोलिए",
        "kholna" to "खोलना",
        "chalao" to "चलाओ",
        "chala" to "चला",
        "chalu" to "चालू",
        "chalaye" to "चलाएं",
        "rahi" to "रही",
        "raha" to "रहा",
        "rahe" to "रहे",
        "hoon" to "हूँ",
        "hun" to "हूँ",
        "hai" to "है",
        "hain" to "हैं",
        "ho" to "हो",
        "hoga" to "होगा",
        "hogi" to "होगी",
        "honge" to "होंगे",
        "tha" to "था",
        "thi" to "थी",
        "the" to "थे",
        "gaya" to "गया",
        "gayi" to "गई",
        "gaye" to "गए",
        "sakta" to "सकता",
        "sakti" to "सकती",
        "sakte" to "सकते",
        "chahiye" to "चाहिए",
        "pata" to "पता",
        "lagao" to "लगाओ",
        "batao" to "बताओ",
        "bataiye" to "बताइए",
        "boliye" to "बोलिए",
        "bolo" to "बोलो",
        "suno" to "सुनो",
        "suniye" to "सुनिए",
        "samajh" to "समझ",
        "dhundo" to "ढूंढो",
        "bhejo" to "भेजो",
        "likho" to "लिखो",
        "padho" to "पढ़ो",
        "dekho" to "देखो",
        "aao" to "आओ",
        "jao" to "जाओ",
        "aaye" to "आए",
        "aaya" to "आया",
        "aayi" to "आई",
        "ruko" to "रुको",
        "rok" to "रोक",
        "roko" to "रोको",
        "badhao" to "बढ़ाओ",
        "ghatao" to "घटाओ",
        "kam" to "कम",
        "zyada" to "ज़्यादा",
        "jyada" to "ज़्यादा",

        // Question words and pronouns
        "kya" to "क्या",
        "kaise" to "कैसे",
        "kyun" to "क्यों",
        "kyu" to "क्यों",
        "kab" to "कब",
        "kahan" to "कहाँ",
        "kaun" to "कौन",
        "kitna" to "कितना",
        "kitne" to "कितने",
        "kitni" to "कितनी",
        "aap" to "आप",
        "aapka" to "आपका",
        "aapki" to "आपकी",
        "aapke" to "आपके",
        "tum" to "तुम",
        "tumhara" to "तुम्हारा",
        "tumhari" to "तुम्हारी",
        "tumhare" to "तुम्हारे",
        "main" to "मैं",
        "mera" to "मेरा",
        "meri" to "मेरी",
        "mere" to "मेरे",
        "mujhe" to "मुझे",
        "mujhko" to "मुझको",
        "hum" to "हम",
        "hamara" to "हमारा",
        "hamari" to "हमारी",
        "humein" to "हमें",
        "hume" to "हमें",
        "yeh" to "यह",
        "ye" to "ये",
        "woh" to "वह",
        "wo" to "वो",
        "iska" to "इसका",
        "iski" to "इसकी",
        "iske" to "इसके",
        "uska" to "उसका",
        "uski" to "उसकी",
        "uske" to "उसके",
        "unka" to "उनका",
        "unki" to "उनकी",
        "unke" to "उनके",
        "ise" to "इसे",
        "use" to "उसे",

        // Postpositions & conjunctions
        "aur" to "और",
        "ya" to "या",
        "lekin" to "लेकिन",
        "magar" to "मगर",
        "par" to "पर",
        "pe" to "पे",
        "se" to "से",
        "ko" to "को",
        "ka" to "का",
        "ke" to "के",
        "ki" to "की",
        "mein" to "में",
        "me" to "में",
        "bhi" to "भी",
        "to" to "तो",
        "toh" to "तो",
        "mat" to "मत",
        "sirf" to "सिर्फ",
        "bas" to "बस",
        "kuch" to "कुछ",
        "kuchh" to "कुछ",
        "sab" to "सब",
        "saare" to "सारे",
        "saari" to "सारी",
        "koi" to "कोई",
        "aaj" to "आज",
        "kal" to "कल",
        "ab" to "अब",
        "abhi" to "अभी",
        "pehle" to "पहले",
        "baad" to "बाद",
        "yahan" to "यहाँ",
        "wahan" to "वहाँ",
        "upar" to "ऊपर",
        "neeche" to "नीचे",
        "aage" to "आगे",
        "peeche" to "पीछे",
        "andar" to "अंदर",
        "bahar" to "बाहर",
        "paas" to "पास",
        "door" to "दूर",
        "saath" to "साथ",
        "bina" to "बिना",
        "tarah" to "तरह",
        "wala" to "वाला",
        "wali" to "वाली",
        "wale" to "वाले",

        // Common conversational expressions
        "namaste" to "नमस्ते",
        "namaskar" to "नमस्कार",
        "haan" to "हाँ",
        "ha" to "हाँ",
        "haanji" to "हाँजी",
        "nahi" to "नहीं",
        "nahin" to "नहीं",
        "theek" to "ठीक",
        "achha" to "अच्छा",
        "accha" to "अच्छा",
        "shukriya" to "शुक्रिया",
        "dhanyawad" to "धन्यवाद",
        "kripya" to "कृपया",
        "alvida" to "अलvida",
        "shubh" to "शुभ",
        "prabhat" to "प्रभात",
        "ratri" to "रात्रि",
        "dost" to "दोस्त",
        "bhai" to "भाई",
        "madad" to "मदद",
        "shuru" to "शुरू",
        "khatam" to "खत्म",
        "zarur" to "ज़रूर",
        "zaroor" to "ज़रूर",
        "zaroorat" to "ज़रूरत",
        "sahi" to "सही",
        "galat" to "गलत",
        "aawaz" to "आवाज़",
        "aawaaz" to "आवाज़",
        "gana" to "गाना",
        "gaana" to "गाना",
        "yaad" to "याद",
        "baat" to "बात",
        "kaam" to "काम",
        "samay" to "समय",
        "waqt" to "वक़्त",
        "din" to "दिन",
        "raat" to "रात",
        "subah" to "सुबह",
        "shaam" to "शाम",
        "minute" to "मिनट",
        "minat" to "मिनट",
        "second" to "सेकंड",
        "maaf" to "माफ़"
    )

    // Contextual tech / action terms (e.g. "torch band kar di" -> "टॉर्च बंद कर दी")
    private val CONTEXTUAL_WORD_MAP = mapOf(
        "torch" to "टॉर्च"
    )

    /**
     * Determines whether the given text contains Hindi or Romanized Hindi (Hinglish).
     */
    fun isHindiOrHinglish(text: String): Boolean {
        if (text.any { it in '\u0900'..'\u097F' }) {
            return true
        }
        val lower = text.lowercase(Locale.ROOT)
        val tokens = lower.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val unambiguousHindiCount = tokens.count { UNAMBIGUOUS_HINDI_WORDS.contains(it) || it == "kar" || it == "di" }
        return unambiguousHindiCount > 0
    }

    /**
     * Checks if the text is pure English.
     */
    fun isPureEnglish(text: String): Boolean {
        if (text.any { it in '\u0900'..'\u097F' }) return false
        val lower = text.lowercase(Locale.ROOT)
        val tokens = lower.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true

        val unambiguousHindiCount = tokens.count { UNAMBIGUOUS_HINDI_WORDS.contains(it) || it == "kar" || it == "di" }
        val englishWordsCount = tokens.count { ENGLISH_COMMON_WORDS.contains(it) || PRESERVED_ENGLISH_WORDS.contains(it) }

        if (unambiguousHindiCount == 0) {
            return true
        }
        return englishWordsCount > unambiguousHindiCount && unambiguousHindiCount == 0
    }

    /**
     * Prepares text for speech synthesis on Android TTS.
     *
     * Converts Romanized Hindi words into natural Devanagari script while
     * preserving English technical terms, brand names, numbers, and symbols.
     */
    fun prepareForSpeech(text: String): String {
        if (text.isBlank()) return ""

        // If pure English, preserve as-is without unwanted transformations
        if (isPureEnglish(text)) {
            return text
        }

        // Tokenize keeping delimiters (whitespace and punctuation)
        val regex = Regex("([a-zA-Z0-9]+|[^a-zA-Z0-9\\s]+|\\s+)")
        val matches = regex.findAll(text)

        val tokensList = mutableListOf<String>()
        for (match in matches) {
            tokensList.add(match.value)
        }

        val result = StringBuilder()

        var i = 0
        while (i < tokensList.size) {
            val token = tokensList[i]

            // If whitespace or punctuation or already Devanagari, preserve
            if (token.all { it.isWhitespace() } || !token.any { it.isLetterOrDigit() } || token.any { it in '\u0900'..'\u097F' }) {
                result.append(token)
                i++
                continue
            }

            // Numbers, percentages, URLs
            if (token.all { it.isDigit() }) {
                result.append(token)
                i++
                continue
            }

            val lowerToken = token.lowercase(Locale.ROOT)

            // Contextual word check (e.g., "torch band kar di" -> "टॉर्च बंद कर दी")
            if (CONTEXTUAL_WORD_MAP.containsKey(lowerToken)) {
                val hasHindiContext = checkHasHindiInProximity(tokensList, i)
                if (hasHindiContext) {
                    result.append(CONTEXTUAL_WORD_MAP[lowerToken])
                    i++
                    continue
                }
            }

            // Check if preserved English / brand term
            if (PRESERVED_ENGLISH_WORDS.contains(lowerToken)) {
                result.append(token)
                i++
                continue
            }

            // Check if ambiguous word (like "the", "to", "do", "me", "is", "in")
            if (AMBIGUOUS_WORDS.contains(lowerToken)) {
                val hasHindiContext = checkHasHindiInProximity(tokensList, i)
                if (!hasHindiContext) {
                    // In non-Hindi context, keep as English
                    result.append(token)
                    i++
                    continue
                }
            }

            // Check dictionary translation
            val hindiEquivalent = ROMAN_TO_HINDI_MAP[lowerToken]
            if (hindiEquivalent != null) {
                result.append(hindiEquivalent)
            } else {
                // Unrecognized word: preserve safely without mangling
                result.append(token)
            }

            i++
        }

        return result.toString()
    }

    /**
     * Checks if tokens nearby indicate a Hindi / Hinglish sentence context.
     */
    private fun checkHasHindiInProximity(tokens: List<String>, currentIndex: Int): Boolean {
        val start = (currentIndex - 6).coerceAtLeast(0)
        val end = (currentIndex + 6).coerceAtMost(tokens.size - 1)
        for (idx in start..end) {
            if (idx == currentIndex) continue
            val word = tokens[idx].trim().lowercase(Locale.ROOT)
            if (UNAMBIGUOUS_HINDI_WORDS.contains(word) || word == "kar" || word == "di" || tokens[idx].any { it in '\u0900'..'\u097F' }) {
                return true
            }
        }
        return false
    }

    /**
     * Converts a number (0..100) to its natural Hindi word representation for clear TTS speech.
     */
    fun numberToHindiWords(number: Int): String {
        return when (number) {
            0 -> "शून्य"
            1 -> "एक"
            2 -> "दो"
            3 -> "तीन"
            4 -> "चार"
            5 -> "पाँच"
            6 -> "छह"
            7 -> "सात"
            8 -> "आठ"
            9 -> "नौ"
            10 -> "दस"
            11 -> "ग्यारह"
            12 -> "बारह"
            13 -> "तेरह"
            14 -> "चौदह"
            15 -> "पंद्रह"
            16 -> "सोलह"
            17 -> "सत्रह"
            18 -> "अठारह"
            19 -> "उन्नीस"
            20 -> "बीस"
            25 -> "पच्चीस"
            30 -> "तीस"
            35 -> "पैंतीस"
            40 -> "चालीस"
            45 -> "पैंतालीस"
            50 -> "पचास"
            55 -> "पचपन"
            60 -> "साठ"
            65 -> "पैंसठ"
            70 -> "सत्तर"
            75 -> "पचहत्तर"
            80 -> "अस्सी"
            85 -> "पचासी"
            90 -> "नब्बे"
            95 -> "पंचानवे"
            100 -> "सौ"
            else -> number.toString()
        }
    }
}
