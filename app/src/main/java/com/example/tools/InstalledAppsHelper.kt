package com.example.tools

import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Utility to query installed launchable apps and match spoken/STT app targets dynamically.
 */
object InstalledAppsHelper {

    /**
     * Dynamically queries the PackageManager for all launchable application labels.
     */
    fun getInstalledAppLabels(context: Context): List<String> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos.mapNotNull {
                try {
                    it.loadLabel(pm).toString().trim()
                } catch (_: Exception) {
                    null
                }
            }.filter { it.isNotBlank() }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves an app name candidate against installed application labels.
     * Supports exact match, normalized punctuation/spacing match, and Devanagari transliteration.
     * Returns the exact label of the matching app if unambiguous, or null if ambiguous/not found.
     */
    fun findMatchingInstalledApp(candidate: String, installedAppLabels: List<String>): String? {
        val cleanCandidate = candidate.trim().lowercase(Locale.ROOT)
        if (cleanCandidate.isBlank() || installedAppLabels.isEmpty()) return null

        val normCandidate = cleanCandidate.replace(" ", "").replace("-", "").replace(".", "")

        // 1. Direct match (exact or whitespace-normalized)
        val directMatches = installedAppLabels.filter { label ->
            val lower = label.lowercase(Locale.ROOT)
            val norm = lower.replace(" ", "").replace("-", "").replace(".", "")
            lower == cleanCandidate || norm == normCandidate
        }
        if (directMatches.size == 1) {
            return directMatches.first()
        } else if (directMatches.size > 1) {
            // Ambiguous
            return null
        }

        // 2. Transliteration / Phonetic mapping for Devanagari app names (e.g. चैट जीपीटी -> ChatGPT)
        val transliterated = transliterateDevanagariToLatin(cleanCandidate)
        if (transliterated.isNotBlank() && transliterated != cleanCandidate) {
            val normTrans = transliterated.replace(" ", "").replace("-", "").replace(".", "")
            val transliteratedMatches = installedAppLabels.filter { label ->
                val normLabel = label.lowercase(Locale.ROOT).replace(" ", "").replace("-", "").replace(".", "")
                normLabel == normTrans || normLabel.contains(normTrans) || normTrans.contains(normLabel)
            }
            if (transliteratedMatches.size == 1) {
                return transliteratedMatches.first()
            } else if (transliteratedMatches.size > 1) {
                return null
            }
        }

        // 3. Substring match (e.g. "whatsapp" matching "WhatsApp Messenger")
        val substringMatches = installedAppLabels.filter { label ->
            val normLabel = label.lowercase(Locale.ROOT).replace(" ", "").replace("-", "").replace(".", "")
            normLabel.contains(normCandidate) || normCandidate.contains(normLabel)
        }
        if (substringMatches.size == 1) {
            return substringMatches.first()
        }

        return null
    }

    /**
     * Simple phonetic transliteration mapping Devanagari syllables into Latin characters.
     */
    fun transliterateDevanagariToLatin(text: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = text.length
        while (i < len) {
            val ch = text[i]
            when (ch) {
                'च' -> sb.append("ch")
                'छ' -> sb.append("chh")
                'ज' -> sb.append("j")
                'झ' -> sb.append("jh")
                'ट' -> sb.append("t")
                'ठ' -> sb.append("th")
                'ड' -> sb.append("d")
                'ढ' -> sb.append("dh")
                'त' -> sb.append("t")
                'थ' -> sb.append("th")
                'द' -> sb.append("d")
                'ध' -> sb.append("dh")
                'न' -> sb.append("n")
                'प' -> sb.append("p")
                'फ' -> sb.append("f")
                'ब' -> sb.append("b")
                'भ' -> sb.append("bh")
                'म' -> sb.append("m")
                'य' -> sb.append("y")
                'र' -> sb.append("r")
                'ल' -> sb.append("l")
                'व' -> sb.append("v")
                'श', 'ष' -> sb.append("sh")
                'स' -> sb.append("s")
                'ह' -> sb.append("h")
                'ग' -> sb.append("g")
                'घ' -> sb.append("gh")
                'क' -> sb.append("k")
                'ख' -> sb.append("kh")
                'ा' -> sb.append("a")
                'ि', 'ी' -> sb.append("i")
                'ु', 'ू' -> sb.append("u")
                'े' -> sb.append("e")
                'ै' -> sb.append("ai")
                'ो' -> sb.append("o")
                'ौ' -> sb.append("au")
                '्' -> {} // virama / half-character
                'ं' -> sb.append("n")
                ' ' -> sb.append(" ")
                in 'a'..'z', in 'A'..'Z', in '0'..'9' -> sb.append(ch)
            }
            i++
        }
        return sb.toString().trim()
    }
}
