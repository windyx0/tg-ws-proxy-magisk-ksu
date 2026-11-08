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
import java.net.InetAddress
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
            latencyMs == null -> Color(0xFFF97575)
            latencyMs < 200 -> Color(0xFF48D597)
            latencyMs < 500 -> Color(0xFFFFD600)
            else -> Color(0xFFF97575)
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

data class PluginEngineStatus(
    val supported: Boolean,
    val enabled: Boolean,
    val safeMode: Boolean,
    val bridgePluginInstalled: Boolean,
    val bridgePluginEnabled: Boolean
)

object RootBridge {
    private const val TAG = "TgBypassRoot"
    const val DEFAULT_PORT = 1444

    val ALL_TELEGRAM_PACKAGES = listOf(
        Pair("com.radolyn.ayugram", "AyuGram"),
        Pair("app.exteragram.messenger", "exteraGram"),
        Pair("com.exteragram.messenger", "exteraGram"),
        Pair("org.zastogram.messenger.dev", "ZaStoGram"),
        Pair("org.telegram.messenger", "Telegram (Play/Official)"),
        Pair("org.telegram.messenger.web", "Telegram Direct / Web"),
        Pair("org.thunderdog.challegram", "Telegram X"),
        Pair("org.telegram.plus", "Plus Messenger"),
        Pair("tw.nekomimi.nekogram", "Nekogram"),
        Pair("cc.modery.cherrygram", "Cherrygram"),
        Pair("nekox.messenger", "NekoX"),
        Pair("org.telegram.BifToGram", "BifToGram")
    )

    val AVAILABLE_BACKENDS = listOf(
        BackendOption("tgbypass", "TgBypass Daemon", "MTPROTO", "127.0.0.1", 1444, "Локальный WebSocket демон обхода"),
        BackendOption("zasto_wss_edge", "ZaSto WSS Cloudflare", "WSS_RELAY", "edge.amberwick.workers.dev", 443, "ZaSto Cloudflare Worker туннель"),
        BackendOption("zasto_wss_node", "ZaSto WSS Резерв", "WSS_RELAY", "nodeone.brackencombe.workers.dev", 443, "ZaSto резервный Worker туннель"),
        BackendOption("cloudflare_dc2", "Cloudflare Front DC2", "MTPROTO", "kws2.pclead.co.uk", 443, "Cloudflare Edge DC2 Амстердам")
    )

