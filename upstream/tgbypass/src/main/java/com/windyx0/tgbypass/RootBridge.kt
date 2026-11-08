package com.windyx0.tgbypass

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

enum class BypassMethod {
    AYUGRAM_PLUGIN,
    VECTOR_HOOK
}

data class TelegramClientInfo(
    val packageName: String,
    val name: String,
    val isInstalled: Boolean,
    val method: BypassMethod,
    val isConfigured: Boolean,
    val detail: String
)

data class BackendOption(
    val key: String,
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val description: String
)

data class BackendSyncInfo(
    val backend: String,
    val backendIdx: Int,
    val timestamp: Long,
    val source: String
)

data class LatencyInfo(
    val dc: Int,
    val name: String,
    val host: String,
    val port: Int,
    val latencyMs: Long?
) {
    fun getBadgeText(): String {
        return if (latencyMs != null) "$latencyMs ms" else "Таймаут"
    }

    fun getBadgeColor(): Color {
        return when {
            latencyMs == null -> Color(0xFFF97575) // Red
            latencyMs < 200 -> Color(0xFF48D597)   // Green (< 200)
            latencyMs < 500 -> Color(0xFFFFD600)   // Yellow (< 500)
            else -> Color(0xFFF97575)              // Red (>= 500)
        }
    }

    fun getContainerColor(): Color {
        return when {
            latencyMs == null -> Color(0xFF381414)
            latencyMs < 200 -> Color(0xFF143823)
            latencyMs < 500 -> Color(0xFF383314)
            else -> Color(0xFF381414)
        }
    }
}

object RootBridge {
    private const val TAG = "TgBypassRoot"
    const val DEFAULT_PORT = 1444

    // Official and ZaSto WSS Backends
    val AVAILABLE_BACKENDS = listOf(
        BackendOption("tgbypass", "TgBypass Daemon", "MTPROTO", "127.0.0.1", 1444, "Локальный WebSocket демон обхода"),
        BackendOption("zasto_wss_edge", "ZaSto WSS Cloudflare", "WSS_RELAY", "edge.amberwick.workers.dev", 443, "ZaSto Cloudflare Worker туннель"),
        BackendOption("zasto_wss_node", "ZaSto WSS Резерв", "WSS_RELAY", "nodeone.brackencombe.workers.dev", 443, "ZaSto резервный Worker туннель"),
        BackendOption("cloudflare_dc2", "Cloudflare Front DC2", "MTPROTO", "kws2.pclead.co.uk", 443, "Cloudflare Edge DC2 Амстердам")
    )

    suspend fun readAyuGramSyncState(): BackendSyncInfo? = withContext(Dispatchers.IO) {
        try {
            val out = execRoot("cat /data/data/com.radolyn.ayugram/files/plugins/tgbypass_sync.json 2>/dev/null")
            if (out.contains("\"backend\"")) {
                val json = org.json.JSONObject(out)
                val backend = json.optString("backend", "")
                val idx = json.optInt("backend_idx", 0)
                val ts = json.optLong("timestamp", 0L)
                val src = json.optString("source", "")
                if (backend.isNotEmpty()) {
                    return@withContext BackendSyncInfo(backend, idx, ts, src)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pushBackendSync(backendKey: String, backendIdx: Int, timestampMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uidGid = execRoot("stat -c %u:%g /data/data/com.radolyn.ayugram 2>/dev/null").trim().ifEmpty { "10334:10334" }
            val syncPath = "/data/data/com.radolyn.ayugram/files/plugins/tgbypass_sync.json"
            val settingsPath = "/data/data/com.radolyn.ayugram/files/plugins/plugin_settings.json"

            // 1. Write tgbypass_sync.json
            val syncJson = org.json.JSONObject().apply {
                put("backend", backendKey)
                put("backend_idx", backendIdx)
                put("timestamp", timestampMs)
                put("source", "tgbypass_app")
            }.toString()

            val escapedSync = syncJson.replace("\"", "\\\"")
            execRoot("echo \"$escapedSync\" > $syncPath && chown $uidGid $syncPath && chmod 666 $syncPath")

            // 2. Also update backend_idx in plugin_settings.json if it exists
            val currentSettings = execRoot("cat $settingsPath 2>/dev/null")
            if (currentSettings.isNotEmpty() && currentSettings.contains("{")) {
                try {
                    val json = org.json.JSONObject(currentSettings)
                    val bridgeObj = json.optJSONObject("tg_ws_vector_bridge") ?: org.json.JSONObject()
                    bridgeObj.put("backend_idx", backendIdx)
                    json.put("tg_ws_vector_bridge", bridgeObj)
                    val updatedStr = json.toString().replace("\"", "\\\"")
                    execRoot("echo \"$updatedStr\" > $settingsPath && chown $uidGid $settingsPath && chmod 666 $settingsPath")
                } catch (_: Exception) {}
            }

            // 3. Update global marker in /data/local/tmp
            execRoot("echo \"$backendKey\" > /data/local/tmp/tgbypass_active_backend.txt && chmod 644 /data/local/tmp/tgbypass_active_backend.txt")

            true
        } catch (e: Exception) {
            Log.e(TAG, "pushBackendSync error", e)
            false
        }
    }

    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val ok = out.contains("uid=0")
            Log.d(TAG, "requestRoot: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "requestRoot error", e)
            false
        }
    }

    suspend fun execRoot(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            // Use -mm (mount master) so root process can access all app directories in /data/data
            val p = try {
                Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", cmd))
            } catch (_: Exception) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            }
            val stdout = p.inputStream.bufferedReader().readText()
            p.waitFor()
            stdout.trim()
        } catch (e: Exception) {
            Log.e(TAG, "execRoot failed: $cmd", e)
            ""
        }
    }

