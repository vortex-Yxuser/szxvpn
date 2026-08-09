package com.example.sshpayloadvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SshVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.sshpayloadvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.sshpayloadvpn.DISCONNECT"

        const val EXTRA_SSH_HOST = "ssh_host"
        const val EXTRA_SSH_PORT = "ssh_port"
        const val EXTRA_SSH_USER = "ssh_user"
        const val EXTRA_SSH_PASS = "ssh_pass"
        const val EXTRA_PROXY_HOST = "proxy_host"
        const val EXTRA_PROXY_PORT = "proxy_port"
        const val EXTRA_PAYLOAD = "payload"

        private const val CHANNEL_ID = "ssh_vpn"
        private const val NOTIFICATION_ID = 1001
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var session: Session? = null
    private var vpnInterface: android.os.ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        AppLog.i("VPN service created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnect()
            ACTION_CONNECT -> connectFromIntent(intent)
        }
        return START_NOT_STICKY
    }

    private fun connectFromIntent(intent: Intent) {
        if (!running.compareAndSet(false, true)) {
            AppLog.i("Already connecting/connected")
            return
        }

        startForeground(
            NOTIFICATION_ID,
            notification("Connecting...")
        )

        val sshHost = intent.getStringExtra(EXTRA_SSH_HOST).orEmpty()
        val sshPort = intent.getIntExtra(EXTRA_SSH_PORT, 22)
        val sshUser = intent.getStringExtra(EXTRA_SSH_USER).orEmpty()
        val sshPass = intent.getStringExtra(EXTRA_SSH_PASS).orEmpty()
        val proxyHost = intent.getStringExtra(EXTRA_PROXY_HOST).orEmpty()
        val proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, 443)
        val payload = intent.getStringExtra(EXTRA_PAYLOAD).orEmpty()

        executor.execute {
            try {
                if (sshHost.isBlank() || sshUser.isBlank()) {
                    throw IllegalArgumentException("SSH host and username are required")
                }

                AppLog.i("Starting SSH connection")
                AppLog.i("Target: $sshHost:$sshPort")

                val jsch = JSch()
                val s = jsch.getSession(sshUser, sshHost, sshPort)
                s.setPassword(sshPass)
                s.setConfig("StrictHostKeyChecking", "no")
                s.timeout = 30_000

                if (proxyHost.isNotBlank()) {
                    AppLog.i("HTTP proxy enabled: $proxyHost:$proxyPort")
                    s.socketFactory = ProxySocketFactory(
                        proxyHost = proxyHost,
                        proxyPort = proxyPort,
                        payload = payload
                    )
                } else {
                    AppLog.i("No proxy: direct SSH")
                }

                session = s
                AppLog.i("Authenticating SSH...")
                s.connect(30_000)
                AppLog.i("SSH authentication successful")

                // Establish an Android VPN interface so the app can later be
                // extended with a TUN-to-SSH forwarding engine.
                vpnInterface = Builder()
                    .setSession("SSH Payload VPN")
                    .addAddress("10.8.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .setBlocking(false)
                    .establish()

                if (vpnInterface == null) {
                    throw IllegalStateException("Unable to establish Android VPN interface")
                }

                AppLog.i("VPN interface established")
                AppLog.i("CONNECTED")
            } catch (t: Throwable) {
                AppLog.e("Connection failed", t)
                disconnect()
            }
        }
    }

    private fun disconnect() {
        executor.execute {
            try {
                AppLog.i("Disconnect requested")
                session?.disconnect()
            } catch (t: Throwable) {
                AppLog.e("SSH disconnect error", t)
            } finally {
                session = null
                try {
                    vpnInterface?.close()
                } catch (_: Throwable) {
                }
                vpnInterface = null
                running.set(false)
                AppLog.i("DISCONNECTED")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        try {
            session?.disconnect()
            vpnInterface?.close()
        } catch (_: Throwable) {
        }
        session = null
        vpnInterface = null
        running.set(false)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? =
        super.onBind(intent)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SSH Payload VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Payload VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
}
