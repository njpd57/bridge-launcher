package com.tored.bridgelauncher.api2.shared

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.tored.bridgelauncher.utils.RawRepresentable
import com.tored.bridgelauncher.utils.q

enum class DefaultAppRoleStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    Dialer("dialer"),
    Browser("browser"),
    SMS("sms"),
    Email("email"),
    Camera("camera"),
    ;

    /** An intent that resolves to the user's default app for this role. */
    fun createIntent() = when (this)
    {
        Dialer -> Intent(Intent.ACTION_DIAL)
        Browser -> Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        Email -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        Camera -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    }

    companion object
    {
        fun fromStringOrThrow(role: String): DefaultAppRoleStringOptions
        {
            return entries.firstOrNull { it.rawValue == role }
                ?: throw Exception("Argument \"role\" must be one of ${entries.joinToString { q(it) }} (got ${q(role)}).")
        }
    }
}