    suspend fun isPortListening(port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 250)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isEndpointReachable(host: String, port: Int = 443, timeoutMs: Int = 600): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getVectorStatus(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val out = execRoot("/data/adb/lspd/cli status 2>/dev/null")
        val isRunning = out.contains("Framework Version:")
        val ver = if (isRunning) {
            out.lines().firstOrNull { it.contains("Framework Version:") }?.substringAfter(":")?.trim() ?: "2.2"
        } else {
            "Не активен"
        }
        Pair(isRunning, ver)
    }

    suspend fun isModuleEnabledInVector(pkg: String = "com.windyx0.tgbypass"): Boolean = withContext(Dispatchers.IO) {
        val out = execRoot("/data/adb/lspd/cli modules ls 2>/dev/null")
        out.contains(pkg) && out.contains("enabled")
    }

    suspend fun getInstalledTelegramClients(context: Context): List<TelegramClientInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TelegramClientInfo>()
        val pm = context.packageManager
        val vectorScope = execRoot("/data/adb/lspd/cli scope ls com.windyx0.tgbypass 2>/dev/null")

        // 1. AyuGram
        val ayuInstalled = try {
            pm.getPackageInfo("com.radolyn.ayugram", 0) != null
        } catch (_: Exception) {
            execRoot("pm path com.radolyn.ayugram 2>/dev/null").isNotEmpty()
        }
        if (ayuInstalled) {
            val existsOut = execRoot("test -f /data/data/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py && echo 1")
            val pluginExists = existsOut.contains("1")

            val xml = execRoot("cat /data/data/com.radolyn.ayugram/shared_prefs/plugin_settings.xml 2>/dev/null")
            val pluginEnabled = xml.contains("plugin_enabled_tg_ws_vector_bridge") && 
                    (xml.contains("\"true\"") || xml.contains(">true<") || xml.contains("true"))

            val isOk = pluginExists && pluginEnabled
            val detail = when {
                isOk -> "Мост-плагин активен (127.0.0.1:1444)"
                pluginExists -> "Плагин отключен в настройках"
                else -> "Плагин не установлен"
            }
            Log.d(TAG, "AyuGram check: exists=$pluginExists, enabled=$pluginEnabled, isOk=$isOk, xmlLen=${xml.length}")
            list.add(TelegramClientInfo(
                packageName = "com.radolyn.ayugram",
                name = "AyuGram",
                isInstalled = true,
                method = BypassMethod.AYUGRAM_PLUGIN,
                isConfigured = isOk,
                detail = detail
            ))
        }

        // 2. ZaStoGram (Official ZaSto Telegram Fork with native WSS)
        val zastoInstalled = try {
            pm.getPackageInfo("org.zastogram.messenger.dev", 0) != null
        } catch (_: Exception) {
            execRoot("pm path org.zastogram.messenger.dev 2>/dev/null").isNotEmpty()
        }
        if (zastoInstalled) {
            list.add(TelegramClientInfo(
                packageName = "org.zastogram.messenger.dev",
                name = "ZaStoGram",
                isInstalled = true,
                method = BypassMethod.VECTOR_HOOK,
                isConfigured = true,
                detail = "Встроенный WSS-транспорт активен"
            ))
        }

