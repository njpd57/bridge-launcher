package com.tored.bridgelauncher.services.storage

import android.os.Environment
import android.os.StatFs
import kotlinx.serialization.Serializable

@Serializable
data class SerializableStorageStats(
    /** Bytes of internal (data partition) storage. */
    val totalBytes: Long,
    /** Bytes available to apps. */
    val freeBytes: Long,
)
{
    companion object
    {
        fun read(): SerializableStorageStats
        {
            val stat = StatFs(Environment.getDataDirectory().path)
            return SerializableStorageStats(
                totalBytes = stat.totalBytes,
                freeBytes = stat.availableBytes,
            )
        }
    }
}
