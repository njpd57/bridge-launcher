package com.tored.bridgelauncher.api2.shared

import com.tored.bridgelauncher.utils.RawRepresentable
import com.tored.bridgelauncher.utils.q
import com.tored.bridgelauncher.utils.serialization.StringEnumWriteOnlySerializer
import kotlinx.serialization.Serializable

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = StringEnumWriteOnlySerializer::class)
enum class ScreenOrientationStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    Unspecified("unspecified"),
    Portrait("portrait"),
    ;

    companion object
    {
        fun fromLockHomeScreenToPortrait(it: Boolean) = when (it)
        {
            true -> Portrait
            false -> Unspecified
        }

        fun lockHomeScreenToPortraitOrThrow(orientation: String): Boolean
        {
            return when (orientation)
            {
                Portrait.rawValue -> true
                Unspecified.rawValue -> false
                else -> throw Exception("Argument \"orientation\" must be either ${q(Portrait)} or ${q(Unspecified)} (got ${q(orientation)}).")
            }
        }
    }
}
