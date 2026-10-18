package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.icu.text.Transliterator
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/** Exact contact-name resolver. Ambiguous names are never dialled automatically. */
class ContactAgent(private val context: Context) {
    fun callContact(name: String): ToolExecutionResult {
        val requested = normalize(name)
        if (requested.isBlank()) return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Please tell me the contact name.")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "CONTACT_PERMISSION_REQUIRED: Allow Contacts access in Android permissions to search your contacts.")
        }

        val matches = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIndex >= 0 && numberIndex >= 0) while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)?.trim().orEmpty()
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                if (displayName.isNotBlank() && number.isNotBlank() && (normalize(displayName) == requested || transliterated(displayName) == transliterated(name))) matches.add(displayName to number)
            }
        }

        val uniqueNumbers = matches.distinctBy { normalize(it.second) }
        if (uniqueNumbers.isEmpty()) return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No exact contact match was found for '$name'.")
        if (uniqueNumbers.size > 1) {
            val options = uniqueNumbers.joinToString("; ") { "${it.first}: ${it.second}" }
            return ToolExecutionResult(ToolResultStatus.USER_CONFIRMATION_REQUIRED, "AMBIGUOUS_CONTACTS: More than one phone number matches '$name'. Ask which one to call: $options")
        }

        val match = uniqueNumbers.single()
        return startCall(match.first, match.second)
    }

    fun callSpecificNumber(numberInput: String): ToolExecutionResult {
        val requestedDigits = numberInput.filter(Char::isDigit)
        if (requestedDigits.length < 7) return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Please choose one of the listed phone numbers.")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "CONTACT_PERMISSION_REQUIRED: Allow Contacts access.")
        }
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        var selected: Pair<String, String>? = null
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIndex >= 0 && numberIndex >= 0) while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)?.trim().orEmpty()
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                val digits = number.filter(Char::isDigit)
                if (displayName.isNotBlank() && digits.isNotBlank() &&
                    (digits == requestedDigits || digits.endsWith(requestedDigits) || requestedDigits.endsWith(digits))) {
                    if (selected != null && normalize(selected!!.second) != normalize(number)) {
                        return ToolExecutionResult(ToolResultStatus.USER_CONFIRMATION_REQUIRED, "More than one saved number matches that choice. Please specify more digits.")
                    }
                    selected = displayName to number
                }
            }
        }
        val match = selected ?: return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "That number is no longer available in contacts.")
        return startCall(match.first, match.second)
    }

    private fun startCall(displayName: String, number: String): ToolExecutionResult = try {
        // Open the dialer with the resolved number and require the user to press Call.
        // ACTION_CALL returning from startActivity is not proof that a call was actually
        // initiated/connected, and must never be reported as "Call started".
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        ToolExecutionResult(
            ToolResultStatus.USER_CONFIRMATION_REQUIRED,
            "CALL_CONFIRMATION_REQUIRED: Opened the dialer for $displayName. The call has NOT been placed; review the number and tap Call."
        )
    } catch (_: Exception) {
        ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Android could not open the dialer for $displayName.")
    }

    private fun transliterated(value: String): String = try {
        normalize(Transliterator.getInstance("Devanagari-Latin; Latin-ASCII").transliterate(value))
    } catch (_: Exception) {
        normalize(value)
    }

    private fun phoneticNormalize(value: String): String = transliterated(value)
        .replace("simh", "singh")
        .replace("sinh", "singh")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