    suspend fun readAyuGramSyncState(): BackendSyncInfo? = withContext(Dispatchers.IO) {
        try {
            for ((pkg, _) in ALL_TELEGRAM_PACKAGES) {
                val out = execRoot("cat /data/data/$pkg/files/plugins/tgbypass_sync.json 2>/dev/null")
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
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pushBackendSync(backendKey: String, backendIdx: Int, timestampMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val syncJson = org.json.JSONObject().apply {
                put("backend", backendKey)
                put("backend_idx", backendIdx)
                put("timestamp", timestampMs)
                put("source", "tgbypass_app")
            }.toString()
            val escapedSync = syncJson.replace("\"", "\\\"")

            for ((pkg, _) in ALL_TELEGRAM_PACKAGES) {
                val hasDir = execRoot("test -d /data/data/$pkg/files/plugins && echo 1 || test -d /data/user/0/$pkg/files/plugins && echo 1").contains("1")
                if (hasDir) {
                    val uidGid = execRoot("stat -c %u:%g /data/data/$pkg 2>/dev/null").trim().ifEmpty { "1000:1000" }
                    val syncPath = "/data/data/$pkg/files/plugins/tgbypass_sync.json"
                    val settingsPath = "/data/data/$pkg/files/plugins/plugin_settings.json"

                    execRoot("echo \"$escapedSync\" > $syncPath && chown $uidGid $syncPath && chmod 666 $syncPath")

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
                }
            }

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
            out.contains("uid=0")
        } catch (e: Exception) {
            Log.e(TAG, "requestRoot error", e)
            false
        }
    }

    suspend fun execRoot(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val p = try {
                Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", cmd))
            } catch (_: Exception) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            }
            val reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
            val sb = java.lang.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            p.waitFor()
            sb.toString().trim()
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

    suspend fun isEndpointReachable(host: String, port: Int = 443, timeoutMs: Int = 1200): Boolean = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(host)
            Socket().use { s ->
                s.connect(InetSocketAddress(addr, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getVectorStatus(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val out = execRoot("/data/adb/lspd/cli status 2>/dev/null")
        val isRunning = out.contains("Framework Version:")
        var ver = "2.2"
        if (isRunning) {
            val lines = out.split("\n")
            for (line in lines) {
                if (line.contains("Framework Version:")) {
                    ver = line.substringAfter("Framework Version:").trim()
                }
            }
        }
        Pair(isRunning, ver)
    }

    suspend fun isModuleEnabledInVector(pkg: String = "com.windyx0.tgbypass"): Boolean = withContext(Dispatchers.IO) {
        val out = execRoot("/data/adb/lspd/cli modules ls 2>/dev/null")
        out.contains(pkg) && out.contains("enabled")
    }

    suspend fun inspectClientPluginState(pkg: String): PluginEngineStatus = withContext(Dispatchers.IO) {
        val hasDir = execRoot("test -d /data/data/$pkg/files/plugins && echo 1 || test -d /data/user/0/$pkg/files/plugins && echo 1").contains("1")
        if (!hasDir) {
            return@withContext PluginEngineStatus(
                supported = false,
                enabled = false,
                safeMode = false,
                bridgePluginInstalled = false,
                bridgePluginEnabled = false
            )
        }

        val bridgeInstalled = execRoot("test -f /data/data/$pkg/files/plugins/tg_ws_vector_bridge.py && echo 1").contains("1")

        var engineEnabled = true
        var safeMode = false
        var bridgeEnabled = true

        val prefs = execRoot("cat /data/data/$pkg/shared_prefs/exteraconfig.xml /data/data/$pkg/shared_prefs/ayuconfig.xml /data/data/$pkg/shared_prefs/mainconfig.xml 2>/dev/null")
        if (prefs.contains("name=\"pluginsEngine\" value=\"false\"") || prefs.contains("name=\"plugins_engine\" value=\"false\"")) {
            engineEnabled = false
        }
        if (prefs.contains("name=\"pluginsSafeMode\" value=\"true\"") || prefs.contains("name=\"plugins_safe_mode\" value=\"true\"") || prefs.contains("name=\"safe_mode\" value=\"true\"")) {
            safeMode = true
        }

        val pluginSettings = execRoot("cat /data/data/$pkg/shared_prefs/plugin_settings.xml 2>/dev/null")
        if (pluginSettings.contains("name=\"plugin_enabled_tg_ws_vector_bridge\" value=\"false\"")) {
            bridgeEnabled = false
        }

        PluginEngineStatus(
            supported = true,
            enabled = engineEnabled,
            safeMode = safeMode,
            bridgePluginInstalled = bridgeInstalled,
            bridgePluginEnabled = bridgeEnabled
        )
    }

    suspend fun getInstalledTelegramClients(context: Context): List<TelegramClientInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TelegramClientInfo>()
        val pm = context.packageManager
        val fromPm = try { pm.getInstalledPackages(0).map { it.packageName }.toSet() } catch (_: Exception) { null } ?: emptySet()
        val fromShell = execRoot("pm list packages 2>/dev/null").split("\n").map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        val installedSet = fromPm + fromShell
        val vectorScope = execRoot("/data/adb/lspd/cli scope ls com.windyx0.tgbypass 2>/dev/null")

        for ((pkg, name) in ALL_TELEGRAM_PACKAGES) {
            val installed = installedSet.contains(pkg) || runCatching { pm.getPackageInfo(pkg, 0) != null }.getOrDefault(false)
            if (!installed) continue

            val inScope = vectorScope.contains(pkg)
            val pluginStatus = inspectClientPluginState(pkg)

            if (pluginStatus.supported && pluginStatus.enabled && !pluginStatus.safeMode) {
                val isOk = pluginStatus.bridgePluginInstalled && pluginStatus.bridgePluginEnabled
                val detail = when {
                    isOk -> "Мост-плагин активен (127.0.0.1:1444)"
                    pluginStatus.bridgePluginInstalled -> "Плагин отключен в настройках"
                    else -> "Плагин не установлен"
                }
                list.add(TelegramClientInfo(
                    packageName = pkg,
                    name = name,
                    isInstalled = true,
                    method = BypassMethod.AYUGRAM_PLUGIN,
                    isConfigured = isOk,
                    detail = detail
                ))
            } else {
                val detail = when {
                    pluginStatus.supported && pluginStatus.safeMode -> {
                        if (inScope) "Безопасный режим -> Vector Hook активен" else "Безопасный режим -> Не в области Vector"
                    }
                    pluginStatus.supported && !pluginStatus.enabled -> {
                        if (inScope) "Плагины выключены -> Vector Hook активен" else "Плагины выключены -> Не в области Vector"
                    }
                    pkg == "org.zastogram.messenger.dev" -> "Встроенный WSS-транспорт активен"
                    inScope -> "Vector Hook активен"
                    else -> "Не в области действия Vector"
                }
                list.add(TelegramClientInfo(
                    packageName = pkg,
                    name = name,
                    isInstalled = true,
                    method = BypassMethod.VECTOR_HOOK,
                    isConfigured = inScope || pkg == "org.zastogram.messenger.dev",
                    detail = detail
                ))
            }
        }

        list
    }

    suspend fun autoInstallAndFixAll(context: Context): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val pm = context.packageManager

        val pluginBytes = try {
            context.assets.open("tg_ws_vector_bridge.py").use { it.readBytes() }
        } catch (_: Exception) { null }

        val tmpFile = if (pluginBytes != null) {
            val f = java.io.File(context.cacheDir, "tg_ws_vector_bridge.py")
            f.writeBytes(pluginBytes)
            f
        } else null

        // 1. Ensure Vector module is enabled in LSPosed/Vector
        execRoot("/data/adb/lspd/cli modules enable com.windyx0.tgbypass 2>/dev/null")

        val fromPm = try { pm.getInstalledPackages(0).map { it.packageName }.toSet() } catch (_: Exception) { null } ?: emptySet()
        val fromShell = execRoot("pm list packages 2>/dev/null").split("\n").map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        val installedSet = fromPm + fromShell

        // 2. Scan each installed client and automatically configure plugins vs Vector hook
        for ((pkg, name) in ALL_TELEGRAM_PACKAGES) {
            val installed = installedSet.contains(pkg) || runCatching { pm.getPackageInfo(pkg, 0) != null }.getOrDefault(false)
            if (!installed) continue

            val pluginStatus = inspectClientPluginState(pkg)

            if (pluginStatus.supported) {
                // Deploy / update bridge plugin
                if (tmpFile != null) {
                    val uidGid = execRoot("stat -c %u:%g /data/data/$pkg 2>/dev/null").trim().ifEmpty { "1000:1000" }
                    val targetDir = "/data/data/$pkg/files/plugins"
                    val prefsFile = "/data/data/$pkg/shared_prefs/plugin_settings.xml"

                    execRoot("mkdir -p $targetDir")
                    execRoot("cp ${tmpFile.absolutePath} $targetDir/tg_ws_vector_bridge.py")
                    execRoot("cp ${tmpFile.absolutePath} $targetDir/.temp_tg_ws_vector_bridge.plugin")
                    execRoot("chown -R $uidGid $targetDir")
                    execRoot("chmod 600 $targetDir/tg_ws_vector_bridge.py $targetDir/.temp_tg_ws_vector_bridge.plugin")

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
                }

                // If plugins are enabled and safe mode is OFF:
                if (pluginStatus.enabled && !pluginStatus.safeMode) {
                    // Remove from Vector scope to prevent ART crash
                    execRoot("/data/adb/lspd/cli scope rm com.windyx0.tgbypass $pkg/0 2>/dev/null")
                    sb.append("$name: мост-плагин активен (Vector Hook исключен во избежание конфликта).\n")
                } else {
                    // Safe mode is ON or plugins are disabled -> fallback to Vector Hook!
                    execRoot("/data/adb/lspd/cli scope add com.windyx0.tgbypass $pkg/0 2>/dev/null")
                    sb.append("$name: плагины отключены, подключен Vector Hook.\n")
                }
            } else {
                // Client has no plugin engine -> Vector Hook
                execRoot("/data/adb/lspd/cli scope add com.windyx0.tgbypass $pkg/0 2>/dev/null")
                sb.append("$name: Vector Hook добавлен в область действия.\n")
            }
        }

        // 3. Ensure proxy daemon is alive
        com.windyx0.tgbypass.proxy.ProxyService.start(context, DEFAULT_PORT)
        execRoot("am start-foreground-service -n com.windyx0.tgbypass/com.windyx0.tgbypass.proxy.ProxyService -a com.windyx0.tgbypass.START --ei EXTRA_PORT 1444 --es EXTRA_SECRET 7ee8902211954d7e8235f108a6313d42 >/dev/null 2>&1")

        var isUp = false
        for (i in 0 until 10) {
            if (isPortListening()) {
                isUp = true
                break
            }
            delay(250)
        }
        if (isUp) {
            sb.append("Демон 127.0.0.1:1444 запущен.")
        } else {
            sb.append("Демон 127.0.0.1:1444 запускается...")
        }

        sb.toString().trim()
    }

    suspend fun getZapret2Status(): Triple<Boolean, String, String> = withContext(Dispatchers.IO) {
        val pid = execRoot("pgrep -f nfqws2 2>/dev/null | head -n 1").trim()
        val isRunning = pid.isNotEmpty()
        val out = if (isRunning) "Активен (PID $pid)" else "Остановлен"
        val mode = if (isRunning) "NFQWS2 Multi-Strategy" else "Выключен"
        Triple(isRunning, out, mode)
    }

    suspend fun restartZapret2(): Boolean = withContext(Dispatchers.IO) {
        execRoot("/data/adb/modules/zapret2/service.sh 2>&1 &")
        delay(1000)
        getZapret2Status().first
    }

    suspend fun pingEndpoint(host: String, port: Int = 443, timeoutMs: Int = 2500): Long? = withContext(Dispatchers.IO) {
        for (attempt in 1..2) {
            try {
                val addr = InetAddress.getByName(host)
                val socketAddress = InetSocketAddress(addr, port)
                val start = System.currentTimeMillis()
                Socket().use { s ->
                    s.tcpNoDelay = true
                    s.connect(socketAddress, timeoutMs)
                    return@withContext (System.currentTimeMillis() - start)
                }
            } catch (_: Exception) {
                if (attempt == 1) {
                    delay(120)
                }
            }
        }
        null
    }

    suspend fun testCloudflareEndpoints(): List<LatencyInfo> = coroutineScope {
        val list = listOf(
            LatencyInfo(1, "Cloudflare Front DC1 (Майами)", "kws1.pclead.co.uk", 443, null),
            LatencyInfo(2, "Cloudflare Front DC2 (Амстердам)", "kws2.pclead.co.uk", 443, null),
            LatencyInfo(4, "Cloudflare Front DC4 (Амстердам)", "kws4.pclead.co.uk", 443, null),
            LatencyInfo(5, "Cloudflare Front DC5 (Сингапур)", "kws5.pclead.co.uk", 443, null),
            LatencyInfo(203, "Cloudflare Front DC203 (Тест)", "kws203.pclead.co.uk", 443, null),
            LatencyInfo(0, "ZaSto WSS Edge (Worker Primary)", "edge.amberwick.workers.dev", 443, null),
            LatencyInfo(0, "ZaSto WSS Node (Worker Backup)", "nodeone.brackencombe.workers.dev", 443, null)
        )

        list.mapIndexed { idx, item ->
            async {
                delay(idx * 30L)
                val lat = pingEndpoint(item.host, item.port)
                item.copy(latencyMs = lat)
            }
        }.awaitAll()
    }

    suspend fun openTelegram(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        if (!packageName.isNullOrEmpty()) {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return@withContext
                } catch (_: Exception) {}
            }
            execRoot("monkey -p $packageName -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 &")
            return@withContext
        }

        for ((pkg, _) in ALL_TELEGRAM_PACKAGES) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return@withContext
                } catch (_: Exception) {}
            }
        }
        for ((pkg, _) in ALL_TELEGRAM_PACKAGES) {
            val path = execRoot("pm path $pkg 2>/dev/null")
            if (path.isNotEmpty()) {
                execRoot("monkey -p $pkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 &")
                return@withContext
            }
        }
    }
}
