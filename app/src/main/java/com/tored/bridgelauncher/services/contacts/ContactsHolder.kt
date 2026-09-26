package com.tored.bridgelauncher.services.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

private const val TAG = "ContactsHolder"

// the provider reports many changes in a row while syncing
private const val CHANGE_DEBOUNCE_MS = 1000L

@Serializable
data class SerializablePhoneNumber(
    val number: String,
    /** "Móvil", "Casa"… in the device's language, or the custom label. */
    val label: String?,
    /** The contact's default number. */
    val isPrimary: Boolean,
)

@Serializable
data class SerializableContact(
    val id: Long,
    /** Stable across syncs; what the photo and open methods take. */
    val lookupKey: String,
    val name: String,
    /** Marked as favorite in the contacts app. */
    val starred: Boolean,
    val hasPhoto: Boolean,
    val phoneNumbers: List<SerializablePhoneNumber>,
)

/** The user's contacts that have a phone number (needs READ_CONTACTS), with a debounced change signal. */
class ContactsHolder(
    private val _context: Context,
)
{
    private val _handler = Handler(Looper.getMainLooper())
    private var _isObserving = false

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()

    private val _emitChange = Runnable { _changes.tryEmit(Unit) }

    val canRead: Boolean
        get() = ContextCompat.checkSelfPermission(_context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    val canCall: Boolean
        get() = ContextCompat.checkSelfPermission(_context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** Starts watching the contacts provider; only works once READ_CONTACTS is granted, so it's retried when it may have been. */
    fun startObservingIfPossible()
    {
        if (_isObserving || !canRead) return
        try
        {
            _context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                object : ContentObserver(_handler)
                {
                    override fun onChange(selfChange: Boolean)
                    {
                        _handler.removeCallbacks(_emitChange)
                        _handler.postDelayed(_emitChange, CHANGE_DEBOUNCE_MS)
                    }
                },
            )
            _isObserving = true
        }
        catch (ex: Exception)
        {
            Log.w(TAG, "Could not observe the contacts", ex)
        }
    }

    /**
     * Contacts with at least one phone number, by name. [query] matches names and numbers the way the
     * contacts app searches; [starredOnly] keeps only favorites.
     */
    fun getContacts(query: String?, starredOnly: Boolean, limit: Int?): List<SerializableContact>
    {
        if (!canRead) throw SecurityException("Bridge doesn't have the contacts permission.")

        val uri = if (query.isNullOrBlank()) Phone.CONTENT_URI
        else Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(query.trim()))

        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.STARRED,
            Phone.PHOTO_ID,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            Phone.IS_SUPER_PRIMARY,
        )

        // several rows per contact (one per number), grouped keeping the name order
        val byId = LinkedHashMap<Long, SerializableContact>()
        _context.contentResolver.query(
            uri,
            projection,
            if (starredOnly) "${Phone.STARRED} = 1" else null,
            null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext())
            {
                val id = c.getLong(0)
                val number = c.getString(5)?.takeIf { it.isNotBlank() } ?: continue
                val phone = SerializablePhoneNumber(
                    number = number,
                    label = Phone.getTypeLabel(_context.resources, c.getInt(6), c.getString(7))?.toString(),
                    isPrimary = c.getInt(8) != 0,
                )

                val existing = byId[id]
                if (existing != null)
                {
                    // the same number can be stored twice (e.g. from two accounts)
                    if (existing.phoneNumbers.none { samePhone(it.number, number) })
                        byId[id] = existing.copy(phoneNumbers = existing.phoneNumbers + phone)
                    continue
                }
                if (limit != null && byId.size >= limit) continue

                byId[id] = SerializableContact(
                    id = id,
                    lookupKey = c.getString(1) ?: continue,
                    name = c.getString(2) ?: number,
                    starred = c.getInt(3) != 0,
                    hasPhoto = !c.isNull(4) && c.getLong(4) != 0L,
                    phoneNumbers = listOf(phone),
                )
            }
        }
        // the default number first
        return byId.values.map { contact -> contact.copy(phoneNumbers = contact.phoneNumbers.sortedByDescending { it.isPrimary }) }
    }

    /** The contact's photo (high resolution when there is one), or null. */
    fun getPhoto(lookupKey: String): ByteArray?
    {
        if (!canRead) throw SecurityException("Bridge doesn't have the contacts permission.")
        val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        val contactUri = ContactsContract.Contacts.lookupContact(_context.contentResolver, lookupUri) ?: return null
        return ContactsContract.Contacts.openContactPhotoInputStream(_context.contentResolver, contactUri, true)
            ?.use { it.readBytes() }
    }

    fun getContactUri(lookupKey: String): Uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)

    private fun samePhone(a: String, b: String) = a.filter { it.isDigit() || it == '+' } == b.filter { it.isDigit() || it == '+' }
}