        // 3. Other clients (routed via Vector module hook)
        val otherClients = listOf(
            Triple("org.telegram.messenger", "Telegram (Play/Official)", BypassMethod.VECTOR_HOOK),
            Triple("org.telegram.messenger.web", "Telegram Direct / Web", BypassMethod.VECTOR_HOOK),
            Triple("app.exteragram.messenger", "exteraGram", BypassMethod.VECTOR_HOOK),
            Triple("org.thunderdog.challegram", "Telegram X", BypassMethod.VECTOR_HOOK),
            Triple("org.telegram.plus", "Plus Messenger", BypassMethod.VECTOR_HOOK),
            Triple("tw.nekomimi.nekogram", "Nekogram", BypassMethod.VECTOR_HOOK),
            Triple("cc.modery.cherrygram", "Cherrygram", BypassMethod.VECTOR_HOOK)
        )

        for ((pkg, name, method) in otherClients) {
            val installed = try {
                pm.getPackageInfo(pkg, 0) != null
            } catch (_: Exception) {
                execRoot("pm path $pkg 2>/dev/null").isNotEmpty()
            }
            if (installed) {
                val inScope = vectorScope.contains(pkg)
                list.add(TelegramClientInfo(
                    packageName = pkg,
                    name = name,
                    isInstalled = true,
                    method = method,
                    isConfigured = inScope,
                    detail = if (inScope) "Vector Hook активен" else "Не в области действия Vector"
                ))
            }
        }

