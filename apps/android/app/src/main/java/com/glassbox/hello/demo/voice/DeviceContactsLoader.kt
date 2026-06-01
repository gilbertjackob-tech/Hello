package com.glassbox.hello.demo.voice

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal suspend fun Context.loadVoiceDemoContacts(limit: Int = 250): List<DemoContactTarget> = withContext(Dispatchers.IO) {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
    )
    val contacts = linkedMapOf<String, DemoContactTarget>()
    contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
        while (cursor.moveToNext() && contacts.size < limit) {
            val contactId = cursor.getLong(idIndex)
            val displayName = cursor.getString(nameIndex)?.trim().orEmpty()
            if (displayName.isBlank()) continue
            val key = displayName.lowercase(Locale.ROOT)
            contacts.putIfAbsent(
                key,
                DemoContactTarget(
                    id = "device_$contactId",
                    displayName = displayName,
                    aliases = displayName.contactAliases()
                )
            )
        }
    }
    contacts.values.toList()
}

private fun String.contactAliases(): List<String> {
    val normalized = cleanForMatching()
    val parts = normalized.split(" ").filter { it.length > 1 }
    return buildList {
        add(this@contactAliases)
        add(normalized)
        parts.forEach { add(it) }
    }.map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}
