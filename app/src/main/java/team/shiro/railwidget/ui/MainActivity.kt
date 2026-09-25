package team.shiro.railwidget.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.shiro.railwidget.data.api.RailwayApiService
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.parser.Parser12306
import team.shiro.railwidget.sync.CloudMailClient
import team.shiro.railwidget.sync.StandardImapClient
import team.shiro.railwidget.sync.UpdateChecker
import team.shiro.railwidget.sync.UpdateInfo
import team.shiro.railwidget.ui.components.FastImportDialog
import team.shiro.railwidget.ui.components.HeroTripCard
import team.shiro.railwidget.ui.components.HistoryTripItem
import team.shiro.railwidget.ui.components.SettingsDialog
import team.shiro.railwidget.ui.components.UpdateDialog
import team.shiro.railwidget.ui.theme.RailWidgetTheme
import team.shiro.railwidget.widget.TripWidget2x2Receiver
import team.shiro.railwidget.widget.TripWidget4x2Receiver
import team.shiro.railwidget.widget.TripWidget4x4Receiver
import team.shiro.railwidget.widget.TripWidgetRenderer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RailWidgetTheme {
                MainScreen()
            }
        }
    }

    private fun requestPinWidget(receiverClass: Class<*>) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val componentName = ComponentName(this, receiverClass)
            appWidgetManager.requestPinAppWidget(componentName, null, null)
        } else {
            Toast.makeText(this, "当前系统不支持应用内直接添加，请长按桌面空白处手动添加小部件", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val context = this
        val db = remember { TripDatabaseHelper.getInstance(context) }
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var trips by remember { mutableStateOf(emptyList<Trip>()) }
        var isSyncing by remember { mutableStateOf(false) }
        var showImportDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }

        // Update states
        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var isCheckingUpdate by remember { mutableStateOf(false) }
        var isDownloadingUpdate by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableIntStateOf(0) }

        fun reloadTrips() {
            trips = db.getAllTrips()
            TripWidgetRenderer.updateAllWidgets(context)
        }

        LaunchedEffect(Unit) {
            reloadTrips()
            // Auto sync from Cloud Mail if database is empty on start and credentials are set
            if (trips.isEmpty()) {
                val mailUrl = db.getSetting("cloudmail_url", "")
                val mailUser = db.getSetting("cloudmail_user", "")
                val mailPass = db.getSetting("cloudmail_pass", "")
                if (mailUrl.isNotBlank() && mailUser.isNotBlank() && mailPass.isNotBlank()) {
                    isSyncing = true
                    withContext(Dispatchers.IO) {
                        CloudMailClient.sync(mailUrl, mailUser, mailPass, db)
                    }
                    isSyncing = false
                    reloadTrips()
                }
            }
        }

        fun triggerMailSync() {
            val mailUrl = db.getSetting("cloudmail_url", "")
            val mailUser = db.getSetting("cloudmail_user", "")
            val mailPass = db.getSetting("cloudmail_pass", "")

            if (mailUrl.isBlank() || mailUser.isBlank() || mailPass.isBlank()) {
                showSettingsDialog = true
                scope.launch {
                    snackbarHostState.showSnackbar("请先在设置中填写邮箱账号与密码")
                }
                return
            }

            scope.launch {
                isSyncing = true
                val result = withContext(Dispatchers.IO) {
                    CloudMailClient.sync(mailUrl, mailUser, mailPass, db)
                }
                isSyncing = false
                reloadTrips()

                if (result.isSuccess) {
                    val count = result.getOrNull()?.size ?: 0
                    snackbarHostState.showSnackbar("同步成功！已自动拉取并缓存 $count 条车票信息")
                } else {
                    snackbarHostState.showSnackbar("同步失败: ${result.exceptionOrNull()?.message}")
                }
            }
        }

        fun triggerCheckUpdate() {
            scope.launch {
                isCheckingUpdate = true
                val result = withContext(Dispatchers.IO) {
                    UpdateChecker.checkUpdate()
                }
                isCheckingUpdate = false

                if (result.isSuccess) {
                    val info = result.getOrNull()
                    if (info != null && info.hasUpdate) {
                        updateInfo = info
                    } else {
                        snackbarHostState.showSnackbar("当前已是最新版本 (v1.0.0)")
                    }
                } else {
                    snackbarHostState.showSnackbar("检查更新失败: ${result.exceptionOrNull()?.message}")
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "铁路行程助手",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "12306 智能桌面小部件 · MIUI 14+",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        // Check update button
                        IconButton(onClick = { triggerCheckUpdate() }) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = "检查更新")
                            }
                        }

                        // Mail sync button
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { triggerMailSync() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "同步邮件"
                                )
                            }
                        }

                        // Settings button
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置"
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showImportDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("智能识别导入") }
                )
            }
        ) { paddingValues ->
            val upcomingTrips = trips.filter { !it.isArchived }
            val archivedTrips = trips.filter { it.isArchived }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                // Empty State
                if (trips.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🚄", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "暂无出行行程",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "支持通过 Cloud Mail 或通用 IMAP 自动同步，也可直接粘贴 12306 短信/邮件解析导入。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Row {
                                    Button(onClick = { triggerMailSync() }) {
                                        Icon(Icons.Default.Email, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("从邮箱拉取")
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    OutlinedButton(onClick = { showImportDialog = true }) {
                                        Text("手动粘贴")
                                    }
                                }
                            }
                        }
                    }
                }

                // Upcoming Trips Section
                if (upcomingTrips.isNotEmpty()) {
                    item {
                        Text(
                            text = "当前即将出行",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )
                        HeroTripCard(
                            trip = upcomingTrips.first(),
                            onArchive = {
                                db.setArchived(upcomingTrips.first().orderNo, true)
                                reloadTrips()
                            },
                            onDelete = {
                                db.deleteTrip(upcomingTrips.first().orderNo)
                                reloadTrips()
                            }
                        )
                    }

                    // Remaining Upcoming Trips
                    if (upcomingTrips.size > 1) {
                        item {
                            Text(
                                text = "后续行程 (${upcomingTrips.size - 1})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(upcomingTrips.drop(1), key = { it.orderNo }) { trip ->
                            HistoryTripItem(
                                trip = trip,
                                onDelete = {
                                    db.deleteTrip(trip.orderNo)
                                    reloadTrips()
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }

                // Widget Quick Pin Section (One-click add to home screen for MIUI 14+)
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "一键添加到桌面小部件",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "点击下方按钮可直接由系统弹窗添加小部件至桌面，适配 MIUI 14 / HyperOS：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { requestPinWidget(TripWidget2x2Receiver::class.java) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("添加 2×2 磁贴", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { requestPinWidget(TripWidget4x2Receiver::class.java) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("添加 4×2 横卡", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { requestPinWidget(TripWidget4x4Receiver::class.java) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("添加 4×4 全景看板（含途经时刻表与刷新按钮）", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "💡 MIUI 14 / 小米澎湃 OS 手动添加指引：\n" +
                                        "1. 桌面双指捏合 ➔ 点击「添加小部件」；\n" +
                                        "2. 滑动至最底端点击「支持全部应用」或「安卓传统小部件」；\n" +
                                        "3. 找到「铁路行程助手」即可挑选不同尺寸。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Archived / Past Trips Section
                if (archivedTrips.isNotEmpty()) {
                    item {
                        Text(
                            text = "历史归档行程 (${archivedTrips.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                        )
                    }
                    items(archivedTrips, key = { it.orderNo }) { trip ->
                        HistoryTripItem(
                            trip = trip,
                            onDelete = {
                                db.deleteTrip(trip.orderNo)
                                reloadTrips()
                            }
                        )
                        HorizontalDivider()
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // Fast Import Dialog
        if (showImportDialog) {
            FastImportDialog(
                onDismiss = { showImportDialog = false },
                onConfirmImport = { rawText ->
                    showImportDialog = false
                    scope.launch {
                        val parsed = withContext(Dispatchers.IO) {
                            val emailTrips = Parser12306.parseEmail(rawText)
                            if (emailTrips.isNotEmpty()) {
                                emailTrips.map { RailwayApiService.enrichTrip(it) }
                            } else {
                                val smsTrip = Parser12306.parseSms(rawText)
                                if (smsTrip != null) listOf(RailwayApiService.enrichTrip(smsTrip)) else emptyList()
                            }
                        }

                        if (parsed.isNotEmpty()) {
                            parsed.forEach { db.insertOrUpdateTrip(it) }
                            reloadTrips()
                            snackbarHostState.showSnackbar("成功导入并缓存 ${parsed.size} 条车票信息！")
                        } else {
                            snackbarHostState.showSnackbar("未能识别出 12306 车票信息，请检查文本。")
                        }
                    }
                }
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                initialCloudUrl = db.getSetting("cloudmail_url", "https://mail.example.com"),
                initialCloudUser = db.getSetting("cloudmail_user", ""),
                initialCloudPass = db.getSetting("cloudmail_pass", ""),
                initialImapHost = db.getSetting("imap_host", "imap.qq.com"),
                initialImapPort = db.getSetting("imap_port", "993"),
                initialImapUser = db.getSetting("imap_user", ""),
                initialImapPass = db.getSetting("imap_pass", ""),
                initialImapSsl = db.getSetting("imap_ssl", "true") == "true",
                onDismiss = { showSettingsDialog = false },
                onSaveAndSync = { mode, cloudUrl, cloudUser, cloudPass, imapHost, imapPort, imapUser, imapPass, imapSsl ->
                    showSettingsDialog = false
                    db.setSetting("cloudmail_url", cloudUrl)
                    db.setSetting("cloudmail_user", cloudUser)
                    db.setSetting("cloudmail_pass", cloudPass)
                    db.setSetting("imap_host", imapHost)
                    db.setSetting("imap_port", imapPort)
                    db.setSetting("imap_user", imapUser)
                    db.setSetting("imap_pass", imapPass)
                    db.setSetting("imap_ssl", imapSsl.toString())

                    scope.launch {
                        isSyncing = true
                        val res = withContext(Dispatchers.IO) {
                            if (mode == 0) {
                                CloudMailClient.sync(cloudUrl, cloudUser, cloudPass, db)
                            } else {
                                val portInt = imapPort.toIntOrNull() ?: 993
                                StandardImapClient.sync(imapHost, portInt, imapUser, imapPass, imapSsl, db)
                            }
                        }
                        isSyncing = false
                        reloadTrips()

                        if (res.isSuccess) {
                            snackbarHostState.showSnackbar("同步成功！已自动拉取并缓存 ${res.getOrNull()?.size ?: 0} 条行程数据")
                        } else {
                            snackbarHostState.showSnackbar("同步失败: ${res.exceptionOrNull()?.message}")
                        }
                    }
                }
            )
        }

        // Update Dialog
        updateInfo?.let { info ->
            UpdateDialog(
                updateInfo = info,
                isDownloading = isDownloadingUpdate,
                downloadProgress = downloadProgress,
                onDismiss = { updateInfo = null },
                onDownloadAndInstall = {
                    if (info.downloadUrl.isNotBlank()) {
                        scope.launch {
                            isDownloadingUpdate = true
                            downloadProgress = 0
                            val res = withContext(Dispatchers.IO) {
                                UpdateChecker.downloadAndInstall(context, info.downloadUrl) { progress ->
                                    downloadProgress = progress
                                }
                            }
                            isDownloadingUpdate = false
                            if (res.isFailure) {
                                snackbarHostState.showSnackbar("安装包下载失败: ${res.exceptionOrNull()?.message}")
                            } else {
                                updateInfo = null
                            }
                        }
                    } else {
                        UpdateChecker.openBrowserDownload(context, info.htmlUrl)
                        updateInfo = null
                    }
                },
                onOpenBrowser = {
                    UpdateChecker.openBrowserDownload(context, info.htmlUrl)
                    updateInfo = null
                }
            )
        }
    }
}
