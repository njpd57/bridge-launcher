package com.tored.bridgelauncher.api2.server

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.BridgeLauncherApplication
import com.tored.bridgelauncher.api2.server.endpoints.AppIconsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.AppShortcutIconsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.AppShortcutsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.AppUsageEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.AppsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.BridgeFileServer
import com.tored.bridgelauncher.api2.server.endpoints.CalendarEventsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.ContactPhotosEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.ProfileAppIconsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.ProfileAppsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.ContactsEndpoint
import com.tored.bridgelauncher.services.contacts.ContactsHolder
import com.tored.bridgelauncher.services.apps.ProfileAppsHolder
import com.tored.bridgelauncher.api2.server.endpoints.IconPackContentEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.IconPacksEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.MediaArtEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.NotificationIconsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.NotificationsEndpoint
import com.tored.bridgelauncher.services.apps.InstalledAppsHolder
import com.tored.bridgelauncher.services.apps.SerializableInstalledApp
import com.tored.bridgelauncher.services.iconpackcache.InstalledIconPacksHolder
import com.tored.bridgelauncher.services.media.MediaSessionsHolder
import com.tored.bridgelauncher.services.notifications.NotificationsHolder
import com.tored.bridgelauncher.services.shortcuts.AppShortcutsHolder
import com.tored.bridgelauncher.services.calendar.CalendarHolder
import com.tored.bridgelauncher.services.usage.UsageStatsHolder
import com.tored.bridgelauncher.services.settings2.BridgeSetting
import com.tored.bridgelauncher.services.settings2.BridgeSettings
import com.tored.bridgelauncher.services.settings2.settingsDataStore
import com.tored.bridgelauncher.services.settings2.useBridgeSettingStateFlow
import com.tored.bridgelauncher.utils.URLWithQueryBuilder
import com.tored.bridgelauncher.utils.q
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import kotlinx.serialization.Serializable

private const val TAG = "ReqHandler"

fun getBridgeApiEndpointURL(endpoint: String, vararg queryParams: Pair<String, Any?>): String
{
    return URLWithQueryBuilder("https://${BridgeServer.HOST}/${BridgeServer.API_PATH_ROOT}/$endpoint")
        .addParams(queryParams.asIterable())
        .build()
}

@Serializable
data class BridgeAPIEndpointAppsResponse(
    val apps: List<SerializableInstalledApp>,
)

class BridgeServer(
    private val _app: BridgeLauncherApplication,
    private val _apps: InstalledAppsHolder,
    private val _iconPacks: InstalledIconPacksHolder,
    private val _notifications: NotificationsHolder,
    private val _media: MediaSessionsHolder,
    private val _shortcuts: AppShortcutsHolder,
    private val _calendar: CalendarHolder,
    private val _usage: UsageStatsHolder,
    private val _contacts: ContactsHolder,
    private val _profileApps: ProfileAppsHolder,
)
{
    private val _scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()

    // SETTINGS
    private fun <TPreference, TResult> s(setting: BridgeSetting<TPreference, TResult>) = useBridgeSettingStateFlow(_app.settingsDataStore, _scope, setting)
    private val _currentProjDir = s(BridgeSettings.currentProjDir)

    val isReadyToServe = _currentProjDir.map { it != null }

    private val _fileServer = BridgeFileServer(
        _currentProjDir = _currentProjDir,
    )

    private val _endpoints = mapOf(
        ENDPOINT_APPS to AppsEndpoint(_apps),
        ENDPOINT_APP_ICONS to AppIconsEndpoint(_apps, _iconPacks),
        ENDPOINT_ICON_PACKS to IconPacksEndpoint(_iconPacks),
        ENDPOINT_ICON_PACK_CONTENT to IconPackContentEndpoint(_iconPacks),
        ENDPOINT_NOTIFICATIONS to NotificationsEndpoint(_notifications),
        ENDPOINT_NOTIFICATION_ICONS to NotificationIconsEndpoint(_app, _notifications),
        ENDPOINT_MEDIA_ART to MediaArtEndpoint(_media),
        ENDPOINT_APP_SHORTCUTS to AppShortcutsEndpoint(_shortcuts),
        ENDPOINT_APP_SHORTCUT_ICONS to AppShortcutIconsEndpoint(_shortcuts),
        ENDPOINT_CALENDAR_EVENTS to CalendarEventsEndpoint(_calendar),
        ENDPOINT_APP_USAGE to AppUsageEndpoint(_usage),
        ENDPOINT_CONTACTS to ContactsEndpoint(_contacts),
        ENDPOINT_CONTACT_PHOTOS to ContactPhotosEndpoint(_contacts),
        ENDPOINT_PROFILE_APPS to ProfileAppsEndpoint(_profileApps),
        ENDPOINT_PROFILE_APP_ICONS to ProfileAppIconsEndpoint(_profileApps),
    )

    suspend fun handle(req: WebResourceRequest): WebResourceResponse?
    {
        val host = req.url.host?.lowercase()

        if (host != HOST)
            return null

        Log.i(TAG, "received request to ${req.url}")

        try
        {
            val path = req.url.path
            val apiPrefix = "/$API_PATH_ROOT/"

            return if (path != null && path.startsWith(apiPrefix))
            {
                val endpointStr = path.substring(apiPrefix.length)
                val endpoint = _endpoints[endpointStr]

                endpoint?.handle(req)
                    ?: errorResponse(HTTPStatusCode.BadRequest, "There is no API endpoint at ${q(endpointStr)}.")
            }
            else
            {
                _fileServer.handle(req)
            }
        }
        catch (ex: HttpResponseException)
        {
            return errorResponse(ex.respStatusCode, ex.respMessage)
        }
        catch (ex: Exception)
        {
            Log.e(TAG, "Unexpected error:", ex)
            return errorResponse(HTTPStatusCode.InternalServerError, "Unexpected error: $ex")
        }
    }

    companion object
    {
        const val HOST = "bridge.launcher"
        const val PROJECT_URL = "https://$HOST/"
        const val API_PATH_ROOT = ":"

        const val ENDPOINT_ICON_PACK_CONTENT = "iconpacks/content"
        const val ENDPOINT_APPS = "apps"
        const val ENDPOINT_APP_ICONS = "appicons"
        const val ENDPOINT_ICON_PACKS = "iconpacks"
        const val ENDPOINT_NOTIFICATIONS = "notifications"
        const val ENDPOINT_NOTIFICATION_ICONS = "notificationicons"
        const val ENDPOINT_MEDIA_ART = "mediaart"
        const val ENDPOINT_APP_SHORTCUTS = "appshortcuts"
        const val ENDPOINT_APP_SHORTCUT_ICONS = "appshortcuticons"
        const val ENDPOINT_CALENDAR_EVENTS = "calendarevents"
        const val ENDPOINT_APP_USAGE = "appusage"
        const val ENDPOINT_CONTACTS = "contacts"
        const val ENDPOINT_CONTACT_PHOTOS = "contactphotos"
        const val ENDPOINT_PROFILE_APPS = "profileapps"
        const val ENDPOINT_PROFILE_APP_ICONS = "profileappicons"
    }
}