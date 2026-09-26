package com.tored.bridgelauncher.services.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
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
    /** A media player's notification (it carries a media session), which projects may show as a player instead. */
    val isMedia: Boolean,
    val hasLargeIcon: Boolean,
    /** The notification's buttons ("Reply", "Mark as read"…), run with requestNotificationAction / requestReplyToNotification. */
    val actions: List<SerializableNotificationAction>,
)

@Serializable
data class SerializableNotificationAction(
    /** Position in the notification's actions; what the request methods take. */
    val index: Int,
    val title: String,
    /** Whether it takes typed text (a "Reply" action), sent with requestReplyToNotification. */
    val acceptsText: Boolean,
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
        isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
        hasLargeIcon = notification.getLargeIcon() != null,
        actions = notification.actions.orEmpty()
            .mapIndexed { i, action ->
                SerializableNotificationAction(
                    index = i,
                    title = action.title?.toString().orEmpty(),
                    acceptsText = action.remoteInputs?.any { it.allowFreeFormInput } == true,
                )
            }
            // smart replies and other system-suggested actions aren't the app's own buttons
            .filterIndexed { i, _ -> !isContextualAction(notification.actions[i]) },
    )
}

private fun isContextualAction(action: Notification.Action) =
    CurrentAndroidVersion.supportsContextualNotificationActions() && action.isContextual
