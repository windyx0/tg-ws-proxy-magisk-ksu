package com.windyx0.tgbypass.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.windyx0.tgbypass.MainActivity
import com.windyx0.tgbypass.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProxyService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "TgBypassProxyService"
        const val NOTIFICATION_ID = 1444
        const val CHANNEL_ID = "tgbypass_service_silent"

        const val ACTION_START = "com.windyx0.tgbypass.START"
        const val ACTION_STOP = "com.windyx0.tgbypass.STOP"
        const val LEGACY_ACTION_START = "com.amurcanov.tgwsproxy.START"
        const val LEGACY_ACTION_STOP = "com.amurcanov.tgwsproxy.STOP"

        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_IPS = "EXTRA_IPS"
        const val EXTRA_SECRET = "EXTRA_SECRET"
        const val EXTRA_POOL_SIZE = "EXTRA_POOL_SIZE"

        const val DEFAULT_PORT = 1444
        const val DEFAULT_SECRET = "7ee8902211954d7e8235f108a6313d42"
        const val DEFAULT_CF_DOMAINS = "pclead.co.uk|offshor.co.uk|cakeisalie.co.uk|noskomnadzor.co.uk|lovetrue.co.uk|sorokdva.co.uk|pyatdesyatdva.co.uk|kartoshka.co.uk|sorokodin.co.uk|pyatdesyatodin.co.uk|notelega.co.uk|ebally.co.uk|nebally.co.uk|havegreatday.co.uk|pomogite.co.uk|fixtelega.co.uk|sadnews.co.uk|onedaychamp.co.uk|stopblocking.co.uk|nothingthere.co.uk"
        const val DEFAULT_DC_IPS = "1:149.154.175.50,2:149.154.167.51,4:149.154.167.91,203:91.105.192.100,@connection_mode=cf_first,@cfproxy=1,@cfproxy_priority=1,@cfproxy_only=0,@route_order=cf_proxy_ws|direct_ws|tcp_fallback,@route_cf_proxy_ws=1,@route_direct_ws=1,@route_tcp_fallback=1,@preferred_route=cf_proxy_ws,@route_fallback=1"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        fun extractRawSecret(raw: String?): String {
            if (raw.isNullOrBlank()) return DEFAULT_SECRET
            val clean = raw.trim().lowercase(java.util.Locale.US)
            if (clean.length == 32 && clean.all { it in "0123456789abcdef" }) {
                return clean
            }
            if ((clean.startsWith("dd") || clean.startsWith("ee")) && clean.length >= 34) {
                val candidate = clean.substring(2, 34)
                if (candidate.all { it in "0123456789abcdef" }) {
                    return candidate
                }
            }
            return DEFAULT_SECRET
        }

        fun start(context: Context, port: Int = DEFAULT_PORT) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_SECRET, DEFAULT_SECRET)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START, LEGACY_ACTION_START -> {
                val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
                val secret = extractRawSecret(intent?.getStringExtra(EXTRA_SECRET))
                var ips = intent?.getStringExtra(EXTRA_IPS) ?: ""
                if (ips.isBlank()) {
                    ips = "$DEFAULT_DC_IPS,@cf_cached_domains=$DEFAULT_CF_DOMAINS"
                }
                val poolSize = intent?.getIntExtra(EXTRA_POOL_SIZE, 8) ?: 8

                startForegroundSafely(port)

                serviceScope.launch {
                    try {
                        Log.i(TAG, "Starting native MTProto proxy on 127.0.0.1:$port...")
                        val res = NativeProxy.startMtProtoProxy(
                            host = "127.0.0.1",
                            port = port,
                            secret = secret,
                            runtimeConfig = ips,
                            verbose = 0
                        )
                        if (res == 0) {
                            Log.i(TAG, "Native MTProto proxy started successfully.")
                            _isRunning.value = true
                        } else {
                            Log.e(TAG, "Native proxy returned non-zero code: $res")
                            // Check if port is already listening anyway
                            _isRunning.value = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting native proxy", e)
                    }
                }
            }

            ACTION_STOP, LEGACY_ACTION_STOP -> {
                serviceScope.launch {
                    try {
                        Log.i(TAG, "Stopping native MTProto proxy...")
                        NativeProxy.stopMtProtoProxy()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping native proxy", e)
                    } finally {
                        _isRunning.value = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundSafely(port: Int) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TgBypass Proxy Daemon")
            .setContentText("127.0.0.1:$port (Silent Mode)")
            .setSmallIcon(R.drawable.ic_notification_small_v2)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Throwable) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TgBypass Daemon",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent background WebSocket proxy service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TgBypass:ProxyWakeLock").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours timeout
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not acquire wake lock: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Throwable) {}
        serviceScope.launch {
            try {
                NativeProxy.stopMtProtoProxy()
            } catch (_: Throwable) {}
            _isRunning.value = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
