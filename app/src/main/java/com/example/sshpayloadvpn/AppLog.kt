package com.example.sshpayloadvpn

import android.os.Handler
import android.os.Looper
import android.util.Log

object AppLog {
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(String) -> Unit>()

    fun addListener(listener: (String) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: (String) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun i(message: String) = write("INFO", message)
    fun e(message: String, error: Throwable? = null) =
        write("ERROR", message + (error?.let { " | ${it.message}" } ?: ""))

    private fun write(level: String, message: String) {
        Log.i("SshPayloadVPN", "[$level] $message")
        val line = "[$level] $message"
        handler.post {
            synchronized(listeners) { listeners.toList() }.forEach { it(line) }
        }
    }
}
