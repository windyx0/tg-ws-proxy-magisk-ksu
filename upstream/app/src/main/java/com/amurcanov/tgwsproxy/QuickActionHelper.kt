package com.amurcanov.tgwsproxy

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object QuickActionHelper {
    private const val PREFS = PROXY_PREFS_NAME
    private const val KEY_TARGET_FORK = "target_telegram_app"
    private const val DEFAULT_PORT = 1443
    private const val DEFAULT_SECRET = "ee111111111111111111111111111111117777772e636c6f7564666c6172652e636f6d"

    fun executeQuickAction(
        context: Context,
        scope: CoroutineScope,
        onProgress: (String) -> Unit = {},
        onFinished: (Boolean) -> Unit = {},
    ) {
        scope.launch {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val port = prefs.getInt("proxy_port", DEFAULT_PORT)
            val secret = prefs.getString("mtproto_secret", DEFAULT_SECRET) ?: DEFAULT_SECRET
            val targetFork = prefs.getString(KEY_TARGET_FORK, "auto") ?: "auto"

            onProgress("Проверка подключения прокси...")

            // Check if listening
            var isRunning = ProxyService.isRunning.value || RootModuleBridge.isLocalPortListening(port)

            if (!isRunning) {
                onProgress("Прокси отключен. Запуск службы...")
                val rootAvail = RootModuleBridge.isRootAvailable()

                if (rootAvail && RootModuleBridge.isMagiskModuleInstalled()) {
                    RootModuleBridge.startProxyViaRoot()
                } else {
                    // Start via standard Android Service
                    val startIntent = Intent(context, ProxyService::class.java).apply {
                        action = ProxyService.ACTION_START
                        putExtra(ProxyService.EXTRA_PORT, port)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(startIntent)
                    } else {
                        context.startService(startIntent)
                    }
                }

                // Wait up to 3 seconds for port to open
                var attempts = 0
                while (attempts < 6 && !isRunning) {
                    delay(500)
                    isRunning = ProxyService.isRunning.value || RootModuleBridge.isLocalPortListening(port)
                    attempts++
                }
            }

            // Construct proxy URL
            val proxyUrl = "tg://resolve?domain=proxy&server=127.0.0.1&port=$port&secret=$secret"
            onProgress("Применение прокси в Telegram...")

            val success = withContext(Dispatchers.Main) {
                TelegramForkManager.applyProxyToTelegram(context, proxyUrl, targetFork)
            }

            if (success) {
                onProgress("✓ Прокси успешно подключен и применен!")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Прокси применен в Telegram!", Toast.LENGTH_SHORT).show()
                }
            } else {
                onProgress("! Не удалось открыть Telegram")
            }

            onFinished(success)
        }
    }
}
