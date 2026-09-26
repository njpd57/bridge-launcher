package com.tored.bridgelauncher.services.alarm

import kotlinx.serialization.Serializable

@Serializable
data class SerializableNextAlarm(
    /** When the alarm goes off, in milliseconds since the epoch. */
    val triggerTime: Long,
    /** The app that set it (the clock app, usually), or null if Android doesn't say. */
    val packageName: String?,
)
