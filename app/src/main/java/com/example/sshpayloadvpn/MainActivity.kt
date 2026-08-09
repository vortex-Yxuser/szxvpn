package com.example.sshpayloadvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var logView: TextView

    private val logListener: (String) -> Unit = { line ->
        logView.append(line + "\n")
        status.text = line
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        logView = findViewById(R.id.log)

        val connect = findViewById<Button>(R.id.connect)
        val disconnect = findViewById<Button>(R.id.disconnect)

        connect.setOnClickListener {
            requestVpnAndConnect()
        }

        disconnect.setOnClickListener {
            startService(
                Intent(this, SshVpnService::class.java)
                    .setAction(SshVpnService.ACTION_DISCONNECT)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        AppLog.addListener(logListener)
    }

    override fun onStop() {
        AppLog.removeListener(logListener)
        super.onStop()
    }

    private fun requestVpnAndConnect() {
        val prepare = VpnService.prepare(this)

        if (prepare != null) {
            startActivityForResult(prepare, 100)
        } else {
            startVpnService()
        }
    }

    @Deprecated("Use Activity Result API in a future UI refactor")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else if (requestCode == 100) {
            AppLog.e("VPN permission was not granted")
        }
    }

    private fun startVpnService() {
        fun text(id: Int): String =
            findViewById<EditText>(id).text.toString().trim()

        val intent = Intent(this, SshVpnService::class.java)
            .setAction(SshVpnService.ACTION_CONNECT)
            .putExtra(SshVpnService.EXTRA_SSH_HOST, text(R.id.sshHost))
            .putExtra(SshVpnService.EXTRA_SSH_PORT, text(R.id.sshPort).toIntOrNull() ?: 22)
            .putExtra(SshVpnService.EXTRA_SSH_USER, text(R.id.sshUser))
            .putExtra(SshVpnService.EXTRA_SSH_PASS, text(R.id.sshPass))
            .putExtra(SshVpnService.EXTRA_PROXY_HOST, text(R.id.proxyHost))
            .putExtra(SshVpnService.EXTRA_PROXY_PORT, text(R.id.proxyPort).toIntOrNull() ?: 443)
            .putExtra(SshVpnService.EXTRA_PAYLOAD, text(R.id.payload))

        if (Build.VERSION.SDK_INT >= 26) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}
