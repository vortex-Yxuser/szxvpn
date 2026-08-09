package com.example.sshpayloadvpn

import android.util.Base64
import com.jcraft.jsch.SocketFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * JSch SocketFactory.
 *
 * The socket first connects to the HTTP proxy, sends the configured
 * CONNECT payload, waits for a 200 response, and then leaves the same
 * socket open for the SSH protocol.
 */
class ProxySocketFactory(
    private val proxyHost: String?,
    private val proxyPort: Int,
    private val payload: String?,
    private val proxyUser: String? = null,
    private val proxyPass: String? = null
) : SocketFactory {

    private val connectTimeout = 20_000
    private val readTimeout = 30_000

    override fun createSocket(host: String, port: Int): Socket {
        val socket = Socket()

        if (proxyHost.isNullOrBlank()) {
            AppLog.i("DIRECT -> $host:$port")
            socket.connect(InetSocketAddress(host, port), connectTimeout)
            socket.soTimeout = readTimeout
            return socket
        }

        require(proxyPort in 1..65535) { "Invalid proxy port: $proxyPort" }

        AppLog.i("Connecting HTTP proxy $proxyHost:$proxyPort")
        socket.connect(InetSocketAddress(proxyHost, proxyPort), connectTimeout)
        socket.soTimeout = readTimeout
        AppLog.i("Proxy TCP connected")

        val request = buildPayload(host, port)
        AppLog.i("Sending HTTP payload")

        val writer = OutputStreamWriter(
            socket.getOutputStream(),
            Charsets.ISO_8859_1
        )
        writer.write(request)
        writer.flush()

        val reader = BufferedReader(
            InputStreamReader(
                socket.getInputStream(),
                Charsets.ISO_8859_1
            )
        )

        val statusLine = reader.readLine()
            ?: throw IOException("Proxy returned an empty response")

        val statusCode = Regex("""HTTP/\d(?:\.\d)?\s+(\d{3})""")
            .find(statusLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: throw IOException("Invalid HTTP response: $statusLine")

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }

        AppLog.i("Proxy response: $statusLine")

        if (statusCode != 200) {
            throw IOException("HTTP CONNECT failed: $statusLine")
        }

        AppLog.i("HTTP CONNECT 200 OK -> $host:$port")
        return socket
    }

    override fun getInputStream(socket: Socket): InputStream =
        socket.getInputStream()

    override fun getOutputStream(socket: Socket): OutputStream =
        socket.getOutputStream()

    private fun buildPayload(host: String, port: Int): String {
        var request = payload?.takeIf { it.isNotBlank() }
            ?: "CONNECT [host_port] [protocol][crlf]Host: [host][crlf][crlf]"

        request = request
            .replace("[host_port]", "$host:$port")
            .replace("[host]", host)
            .replace("[port]", port.toString())
            .replace("[protocol]", "HTTP/1.1")
            .replace("[crlf]", "\r\n")
            .replace("[lf]", "\n")
            .replace("\\r\\n", "\r\n")
            .replace("\\n", "\n")

        if (!request.endsWith("\r\n\r\n")) {
            request = request.trimEnd('\r', '\n') + "\r\n\r\n"
        }

        if (!proxyUser.isNullOrBlank()) {
            val credentials = "$proxyUser:${proxyPass ?: ""}"
            val encoded = Base64.encodeToString(
                credentials.toByteArray(Charsets.ISO_8859_1),
                Base64.NO_WRAP
            )
            val separator = "\r\n\r\n"
            val index = request.lastIndexOf(separator)
            request = if (index >= 0) {
                request.substring(0, index) +
                    "\r\nProxy-Authorization: Basic $encoded" +
                    request.substring(index)
            } else {
                request.trimEnd('\r', '\n') +
                    "\r\nProxy-Authorization: Basic $encoded\r\n\r\n"
            }
        }

        return request
    }
}
