package com.amurcanov.tgwsproxy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

object SilentServicePolicy {
    private const val TAG = "TgWsProxy.SilentPolicy"
    const val PREF_SILENT_MODULE_MODE = "silent_module_mode"

    fun configureSilentNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            
            // Channel for status
            val channelStatus = NotificationChannel(
                ProxyService.CHANNEL_STATUS_ID,
                "TgWsProxy Service Status (Silent)",
                NotificationManager.IMPORTANCE_MIN // Minimizes and hides notification icon from status bar
            ).apply {
                description = "TgWsProxy background runtime state"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channelStatus)

            // Channel for alerts
            val channelAlerts = NotificationChannel(
                ProxyService.CHANNEL_ALERTS_ID,
                "TgWsProxy Alerts (Silent)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channelAlerts)
        }
    }

    suspend fun enforceNotificationSuppression(context: Context) {
        // Use root privileges if available to completely revoke/ignore POST_NOTIFICATIONS
        try {
            val pkg = context.packageName
            RootModuleBridge.suppressNotificationSilently(pkg)
            Log.i(TAG, "Successfully requested root notification suppression for $pkg")
        } catch (e: Exception) {
            Log.w(TAG, "Root notification suppression failed (non-root mode): ${e.message}")
        }
    }
}