        list
    }

    suspend fun autoInstallAndFixAll(context: Context): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        // 1. Install / repair AyuGram Bridge Plugin
        val ayuInstalled = execRoot("pm path com.radolyn.ayugram 2>/dev/null").isNotEmpty()
        if (ayuInstalled) {
            try {
                val pluginBytes = context.assets.open("tg_ws_vector_bridge.py").use { it.readBytes() }
                val tmpFile = java.io.File(context.cacheDir, "tg_ws_vector_bridge.py")
                tmpFile.writeBytes(pluginBytes)

                val uidGid = execRoot("stat -c %u:%g /data/data/com.radolyn.ayugram 2>/dev/null").trim().ifEmpty { "10334:10334" }
                val targetDir = "/data/data/com.radolyn.ayugram/files/plugins"
                val prefsFile = "/data/data/com.radolyn.ayugram/shared_prefs/plugin_settings.xml"

                execRoot("mkdir -p $targetDir")
                execRoot("cp ${tmpFile.absolutePath} $targetDir/tg_ws_vector_bridge.py")
                execRoot("cp ${tmpFile.absolutePath} $targetDir/.temp_tg_ws_vector_bridge.plugin")
                execRoot("chown $uidGid $targetDir/tg_ws_vector_bridge.py $targetDir/.temp_tg_ws_vector_bridge.plugin")
                execRoot("chmod 600 $targetDir/tg_ws_vector_bridge.py $targetDir/.temp_tg_ws_vector_bridge.plugin")

                // Enable plugin in plugin_settings.xml
                val xml = execRoot("cat $prefsFile 2>/dev/null")
                if (!xml.contains("plugin_enabled_tg_ws_vector_bridge")) {
                    if (xml.contains("</map>")) {
                        val updated = xml.replace("</map>", "    <boolean name=\"plugin_enabled_tg_ws_vector_bridge\" value=\"true\" />\n</map>")
                        val tmpXml = java.io.File(context.cacheDir, "plugin_settings.xml")
                        tmpXml.writeText(updated)
                        execRoot("cp ${tmpXml.absolutePath} $prefsFile")
                        execRoot("chown $uidGid $prefsFile")
                        execRoot("chmod 660 $prefsFile")
                    }
                }

                // CRITICAL: Ensure AyuGram is NOT in Vector scope to prevent ART crash
                execRoot("/data/adb/lspd/cli scope rm com.windyx0.tgbypass com.radolyn.ayugram/0 2>/dev/null")
                sb.append("AyuGram: мост-плагин установлен и активирован.\n")
            } catch (e: Exception) {
                sb.append("AyuGram: ошибка установки (${e.message})\n")
            }
        }

        // 2. Enable Vector module and add non-AyuGram clients to scope
        execRoot("/data/adb/lspd/cli modules enable com.windyx0.tgbypass 2>/dev/null")
        val otherPackages = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "app.exteragram.messenger",
            "org.thunderdog.challegram",
            "org.telegram.plus",
            "tw.nekomimi.nekogram",
            "cc.modery.cherrygram",
            "org.zastogram.messenger.dev"
        )
        var addedCount = 0
        for (pkg in otherPackages) {
            val installed = execRoot("pm path $pkg 2>/dev/null").isNotEmpty()
            if (installed) {
                execRoot("/data/adb/lspd/cli scope add com.windyx0.tgbypass $pkg/0 2>/dev/null")
                addedCount++
            }
        }
        sb.append("Vector: область действия обновлена ($addedCount приложений).\n")

        // 3. Ensure proxy daemon is alive
        if (!isPortListening()) {
            execRoot("sh /data/adb/modules/tg_ws_proxy/service.sh 2>&1 &")
            delay(1500)
            if (isPortListening()) {
                sb.append("Демон 127.0.0.1:1444 запущен.")
            } else {
                sb.append("Демон 127.0.0.1:1444 запускается...")
            }
        } else {
            sb.append("Демон прокси активен (127.0.0.1:1444).")
        }

        sb.toString().trim()
    }

    suspend fun getZapret2Status(): Triple<Boolean, String, String> = withContext(Dispatchers.IO) {
        val pid = execRoot("pgrep -f nfqws2 2>/dev/null | head -n 1").trim()
        val isRunning = pid.isNotEmpty() && pid.all { it.isDigit() }
        val msg = if (isRunning) "nfqws2 активен (PID: $pid, Очередь 200)" else "Служба остановлена"
        Triple(isRunning, pid, msg)
    }

    suspend fun restartZapret2(): Boolean = withContext(Dispatchers.IO) {
        execRoot("sh /data/adb/modules/zapret2/zapret2/scripts/zapret-restart.sh 2>&1 || sh /data/adb/modules/zapret2/zapret2/scripts/zapret-start.sh 2>&1")
        getZapret2Status().first
    }

    suspend fun pingEndpoint(host: String, port: Int = 443, timeoutMs: Int = 1800): Long? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                System.currentTimeMillis() - start
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun testCloudflareEndpoints(): List<LatencyInfo> = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            LatencyInfo(0, "TgBypass Локальный (Port 1444)", "127.0.0.1", DEFAULT_PORT, null),
            LatencyInfo(1, "ZaSto WSS Edge (amberwick)", "edge.amberwick.workers.dev", 443, null),
            LatencyInfo(2, "ZaSto WSS Резерв (brackencombe)", "nodeone.brackencombe.workers.dev", 443, null),
            LatencyInfo(3, "Cloudflare Edge DC2 (Амстердам)", "kws2.pclead.co.uk", 443, null),
            LatencyInfo(4, "Cloudflare Edge DC4 (Медиа/Видео)", "kws4.pclead.co.uk", 443, null),
            LatencyInfo(5, "Cloudflare Anycast DNS (1.1.1.1)", "1.1.1.1", 443, null)
        )
        coroutineScope {
            endpoints.map { item ->
                async {
                    val lat = pingEndpoint(item.host, item.port)
                    item.copy(latencyMs = lat)
                }
            }.awaitAll()
        }
    }

    fun openTelegram(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val known = listOf(
                "com.radolyn.ayugram",
                "org.zastogram.messenger.dev",
                "org.telegram.messenger",
                "app.exteragram.messenger",
                "org.thunderdog.challegram",
                "tw.nekomimi.nekogram",
                "cc.modery.cherrygram"
            )
            val launchIntents = mutableListOf<Intent>()
            for (pkg in known) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    launchIntents.add(intent)
                }
            }
            if (launchIntents.isEmpty()) {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("tg://openmessage")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                return true
            }
            val base = launchIntents.removeAt(0).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val chooser = Intent.createChooser(base, "Выберите клиент Telegram").apply {
                if (launchIntents.isNotEmpty()) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, launchIntents.toTypedArray())
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "openTelegram error", e)
            false
        }
    }
}
