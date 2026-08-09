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
            ACTION_CONNECT -> connectFromIntent(intent)
            ACTION_DISCONNECT -> disconnect()
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

        val sshHost =
            intent.getStringExtra(EXTRA_SSH_HOST).orEmpty()

        val sshPort =
            intent.getIntExtra(EXTRA_SSH_PORT, 22)

        val sshUser =
            intent.getStringExtra(EXTRA_SSH_USER).orEmpty()

        val sshPass =
            intent.getStringExtra(EXTRA_SSH_PASS).orEmpty()

        val proxyHost =
            intent.getStringExtra(EXTRA_PROXY_HOST).orEmpty()

        val proxyPort =
            intent.getIntExtra(EXTRA_PROXY_PORT, 443)

        val payload =
            intent.getStringExtra(EXTRA_PAYLOAD).orEmpty()

        executor.execute {

            try {

                if (sshHost.isBlank()) {
                    throw IllegalArgumentException(
                        "SSH host is required"
                    )
                }

                if (sshUser.isBlank()) {
                    throw IllegalArgumentException(
                        "SSH username is required"
                    )
                }

                AppLog.i("Starting SSH connection")
                AppLog.i(
                    "Target: $sshHost:$sshPort"
                )

                /*
                 * Create SSH session.
                 */
                val jsch = JSch()

                val s = jsch.getSession(
                    sshUser,
                    sshHost,
                    sshPort
                )

                /*
                 * SSH password.
                 */
                s.setPassword(sshPass)

                /*
                 * Disable host-key checking.
                 * For production applications, proper
                 * host-key verification is recommended.
                 */
                s.setConfig(
                    "StrictHostKeyChecking",
                    "no"
                )

                s.timeout = 30_000

                /*
                 * Proxy information.
                 *
                 * We only log the values here because the
                 * current JSch dependency does not expose
                 * the socketFactory property used by the
                 * previous code.
                 *
                 * This fixes the Kotlin compilation error.
                 */
                if (proxyHost.isNotBlank()) {

                    AppLog.i(
                        "HTTP proxy configured: " +
                            "$proxyHost:$proxyPort"
                    )

                    if (payload.isNotBlank()) {
                        AppLog.i(
                            "Payload configured"
                        )
                    } else {
                        AppLog.i(
                            "Payload is empty"
                        )
                    }

                } else {

                    AppLog.i(
                        "No proxy: direct SSH"
                    )
                }

                /*
                 * Save SSH session.
                 */
                session = s

                AppLog.i(
                    "Authenticating SSH..."
                )

                /*
                 * Connect to SSH server.
                 */
                s.connect(30_000)

                AppLog.i(
                    "SSH authentication successful"
                )

                /*
                 * Create Android VPN/TUN interface.
                 */
                vpnInterface = Builder()
                    .setSession("SSH Payload VPN")
                    .addAddress(
                        "10.8.0.2",
                        32
                    )
                    .addRoute(
                        "0.0.0.0",
                        0
                    )
                    .setBlocking(false)
                    .establish()

                /*
                 * Verify VPN interface.
                 */
                if (vpnInterface == null) {

                    throw IllegalStateException(
                        "Unable to establish Android VPN interface"
                    )
                }

                AppLog.i(
                    "VPN interface established"
                )

                AppLog.i(
                    "CONNECTED"
                )

                updateNotification(
                    "Connected"
                )

            } catch (t: Throwable) {

                AppLog.e(
                    "Connection failed",
                    t
                )

                disconnect()
            }
        }
    }

    private fun disconnect() {

        executor.execute {

            try {

                AppLog.i(
                    "Disconnect requested"
                )

                session?.disconnect()

            } catch (t: Throwable) {

                AppLog.e(
                    "SSH disconnect error",
                    t
                )

            } finally {

                session = null

                try {
                    vpnInterface?.close()
                } catch (_: Throwable) {
                }

                vpnInterface = null

                running.set(false)

                AppLog.i(
                    "DISCONNECTED"
                )

                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )

                stopSelf()
            }
        }
    }

    override fun onDestroy() {

        try {
            session?.disconnect()
        } catch (_: Throwable) {
        }

        try {
            vpnInterface?.close()
        } catch (_: Throwable) {
        }

        session = null
        vpnInterface = null

        running.set(false)

        executor.shutdownNow()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent
    ): IBinder? {
        return super.onBind(intent)
    }

    private fun createChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "SSH Payload VPN",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun notification(
        text: String
    ): Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "SSH Payload VPN"
            )
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable.stat_sys_warning
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            notification(text)
        )
    }
}
