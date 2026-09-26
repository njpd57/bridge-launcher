package com.tored.bridgelauncher.services.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.serialization.Serializable

@Serializable
data class SerializableNotification(
    val key: String,
    val packageName: String,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val subText: String?,
    val category: String?,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val isGroupSummary: Boolean,
    val hasLargeIcon: Boolean,
)

fun StatusBarNotification.toSerializable(): SerializableNotification
{
    val extras = notification.extras
    return SerializableNotification(
        key = key,
        packageName = packageName,
        postTime = postTime,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
        subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        category = notification.category,
        isOngoing = isOngoing,
        isClearable = isClearable,
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        hasLargeIcon = notification.getLargeIcon() != null,
    )
}
