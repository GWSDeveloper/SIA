package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
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
                if (displayName.isNotBlank() && number.isNotBlank() && normalize(displayName) == requested) matches.add(displayName to number)
            }
        }

        val uniqueNumbers = matches.distinctBy { normalize(it.second) }
        if (uniqueNumbers.isEmpty()) return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No exact contact match was found for '$name'.")
        if (uniqueNumbers.size > 1) {
            val options = uniqueNumbers.joinToString("; ") { "${it.first}: ${it.second}" }
            return ToolExecutionResult(ToolResultStatus.USER_CONFIRMATION_REQUIRED, "AMBIGUOUS_CONTACTS: More than one phone number matches '$name'. Ask which one to call: $options")
        }

        val match = uniqueNumbers.single()
        return try {
            val number = match.second
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolExecutionResult(ToolResultStatus.SUCCESS, "Call started for ${match.first}.")
            } else {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolExecutionResult(ToolResultStatus.USER_CONFIRMATION_REQUIRED, "CALL_PERMISSION_REQUIRED: Opened the dialer for ${match.first}; tap Call to continue.")
            }
        } catch (_: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Android could not start the call.")
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\\\s+"), " ")
}
