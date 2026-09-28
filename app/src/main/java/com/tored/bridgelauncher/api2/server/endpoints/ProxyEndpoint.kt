package com.tored.bridgelauncher.api2.server.endpoints

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.HTTPStatusCode
import com.tored.bridgelauncher.api2.server.HttpResponseException
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.badRequest
import com.tored.bridgelauncher.api2.server.stringQueryParamOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

private const val TAG = "ProxyEndpoint"

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000
private const val MAX_REDIRECTS = 5
private const val MAX_BODY_BYTES = 5 * 1024 * 1024

// some sites refuse requests without a browser-like user agent
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) BridgeLauncher"

/**
 * Downloads an http(s) URL from Bridge and returns it as is, so the project can read sites that don't allow CORS
 * (RSS feeds, for example). GET only. The upstream status code and content type are kept; a failed download is a 502,
 * a timeout a 504.
 */
class ProxyEndpoint : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        val url = req.url.stringQueryParamOrNull(QUERY_URL)
            ?: throw badRequest("The url query parameter is required.")

        return withContext(Dispatchers.IO) { download(url) }
    }

    private fun download(urlStr: String): WebResourceResponse
    {
        var url = parseHttpUrl(urlStr)

        // HttpURLConnection doesn't follow redirects between http and https, so they are followed here
        repeat(MAX_REDIRECTS + 1) {
            val conn = url.openConnection() as HttpURLConnection
            try
            {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.setRequestProperty("User-Agent", USER_AGENT)

                val code = conn.responseCode
                if (code in 300..399)
                {
                    val location = conn.getHeaderField("Location")
                        ?: throw HttpResponseException(HTTPStatusCode.BadGateway, "Redirect without a Location header.")
                    url = parseHttpUrl(URL(url, location).toString())
                    return@repeat
                }

                val body = readCapped(if (code >= 400) conn.errorStream else conn.inputStream)
                val (mimeType, charset) = parseContentType(conn.contentType)

                return WebResourceResponse(
                    mimeType,
                    charset,
                    code,
                    conn.responseMessage?.takeIf { it.isNotBlank() } ?: "Status $code",
                    mapOf("Cache-Control" to "no-store"),
                    body.inputStream(),
                )
            }
            catch (ex: SocketTimeoutException)
            {
                throw HttpResponseException(HTTPStatusCode.GatewayTimeout, "Timed out downloading $url.")
            }
            catch (ex: IOException)
            {
                Log.w(TAG, "Failed to download $url", ex)
                throw HttpResponseException(HTTPStatusCode.BadGateway, "Failed to download $url: $ex")
            }
            finally
            {
                conn.disconnect()
            }
        }

        throw HttpResponseException(HTTPStatusCode.BadGateway, "Too many redirects.")
    }

    private fun parseHttpUrl(urlStr: String): URL
    {
        val url = try
        {
            URL(urlStr)
        }
        catch (ex: Exception)
        {
            throw badRequest("Not a valid URL: $urlStr")
        }

        if (url.protocol != "http" && url.protocol != "https")
            throw badRequest("Only http and https URLs can be fetched.")

        return url
    }

    private fun readCapped(stream: java.io.InputStream?): ByteArray
    {
        if (stream == null)
            return ByteArray(0)

        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true)
            {
                val read = input.read(buffer)
                if (read < 0)
                    break
                if (out.size() + read > MAX_BODY_BYTES)
                    throw HttpResponseException(HTTPStatusCode.BadGateway, "The response is larger than $MAX_BODY_BYTES bytes.")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    /** `text/xml; charset=ISO-8859-1` -> (`text/xml`, `ISO-8859-1`). The charset is null when the server doesn't give one. */
    private fun parseContentType(contentType: String?): Pair<String, String?>
    {
        val parts = contentType?.split(';')?.map { it.trim() } ?: emptyList()
        val mimeType = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "application/octet-stream"
        val charset = parts.drop(1)
            .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim('"', ' ')
        return mimeType to charset
    }

    companion object
    {
        const val QUERY_URL = "url"
    }
}
