package com.example.sshpayloadvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var logView: TextView

    companion object {
        private const val VPN_REQUEST = 100
    }

    private val logListener: (String) -> Unit = { line ->
        runOnUiThread {
            logView.append(line + "\n")
            status.text = line
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        logView = findViewById(R.id.log)

        findViewById<Button>(R.id.connect).setOnClickListener {
            requestVpn()
        }

        findViewById<Button>(R.id.disconnect).setOnClickListener {
            disconnect()
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

    private fun requestVpn() {

        val prepare = VpnService.prepare(this)

        if (prepare != null) {
            startActivityForResult(
                prepare,
                VPN_REQUEST
            )
        } else {
            startVpn()
        }
    }

    @Deprecated("Legacy Activity Result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == VPN_REQUEST) {

            if (resultCode == Activity.RESULT_OK) {
                startVpn()
            } else {
                AppLog.e("VPN permission denied")
            }
        }
    }

    private fun startVpn() {

        val vlessUrl =
            findViewById<EditText>(R.id.vlessUrl)
                .text
                .toString()
                .trim()

        if (vlessUrl.isBlank()) {
            AppLog.e("VLESS URL is empty")
            return
        }

        val proxyEnabled =
            findViewById<CheckBox>(R.id.proxyEnabled).isChecked

        val proxyType =
            findViewById<Spinner>(R.id.proxyType)
                .selectedItem
                .toString()

        val proxyHost =
            findViewById<EditText>(R.id.proxyHost)
                .text
                .toString()
                .trim()

        val proxyPort =
            findViewById<EditText>(R.id.proxyPort)
                .text
                .toString()
                .toIntOrNull()
                ?: 8080

        val proxyUser =
            findViewById<EditText>(R.id.proxyUser)
                .text
                .toString()

        val proxyPassword =
            findViewById<EditText>(R.id.proxyPassword)
                .text
                .toString()

        val intent =
            Intent(this, VlessVpnService::class.java)
                .setAction(VlessVpnService.ACTION_CONNECT)
                .putExtra(
                    VlessVpnService.EXTRA_VLESS_URL,
                    vlessUrl
                )
                .putExtra(
                    VlessVpnService.EXTRA_PROXY_ENABLED,
                    proxyEnabled
                )
                .putExtra(
                    VlessVpnService.EXTRA_PROXY_TYPE,
                    proxyType
                )
                .putExtra(
                    VlessVpnService.EXTRA_PROXY_HOST,
                    proxyHost
                )
                .putExtra(
                    VlessVpnService.EXTRA_PROXY_PORT,
                    proxyPort
                )
                .putExtra(
                    VlessVpnService.EXTRA_PROXY_USER,
                    proxyUser
                )
                .putExtra(
                    VlessVpnService.EXTRA_PROXY_PASSWORD,
                    proxyPassword
                )

        if (Build.VERSION.SDK_INT >= 26) {
            ContextCompat.startForegroundService(
                this,
                intent
            )
        } else {
            startService(intent)
        }
    }

    private fun disconnect() {

        val intent =
            Intent(
                this,
                VlessVpnService::class.java
            ).setAction(
                VlessVpnService.ACTION_DISCONNECT
            )

        startService(intent)
    }
}
