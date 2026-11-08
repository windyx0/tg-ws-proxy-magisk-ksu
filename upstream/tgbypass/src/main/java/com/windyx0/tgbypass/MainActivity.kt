package com.windyx0.tgbypass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TgBypassMaterialTheme {
                TgBypassDashboard()
            }
        }
    }
}

@Composable
fun TgBypassMaterialTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = Color(0xFF80CAFF),
            onPrimary = Color(0xFF003353),
            primaryContainer = Color(0xFF004B75),
            onPrimaryContainer = Color(0xFFCBE6FF),
            secondary = Color(0xFF56DCA0),
            onSecondary = Color(0xFF003822),
            secondaryContainer = Color(0xFF005234),
            onSecondaryContainer = Color(0xFF75F9BD),
            background = Color(0xFF111418),
            surface = Color(0xFF191C20),
            surfaceVariant = Color(0xFF232830),
            onSurface = Color(0xFFE2E2E6),
            onSurfaceVariant = Color(0xFFC3C7D0),
            outline = Color(0xFF3B414C),
            outlineVariant = Color(0xFF2B303A)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TgBypassDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("tgbypass_prefs", Context.MODE_PRIVATE) }

    var hasRoot by remember {
        mutableStateOf<Boolean?>(if (prefs.contains("last_has_root")) prefs.getBoolean("last_has_root", true) else null)
    }
    var isVectorRunning by remember {
        mutableStateOf<Boolean?>(if (prefs.contains("last_vector_running")) prefs.getBoolean("last_vector_running", true) else null)
    }
    var vectorVersion by remember {
        mutableStateOf(prefs.getString("last_vector_version", "2.2") ?: "2.2")
    }
    var isModuleEnabledInVector by remember {
        mutableStateOf<Boolean?>(if (prefs.contains("last_module_enabled")) prefs.getBoolean("last_module_enabled", true) else null)
    }
    var isPortListening by remember {
        mutableStateOf<Boolean?>(if (prefs.contains("last_port_listening")) prefs.getBoolean("last_port_listening", true) else null)
    }
    var isEdgeOnline by remember {
        mutableStateOf<Boolean?>(if (prefs.contains("last_edge_online")) prefs.getBoolean("last_edge_online", true) else null)
    }
    var isNodeOnline by remember {
        mutableStateOf<Boolean?>(if (prefs.contains("last_node_online")) prefs.getBoolean("last_node_online", true) else null)
    }
    var isCfOnline by remember {
        mutableStateOf<Boolean?>(if (prefs.contains("last_cf_online")) prefs.getBoolean("last_cf_online", true) else null)
    }
    var isZapret2Running by remember {
        mutableStateOf<Boolean?>(if (prefs.contains("last_zapret2_running")) prefs.getBoolean("last_zapret2_running", true) else null)
    }
    var zapret2Status by remember { mutableStateOf<Triple<Boolean, String, String>?>(null) }
    var telegramClients by remember { mutableStateOf<List<TelegramClientInfo>>(emptyList()) }
    var isFixingClients by remember { mutableStateOf(false) }

    var selectedBackend by remember { mutableStateOf(prefs.getString("active_backend", "tgbypass") ?: "tgbypass") }
    var lastSyncTimestamp by remember { mutableStateOf(0L) }
    var inlineBannerText by remember { mutableStateOf<String?>(null) }

    fun showInline(msg: String) {
        inlineBannerText = msg
        scope.launch {
            delay(4000)
            if (inlineBannerText == msg) inlineBannerText = null
        }
    }

    fun selectAndSyncBackend(backendKey: String, backendName: String) {
        selectedBackend = backendKey
        prefs.edit().putString("active_backend", backendKey).apply()
        val now = System.currentTimeMillis()
        lastSyncTimestamp = now
        val idx = RootBridge.AVAILABLE_BACKENDS.indexOfFirst { it.key == backendKey }.coerceAtLeast(0)
        scope.launch {
            RootBridge.pushBackendSync(backendKey, idx, now)
            try {
                val syncIntent = Intent("com.windyx0.tgbypass.ACTION_BACKEND_SYNC").apply {
                    putExtra("backend", backendKey)
                    putExtra("backend_idx", idx)
                    putExtra("timestamp", now)
                    putExtra("source", "tgbypass_app")
                }
                context.sendBroadcast(syncIntent)
            } catch (_: Exception) {}
        }
        showInline("Выбран бэкенд: $backendName (синхронизировано)")
    }

    // Dynamic receiver for instant incoming sync from Telegram / AyuGram
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "com.windyx0.tgbypass.ACTION_BACKEND_SYNC") {
                    val backend = intent.getStringExtra("backend")
                    val ts = intent.getLongExtra("timestamp", 0L)
                    val src = intent.getStringExtra("source") ?: ""
                    if (!backend.isNullOrEmpty() && src == "ayugram" && backend != selectedBackend) {
                        selectedBackend = backend
                        prefs.edit().putString("active_backend", backend).apply()
                        lastSyncTimestamp = ts
                        val bName = RootBridge.AVAILABLE_BACKENDS.find { it.key == backend }?.name ?: backend
                        showInline("Синхронизировано из Telegram: $bName")
                    }
                }
            }
        }
        val filter = IntentFilter("com.windyx0.tgbypass.ACTION_BACKEND_SYNC")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    var showPingDialog by remember { mutableStateOf(false) }
    var pingResults by remember { mutableStateOf<List<LatencyInfo>>(emptyList()) }
    var isPinging by remember { mutableStateOf(false) }

    var showUpdatesDialog by remember { mutableStateOf(false) }
    var remoteVersionInfo by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    LaunchedEffect(showUpdatesDialog) {
        if (showUpdatesDialog) {
            isCheckingUpdates = true
            withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://raw.githubusercontent.com/windyx0/tg-ws-proxy-magisk-ksu/main/update.json")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        val txt = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(txt)
                        val code = json.optInt("versionCode", 122)
                        val ver = json.optString("version", "v1.2.2")
                        withContext(Dispatchers.Main) {
                            remoteVersionInfo = if (code > 122) "Доступно обновление: $ver" else "У вас актуальная версия ($ver)"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            remoteVersionInfo = "Актуальная версия (v1.2.2)"
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        remoteVersionInfo = "Актуальная версия (v1.2.2)"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isCheckingUpdates = false
                    }
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember { mutableStateOf(true) }

    suspend fun safeReachable(host: String, port: Int = 443): Boolean {
        if (RootBridge.isEndpointReachable(host, port, 500)) return true
        // If first probe failed, retry once with backoff in case device/network is resuming
        delay(300)
        return RootBridge.isEndpointReachable(host, port, 700)
    }

    fun syncState() {
        scope.launch(Dispatchers.IO) {
            // 1. Immediate fast parallel network & port checks so UI reflects live status without delay
            val pJob = async { RootBridge.isPortListening() }
            val edgeJob = async { safeReachable("edge.amberwick.workers.dev", 443) }
            val nodeJob = async { safeReachable("nodeone.brackencombe.workers.dev", 443) }
            val cfJob = async { safeReachable("kws2.pclead.co.uk", 443) }

            val p = pJob.await()
            val edge = edgeJob.await()
            val node = nodeJob.await()
            val cf = cfJob.await()

            withContext(Dispatchers.Main) {
                isPortListening = p
                isEdgeOnline = edge
                isNodeOnline = node
                isCfOnline = cf
                prefs.edit()
                    .putBoolean("last_port_listening", p)
                    .putBoolean("last_edge_online", edge)
                    .putBoolean("last_node_online", node)
                    .putBoolean("last_cf_online", cf)
                    .apply()
            }

            // 2. Heavy root checks
            val root = RootBridge.requestRoot()
            withContext(Dispatchers.Main) {
                hasRoot = root
                prefs.edit().putBoolean("last_has_root", root).apply()
            }
            if (root) {
                val vStat = RootBridge.getVectorStatus()
                val modEnabled = RootBridge.isModuleEnabledInVector()
                val z2 = RootBridge.getZapret2Status()
                val clients = RootBridge.getInstalledTelegramClients(context)
                withContext(Dispatchers.Main) {
                    isVectorRunning = vStat.first
                    vectorVersion = vStat.second
                    isModuleEnabledInVector = modEnabled
                    isZapret2Running = z2.first
                    zapret2Status = z2
                    telegramClients = clients
                    prefs.edit()
                        .putBoolean("last_vector_running", vStat.first)
                        .putString("last_vector_version", vStat.second)
                        .putBoolean("last_module_enabled", modEnabled)
                        .putBoolean("last_zapret2_running", z2.first)
                        .apply()
                }
            } else {
                withContext(Dispatchers.Main) {
                    isVectorRunning = false
                    isModuleEnabledInVector = false
                    isZapret2Running = false
                    prefs.edit()
                        .putBoolean("last_vector_running", false)
                        .putBoolean("last_module_enabled", false)
                        .putBoolean("last_zapret2_running", false)
                        .apply()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isForeground = true
                    syncState()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isForeground = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        syncState()
        while (true) {
            delay(2500)
            if (isForeground) {
                withContext(Dispatchers.IO) {
                    val p = RootBridge.isPortListening()
                    val edge = safeReachable("edge.amberwick.workers.dev", 443)
                    val node = safeReachable("nodeone.brackencombe.workers.dev", 443)
                    val cf = safeReachable("kws2.pclead.co.uk", 443)
                    withContext(Dispatchers.Main) {
                        isPortListening = p
                        isEdgeOnline = edge
                        isNodeOnline = node
                        isCfOnline = cf
                    }
                }
            }

            // Periodic sync check from AyuGram
            val sync = RootBridge.readAyuGramSyncState()
            if (sync != null && sync.timestamp > lastSyncTimestamp && sync.source == "ayugram") {
                if (sync.backend != selectedBackend) {
                    selectedBackend = sync.backend
                    prefs.edit().putString("active_backend", sync.backend).apply()
                    lastSyncTimestamp = sync.timestamp
                    val bName = RootBridge.AVAILABLE_BACKENDS.find { it.key == sync.backend }?.name ?: sync.backend
                    showInline("Синхронизировано из Telegram: $bName")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.avatar_mahiru),
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TgBypass",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "v1.2.2",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Unified All-In-One Manager",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                maxLines = 1,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { syncState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Тест Cloudflare туннелей") },
                                leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showPingDialog = true
                                    isPinging = true
                                    scope.launch {
                                        pingResults = RootBridge.testCloudflareEndpoints()
                                        isPinging = false
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Проверка обновлений") },
                                leadingIcon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showUpdatesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("О приложении") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notification Banner
            AnimatedVisibility(
                visible = inlineBannerText != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = inlineBannerText.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // 1. HERO STATUS CARD
            val isChecking = isPortListening == null || isVectorRunning == null || isModuleEnabledInVector == null || hasRoot == null
            val allGood = (isPortListening == true) && (isVectorRunning == true) && (isModuleEnabledInVector == true)
            val clientsConfigured = telegramClients.isEmpty() || telegramClients.all { !it.isInstalled || it.isConfigured }
            val allConfiguredAndActive = allGood && clientsConfigured
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = when {
                            isChecking -> Color(0xFF383314)
                            allGood -> Color(0xFF143823)
                            else -> Color(0xFF381414)
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isChecking -> Color(0xFFFFD600)
                                            allGood -> Color(0xFF48D597)
                                            else -> Color(0xFFF97575)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isChecking -> "ПРОВЕРКА..."
                                    allGood -> "СИСТЕМА АКТИВНА"
                                    else -> "ТРЕБУЕТСЯ НАСТРОЙКА"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isChecking -> Color(0xFFFFD600)
                                    allGood -> Color(0xFF75F9BD)
                                    else -> Color(0xFFF97575)
                                }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isChecking -> Color(0xFF383314)
                                    allGood -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isChecking -> Icons.Default.HourglassTop
                                allGood -> Icons.Default.Shield
                                else -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = when {
                                isChecking -> Color(0xFFFFD600)
                                allGood -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when {
                            isChecking -> "Проверка компонентов..."
                            allGood -> "Обход Telegram активен - v1.2.2"
                            else -> "TgBypass All-In-One - v1.2.2"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isChecking -> "Определяем доступность локального шлюза 1444 и Vector LSPosed"
                            allGood -> "Локальный демон 127.0.0.1:1444 - Vector хуки - AyuGram мост"
                            else -> when {
                                hasRoot == false -> "Root права недоступны"
                                isPortListening == false -> "Служба прокси 127.0.0.1:1444 не запущена"
                                isVectorRunning == false -> "Vector Framework (LSPosed) не запущен"
                                isModuleEnabledInVector == false -> "Модуль TgBypass не включен в Vector"
                                else -> "Проверьте статус компонентов ниже"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!allConfiguredAndActive) {
                        Spacer(modifier = Modifier.height(20.dp))

                        // Fixed minHeight so text never clips!
                        Button(
                            onClick = {
                                isFixingClients = true
                                scope.launch {
                                    val res = RootBridge.autoInstallAndFixAll(context)
                                    syncState()
                                    isFixingClients = false
                                    showInline(res)
                                }
                            },
                            enabled = !isFixingClients,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isFixingClients) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Применение настроек...")
                            } else {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Автонастройка и починка всех клиентов",
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. BACKEND SELECTION CARD (TgBypass Daemon / ZaSto WSS / Cloudflare Front)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Выбор бэкенда обхода",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "TgBypass Daemon, ZaSto WSS или Cloudflare",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    RootBridge.AVAILABLE_BACKENDS.forEachIndexed { idx, backend ->
                        if (idx > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val isSelected = (backend.key == selectedBackend)
                        val isOnline: Boolean? = when (backend.key) {
                            "tgbypass" -> isPortListening
                            "zasto_wss_edge" -> isEdgeOnline
                            "zasto_wss_node" -> isNodeOnline
                            "cloudflare_dc2" -> isCfOnline
                            else -> true
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectAndSyncBackend(backend.key, backend.name)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectAndSyncBackend(backend.key, backend.name)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backend.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = "${backend.host}:${backend.port} - ${backend.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (isOnline) {
                                    null -> Color(0xFF383314)
                                    true -> Color(0xFF143823)
                                    false -> Color(0xFF381414)
                                }
                            ) {
                                Text(
                                    text = when (isOnline) {
                                        null -> "ПРОВЕРКА..."
                                        true -> "ОНЛАЙН"
                                        false -> "ОФФЛАЙН"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (isOnline) {
                                        null -> Color(0xFFFFD600)
                                        true -> Color(0xFF75F9BD)
                                        false -> Color(0xFFF97575)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. UPDATES & CLOUDFLARE ACTIONS CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Обновления и Cloudflare",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Проверка сетевых узлов и актуальности модулей",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                showPingDialog = true
                                isPinging = true
                                scope.launch {
                                    pingResults = RootBridge.testCloudflareEndpoints()
                                    isPinging = false
                                }
                            },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 50.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cloudflare тест", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = { showUpdatesDialog = true },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 50.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обновления", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }

            // 4. TELEGRAM CLIENTS & PLUGINS AUTO-CHECKER
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Клиенты Telegram и Плагины",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Автоматический чекер состояния",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (telegramClients.isEmpty()) {
                        Text(
                            text = "Поиск установленных клиентов...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        telegramClients.forEachIndexed { idx, client ->
                            if (idx > 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = client.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = if (client.method == BypassMethod.AYUGRAM_PLUGIN) "PLUGIN" else "VECTOR",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = client.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (client.isConfigured) Color(0xFF143823) else Color(0xFF381414)
                                ) {
                                    Text(
                                        text = if (client.isConfigured) "ГОТОВ" else "ИСПРАВИТЬ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (client.isConfigured) Color(0xFF75F9BD) else Color(0xFFF97575),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            RootBridge.openTelegram(context)
                            showInline("Выбор клиента Telegram")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Запустить Telegram", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 5. FRAMEWORK & DAEMON STATUS CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isVectorRunning == true) Color(0xFF48D597) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Статус компонентов системы",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Daemon 1444
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = "Служба прокси-демона", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = when (isPortListening) {
                                    null -> "Проверка порта 1444..."
                                    true -> "127.0.0.1:1444 слушает"
                                    false -> "Порт 1444 не отвечает"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (isPortListening) {
                                null -> Color(0xFF383314)
                                true -> Color(0xFF143823)
                                false -> Color(0xFF381414)
                            }
                        ) {
                            Text(
                                text = when (isPortListening) {
                                    null -> "ПРОВЕРКА..."
                                    true -> "ОНЛАЙН"
                                    false -> "ОФФЛАЙН"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (isPortListening) {
                                    null -> Color(0xFFFFD600)
                                    true -> Color(0xFF75F9BD)
                                    false -> Color(0xFFF97575)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Vector LSPosed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = "Vector Framework", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = when (isVectorRunning) {
                                    null -> "Проверка Vector Framework..."
                                    true -> "Версия $vectorVersion (LSPosed)"
                                    false -> "Vector LSPosed не обнаружен"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (isVectorRunning) {
                                null -> Color(0xFF383314)
                                true -> Color(0xFF143823)
                                false -> Color(0xFF381414)
                            }
                        ) {
                            Text(
                                text = when (isVectorRunning) {
                                    null -> "ПРОВЕРКА..."
                                    true -> "АКТИВЕН"
                                    false -> "НЕ АКТИВЕН"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (isVectorRunning) {
                                    null -> Color(0xFFFFD600)
                                    true -> Color(0xFF75F9BD)
                                    false -> Color(0xFFF97575)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Zapret2 nfqws2
                    val z2 = zapret2Status
                    val z2Running = zapret2Status?.first ?: isZapret2Running
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = "Zapret2 (nfqws2)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = when (z2Running) {
                                    null -> "Проверка службы nfqws2..."
                                    true -> if (!z2?.second.isNullOrEmpty()) "PID: ${z2?.second} - NFQUEUE 200" else "nfqws2 активен - NFQUEUE 200"
                                    false -> "Служба остановлена"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (z2Running) {
                                null -> Color(0xFF383314)
                                true -> Color(0xFF143823)
                                false -> Color(0xFF381414)
                            }
                        ) {
                            Text(
                                text = when (z2Running) {
                                    null -> "ПРОВЕРКА..."
                                    true -> "АКТИВЕН"
                                    false -> "ОСТАНОВЛЕН"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (z2Running) {
                                    null -> Color(0xFFFFD600)
                                    true -> Color(0xFF75F9BD)
                                    false -> Color(0xFFF97575)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Cloudflare & Endpoints Ping Dialog with Strict Color Coding: <200 green, <500 yellow, >=500 red
    if (showPingDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPinging) showPingDialog = false },
            icon = { Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text(text = "Cloudflare и бэкенды обхода", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isPinging) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    } else {
                        pingResults.forEach { info ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(text = info.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(text = "${info.host}:${info.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = info.getContainerColor()
                                ) {
                                    Text(
                                        text = info.getBadgeText(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = info.getBadgeColor(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    isPinging = true
                    scope.launch {
                        pingResults = RootBridge.testCloudflareEndpoints()
                        isPinging = false
                    }
                }, enabled = !isPinging) { Text("Повторить тест") }
            },
            dismissButton = {
                TextButton(onClick = { showPingDialog = false }, enabled = !isPinging) { Text("Закрыть") }
            }
        )
    }

    // Updates Checker Dialog
    if (showUpdatesDialog) {
        AlertDialog(
            onDismissRequest = { showUpdatesDialog = false },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text(text = "Проверка обновлений", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Текущая версия: v1.2.2 (All-In-One Unified)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                    if (isCheckingUpdates) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Проверка обновлений на GitHub...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF143823),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = remoteVersionInfo ?: "У вас актуальная версия",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF75F9BD),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "- Встроенный демон tg-ws-proxy: v1.2.2\n- Встроенный Vector Hook модуль: v2.2\n- Встроенный AyuGram Bridge плагин: v2.2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/windyx0/tg-ws-proxy-magisk-ksu/releases"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) { Text("GitHub") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdatesDialog = false }) { Text("Закрыть") }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Image(
                    painter = painterResource(id = R.drawable.avatar_mahiru),
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            },
            title = { Text(text = "О приложении TgBypass", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "TgBypass All-In-One Unified", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = "Версия: 1.2.2\nРазработчик: @windyx0", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Включает в себя:\n- Менеджер и GUI с Material Design 3\n- Модуль Vector (LSPosed)\n- Мост-плагин для AyuGram (с автосинхронизацией)\n- ZaSto WSS Cloudflare туннели\n- Интеграцию с Zapret2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Закрыть") }
            }
        )
    }
}
