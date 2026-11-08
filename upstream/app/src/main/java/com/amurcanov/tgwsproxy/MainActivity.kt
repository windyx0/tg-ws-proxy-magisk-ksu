package com.amurcanov.tgwsproxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun formatLogLine(line: String): String = line

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_STATUS = "com.amurcanov.tgwsproxy.extra.OPEN_STATUS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TgWsProxyMaterialTheme {
                TgWsProxyDashboard()
            }
        }
    }
}

// ==============================================================================
// Material You (MD3) Dynamic & High-Contrast Dark Palette
// ==============================================================================
@Composable
fun TgWsProxyMaterialTheme(content: @Composable () -> Unit) {
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
            tertiary = Color(0xFFC6C0FF),
            onTertiary = Color(0xFF2E295E),
            tertiaryContainer = Color(0xFF453F76),
            background = Color(0xFF111418),
            surface = Color(0xFF191C20),
            surfaceVariant = Color(0xFF232830),
            onSurface = Color(0xFFE2E2E6),
            onSurfaceVariant = Color(0xFFC3C7D0),
            outline = Color(0xFF3B414C),
            outlineVariant = Color(0xFF2B303A),
            error = Color(0xFFFFB4AB),
            errorContainer = Color(0xFF93000A)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// ==============================================================================
// Main Screen: True Material Design 3 Dashboard
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TgWsProxyDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var isModuleInstalled by remember { mutableStateOf(false) }
    var moduleVersion by remember { mutableStateOf("v1.1.0") }
    var isProxyRunning by remember { mutableStateOf(false) }
    var isBbrActive by remember { mutableStateOf(false) }
    var isAutostart by remember { mutableStateOf(true) }

    // Inline notifications
    var inlineBannerText by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showTurboProgressDialog by remember { mutableStateOf(false) }
    var turboProgressSteps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isTurboExecuting by remember { mutableStateOf(false) }

    var showAboutDialog by remember { mutableStateOf(false) }

    var showUpdatesDialog by remember { mutableStateOf(false) }
    var updatesInfo by remember { mutableStateOf<SystemUpdatesInfo?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }

    var showPingDialog by remember { mutableStateOf(false) }
    var dcPings by remember { mutableStateOf<List<DcLatencyInfo>>(emptyList()) }
    var isPingingDcs by remember { mutableStateOf(false) }

    val forks = remember { RootModuleBridge.getDetectedForks(context) }
    val installedForks = remember(forks) { forks.filter { it.isInstalled } }
    var selectedForkPkg by remember {
        mutableStateOf(installedForks.firstOrNull()?.packageName ?: "com.radolyn.ayugram")
    }

    fun showInlineMessage(msg: String) {
        inlineBannerText = msg
        scope.launch {
            delay(3500)
            if (inlineBannerText == msg) {
                inlineBannerText = null
            }
        }
    }

    fun syncState() {
        scope.launch {
            val root = RootModuleBridge.requestRoot()
            hasRoot = root
            if (root) {
                isModuleInstalled = RootModuleBridge.isModuleInstalled()
                if (isModuleInstalled) {
                    moduleVersion = RootModuleBridge.getModuleVersion()
                    isAutostart = RootModuleBridge.isAutostartEnabled()
                    isBbrActive = RootModuleBridge.isBbrActive()
                }
            }
            isProxyRunning = RootModuleBridge.isPortListening(RootModuleBridge.DEFAULT_PORT)
        }
    }

    fun runTurboProgress(targetCc: String = "bbr") {
        showTurboProgressDialog = true
        isTurboExecuting = true
        turboProgressSteps = emptyList()

        scope.launch {
            val stepsList = mutableListOf<Pair<String, String>>()
            RootModuleBridge.applyNetworkTurboStepByStep(targetCc) { step, detail ->
                stepsList.add(step to detail)
                turboProgressSteps = stepsList.toList()
            }
            isTurboExecuting = false
            syncState()
            showInlineMessage("Оптимизация $targetCc успешно завершена!")
        }
    }

    LaunchedEffect(Unit) {
        syncState()
        while (true) {
            delay(2500)
            isProxyRunning = RootModuleBridge.isPortListening(RootModuleBridge.DEFAULT_PORT)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "TgWsProxy",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Turbo",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "windyx0 edition • $moduleVersion",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Check updates (Comprehensive App + Module + CDN check)
                    IconButton(onClick = {
                        showUpdatesDialog = true
                        isCheckingUpdates = true
                        scope.launch {
                            updatesInfo = RootModuleBridge.checkComprehensiveUpdates()
                            isCheckingUpdates = false
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Проверить обновления",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Ping diagnostics (Cloudflare Edge & Local)
                    IconButton(onClick = {
                        showPingDialog = true
                        isPingingDcs = true
                        scope.launch {
                            dcPings = RootModuleBridge.testAllDatacenters()
                            isPingingDcs = false
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = "Пинг серверов",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // About
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "О приложении",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Refresh
                    IconButton(onClick = { syncState() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Inline Banner (Smooth replacement for popup toasts)
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

            // 1. HERO CARD: Master Proxy Controller
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tactile Status Pill
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = if (isProxyRunning) Color(0xFF143823) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(bottom = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isProxyRunning) Color(0xFF48D597) else Color(0xFF8A92A0))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProxyRunning) "АКТИВЕН • CLOUDFLARE EDGE" else "ПРОКСИ ВЫКЛЮЧЕН",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isProxyRunning) Color(0xFF75F9BD) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Main Status Icon
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                if (isProxyRunning) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isProxyRunning) Icons.Default.Bolt else Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (isProxyRunning) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isProxyRunning) "127.0.0.1:${RootModuleBridge.DEFAULT_PORT}" else "Telegram WS Proxy",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isProxyRunning)
                            "20 CDN эндпоинтов • Обход ТСПУ без пауз"
                        else
                            "Готов к запуску на порту ${RootModuleBridge.DEFAULT_PORT}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Big MD3 Action Button
                    Button(
                        onClick = {
                            scope.launch {
                                if (isProxyRunning) {
                                    RootModuleBridge.stopProxyService(context, RootModuleBridge.DEFAULT_PORT)
                                    showInlineMessage("Прокси остановлен")
                                } else {
                                    RootModuleBridge.startProxyService(context, RootModuleBridge.DEFAULT_PORT)
                                    showInlineMessage("Прокси запущен на порту ${RootModuleBridge.DEFAULT_PORT}")
                                }
                                syncState()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isProxyRunning)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary,
                            contentColor = if (isProxyRunning)
                                MaterialTheme.colorScheme.onError
                            else
                                MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isProxyRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isProxyRunning) "Остановить прокси" else "Запустить прокси (Турбо)",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 2. TELEGRAM INTEGRATION CARD
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
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Подключение к Telegram",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Открытие системного меню со всеми установленными Telegram-клиентами для мгновенного подключения прокси",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // PRIMARY ACTION: CONNECT IN TELEGRAM (SYSTEM CHOOSER)
                    Button(
                        onClick = {
                            scope.launch {
                                if (!isProxyRunning) {
                                    RootModuleBridge.startProxyService(context, RootModuleBridge.DEFAULT_PORT)
                                    syncState()
                                }
                                val ok = RootModuleBridge.openTelegramChooser(
                                    context,
                                    RootModuleBridge.DEFAULT_PORT
                                )
                                if (ok) {
                                    showInlineMessage("Открыто системное меню Telegram")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Подключить в Telegram",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SECONDARY ACTION: COPY LINK
                    OutlinedButton(
                        onClick = {
                            val secret = RootModuleBridge.getActiveSecret(context)
                            val link = "https://t.me/proxy?server=127.0.0.1&port=${RootModuleBridge.DEFAULT_PORT}&secret=$secret"
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Telegram Proxy", link))
                            showInlineMessage("Ссылка прокси скопирована в буфер")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Скопировать ссылку прокси",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 3. KERNEL NETWORK TURBO (ROOT) CARD
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
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Сетевой турбо-режим (Root)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // BBR / CUBIC selector row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Алгоритм перегрузки TCP",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isBbrActive) "Google BBR (Pacing активен)" else "Стандартный CUBIC",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilterChip(
                            selected = isBbrActive,
                            onClick = {
                                val nextCc = if (isBbrActive) "cubic" else "bbr"
                                runTurboProgress(nextCc)
                            },
                            label = {
                                Text(
                                    text = if (isBbrActive) "BBR" else "CUBIC",
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isBbrActive) Icons.Default.Bolt else Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Buffer size row (16 МБ always on one line)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Буферы 16\u00A0МБ & FastOpen",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Снятие лимитов пропускной способности",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                text = "16\u00A0МБ",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Autostart toggle row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Автозапуск при загрузке",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Запуск прокси и сетевого турбо при старте",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutostart,
                            onCheckedChange = { v ->
                                isAutostart = v
                                scope.launch {
                                    RootModuleBridge.setAutostart(v)
                                    showInlineMessage(if (v) "Автозапуск включен" else "Автозапуск выключен")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // PROGRESS-WINDOW ACTIVATING BUTTON
                    Button(
                        onClick = { runTurboProgress("bbr") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Применить ускорение ядра",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 4. MODULE & ROOT STATUS CARD
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
                            tint = if (hasRoot == true && isModuleInstalled) Color(0xFF48D597) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Интеграция с KernelSU / Magisk",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Root-права (su)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (hasRoot == true) "KernelSU предоставлен" else "Не предоставлены",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (hasRoot == true) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF48D597), modifier = Modifier.size(22.dp))
                        } else {
                            TextButton(onClick = { syncState() }) {
                                Text("Запросить")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Модуль tg_ws_proxy",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isModuleInstalled) "Установлен ($moduleVersion) • Action кнопка активна" else "Модуль не найден в /data/adb/modules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isModuleInstalled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF143823)
                            ) {
                                Text(
                                    text = "АКТИВЕН",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF75F9BD),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. SYSTEM & UPDATES CARD
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
                        Text(
                            text = "Система и обновления",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Item 1: Updates check
                    Surface(
                        onClick = {
                            showUpdatesDialog = true
                            isCheckingUpdates = true
                            scope.launch {
                                updatesInfo = RootModuleBridge.checkComprehensiveUpdates()
                                isCheckingUpdates = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Проверить обновления",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Приложение, модуль и сеть CDN",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Item 2: Ping diagnostics
                    Surface(
                        onClick = {
                            showPingDialog = true
                            isPingingDcs = true
                            scope.launch {
                                dcPings = RootModuleBridge.testAllDatacenters()
                                isPingingDcs = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCheck,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Тест серверов TG и CDN",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Измерение отклика узлов Cloudflare",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Item 3: About
                    Surface(
                        onClick = { showAboutDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "О приложении",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Версия 1.11.8 • windyx0",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // ==============================================================================
    // PROGRESS WINDOW DIALOG: Interactive Kernel Turbo Progress
    // ==============================================================================
    if (showTurboProgressDialog) {
        Dialog(onDismissRequest = { if (!isTurboExecuting) showTurboProgressDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Оптимизация ядра Linux",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isTurboExecuting) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF143823),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF48D597), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Стек TCP успешно оптимизирован!",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF75F9BD)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Step log entries
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        turboProgressSteps.forEach { (step, detail) ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF48D597),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = { showTurboProgressDialog = false },
                        enabled = !isTurboExecuting,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isTurboExecuting) "Применяется..." else "Готово")
                    }
                }
            }
        }
    }

    // ==============================================================================
    // ABOUT DIALOG (О приложении)
    // ==============================================================================
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Icon(
                    Icons.Default.RocketLaunch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "О приложении",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Telegram WS Proxy & Root Network Turbo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Автор: windyx0",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Версия приложения: 1.11.8-turbo (build 64)\n" +
                               "Модуль KernelSU: $moduleVersion\n" +
                               "Архитектура: arm64-v8a\n" +
                               "Локальный порт: 127.0.0.1:${RootModuleBridge.DEFAULT_PORT}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Особенности сборки:\n" +
                               "• Мгновенный обход ТСПУ через 20 CDN Cloudflare\n" +
                               "• Интеграция с ядром: BBR, FQ pacing, 16MB буферы\n" +
                               "• Мгновенная Action кнопка для KernelSU/APatch\n" +
                               "• Автоматическая поддержка AyuGram и exteraGram",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // ==============================================================================
    // COMPREHENSIVE UPDATES DIALOG (Проверка обновлений приложения, модуля и CDN)
    // ==============================================================================
    if (showUpdatesDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCheckingUpdates) showUpdatesDialog = false },
            icon = {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Проверка обновлений",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isCheckingUpdates) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Синхронизация с серверами обновлений...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val info = updatesInfo
                        if (info != null) {
                            // 1. App Update Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Приложение TgWsProxy", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(text = "Версия: ${info.appCurrentVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF48D597), modifier = Modifier.size(20.dp))
                                }
                            }

                            // 2. Module Update Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Модуль KernelSU/Magisk", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(text = "Версия: ${info.moduleCurrentVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF48D597), modifier = Modifier.size(20.dp))
                                }
                            }

                            // 3. Cloudflare CDN Endpoints Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Сеть Cloudflare Edge", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(text = info.cdnMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF48D597), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isCheckingUpdates = true
                        scope.launch {
                            updatesInfo = RootModuleBridge.checkComprehensiveUpdates()
                            isCheckingUpdates = false
                        }
                    },
                    enabled = !isCheckingUpdates
                ) {
                    Text("Проверить снова")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdatesDialog = false },
                    enabled = !isCheckingUpdates
                ) {
                    Text("Закрыть")
                }
            }
        )
    }

    // ==============================================================================
    // REAL PING DIAGNOSTICS DIALOG (Cloudflare Edge & Local)
    // ==============================================================================
    if (showPingDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPingingDcs) showPingDialog = false },
            icon = {
                Icon(
                    Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Задержка серверов и CDN",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isPingingDcs) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Измерение отклика CDN узлов...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        dcPings.forEach { info ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = info.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${info.host}:${info.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val ms = info.latencyMs
                                if (ms != null) {
                                    val chipColor = when {
                                        ms < 60 -> Color(0xFF143823)
                                        ms < 120 -> Color(0xFF383314)
                                        else -> Color(0xFF381414)
                                    }
                                    val textColor = when {
                                        ms < 60 -> Color(0xFF75F9BD)
                                        ms < 120 -> Color(0xFFF9E875)
                                        else -> Color(0xFFF97575)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = chipColor
                                    ) {
                                        Text(
                                            text = "$ms ms",
                                            maxLines = 1,
                                            softWrap = false,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    val isLocalAndStopped = info.dc == 0 && !isProxyRunning
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isLocalAndStopped) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF381414)
                                    ) {
                                        Text(
                                            text = if (isLocalAndStopped) "Остановлен" else "Таймаут",
                                            maxLines = 1,
                                            softWrap = false,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isLocalAndStopped) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFF97575),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isPingingDcs = true
                        scope.launch {
                            dcPings = RootModuleBridge.testAllDatacenters()
                            isPingingDcs = false
                        }
                    },
                    enabled = !isPingingDcs
                ) {
                    Text("Повторить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPingDialog = false },
                    enabled = !isPingingDcs
                ) {
                    Text("Закрыть")
                }
            }
        )
    }
}
