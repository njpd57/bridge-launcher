package com.tored.bridgelauncher.api2.server.endpoints

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.HTTPStatusCode
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.notFound
import com.tored.bridgelauncher.services.media.MediaSessionsHolder
import com.tored.bridgelauncher.utils.EncodingStrings
import java.io.ByteArrayOutputStream

// album art can be large; widgets don't need more
private const val MAX_ART_SIZE_PX = 512

/** The current track's art. The `v` query parameter (the art version) only busts the WebView's cache. */
class MediaArtEndpoint(
    private val _media: MediaSessionsHolder,
) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        val art = _media.art ?: throw notFound("The current media session has no art.")

        val scale = minOf(1f, MAX_ART_SIZE_PX.toFloat() / maxOf(art.width, art.height))
        val bmp = if (scale < 1f)
            Bitmap.createScaledBitmap(art, (art.width * scale).toInt().coerceAtLeast(1), (art.height * scale).toInt().coerceAtLeast(1), true)
        else
            art

        // the WebView is responsible for closing this stream
        val readStream = ByteArrayOutputStream().use { writeStream ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, writeStream)
            writeStream.toByteArray().inputStream()
        }

        return WebResourceResponse(
            "image/jpeg",
            EncodingStrings.UTF8,
            HTTPStatusCode.OK.rawValue,
            HTTPStatusCode.OK.name,
            null,
            readStream,
        )
    }

    companion object
    {
        const val QUERY_VERSION = "v"
    }
}
