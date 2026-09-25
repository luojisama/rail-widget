package team.shiro.railwidget.ui

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.shiro.railwidget.data.api.RailwayApiService
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripStage
import team.shiro.railwidget.data.parser.Parser12306
import team.shiro.railwidget.sync.CloudMailClient
import team.shiro.railwidget.sync.SmsInboxReader
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

    private var pendingAction: String? = null
    var onPinBlocked: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAction = intent?.action
        setContent {
            RailWidgetTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutAction(intent.action)
    }

    fun requestPinWidget(receiverClass: Class<*>) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val componentName = ComponentName(this, receiverClass)
            val callbackIntent = Intent(this, MainActivity::class.java).apply {
                action = "team.shiro.railwidget.action.PIN_RESULT"
            }
            val successCallback = PendingIntent.getActivity(
                this,
                1002,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val isAccepted = appWidgetManager.requestPinAppWidget(componentName, null, successCallback)
            if (!isAccepted) {
                onPinBlocked?.invoke()
            }
        } else {
            Toast.makeText(this, "当前系统不支持一键添加，请双指捏合桌面手动添加小部件", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleShortcutAction(action: String?) {
        when (action) {
            "team.shiro.railwidget.action.PIN_4X2" -> requestPinWidget(TripWidget4x2Receiver::class.java)
            "team.shiro.railwidget.action.PIN_2X2" -> requestPinWidget(TripWidget2x2Receiver::class.java)
        }
    }

    companion object {
        fun openAppPermissions(context: Context) {
            try {
                // MIUI / HyperOS direct permissions editor
                val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    putExtra("extra_pkgname", context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(miuiIntent)
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
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
        var isSyncingMail by remember { mutableStateOf(false) }
        var isReadingSms by remember { mutableStateOf(false) }
        var selectedTabIndex by remember { mutableIntStateOf(0) }

        var showImportDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showPinBlockedDialog by remember { mutableStateOf(false) }

        // Bind pin blocked callback
        onPinBlocked = {
            showPinBlockedDialog = true
        }

        // Update states
        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var isCheckingUpdate by remember { mutableStateOf(false) }
        var isDownloadingUpdate by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableIntStateOf(0) }

        fun reloadTrips() {
            trips = db.getAllTrips()
            TripWidgetRenderer.updateAllWidgets(context)
        }

        // 短信权限请求启动器
        val smsPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                scope.launch {
                    isReadingSms = true
                    val imported = withContext(Dispatchers.IO) {
                        SmsInboxReader.syncFromSmsInbox(context)
                    }
                    isReadingSms = false
                    reloadTrips()
                    if (imported.isNotEmpty()) {
                        snackbarHostState.showSnackbar("已成功读取并导入 ${imported.size} 条 12306 短信车票")
                    } else {
                        snackbarHostState.showSnackbar("未在收件箱中发现新的 12306 短信")
                    }
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("需要读取短信权限以自动同步 12306 车票短信")
                }
            }
        }

        fun triggerSmsSync() {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                scope.launch {
                    isReadingSms = true
                    val imported = withContext(Dispatchers.IO) {
                        SmsInboxReader.syncFromSmsInbox(context)
                    }
                    isReadingSms = false
                    reloadTrips()
                    if (imported.isNotEmpty()) {
                        snackbarHostState.showSnackbar("已成功读取并导入 ${imported.size} 条 12306 短信车票")
                    } else {
                        snackbarHostState.showSnackbar("未在收件箱中发现新的 12306 短信")
                    }
                }
            } else {
                smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }

        LaunchedEffect(Unit) {
            reloadTrips()
            when (pendingAction) {
                "team.shiro.railwidget.action.PIN_4X2" -> {
                    requestPinWidget(TripWidget4x2Receiver::class.java)
                    pendingAction = null
                }
                "team.shiro.railwidget.action.PIN_2X2" -> {
                    requestPinWidget(TripWidget2x2Receiver::class.java)
                    pendingAction = null
                }
                "team.shiro.railwidget.action.SYNC_SMS" -> {
                    triggerSmsSync()
                    pendingAction = null
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
                isSyncingMail = true
                val result = withContext(Dispatchers.IO) {
                    CloudMailClient.sync(mailUrl, mailUser, mailPass, db)
                }
                isSyncingMail = false
                reloadTrips()

                if (result.isSuccess) {
                    val count = result.getOrNull()?.size ?: 0
                    snackbarHostState.showSnackbar("同步完成，已获取 $count 条车票信息")
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
                        snackbarHostState.showSnackbar("当前已是最新版本 (v1.0.1)")
                    }
                } else {
                    snackbarHostState.showSnackbar("检查更新失败: ${result.exceptionOrNull()?.message}")
                }
            }
        }

        // 行程三分类
        val now = System.currentTimeMillis()
        val upcomingTrips = trips.filter { it.getStage(now) == TripStage.UPCOMING && !it.isArchived }
            .sortedWith(compareBy({ it.departureDate }, { it.departureTime }))
        val inTransitTrips = trips.filter { it.getStage(now) == TripStage.IN_TRANSIT && !it.isArchived }
            .sortedWith(compareBy({ it.departureDate }, { it.departureTime }))
        val completedTrips = trips.filter { it.getStage(now) == TripStage.COMPLETED || it.isArchived }
            .sortedWith(compareByDescending<Trip> { it.departureDate }.thenByDescending { it.departureTime })

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "铁行卡片",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "铁路行程与桌面小组件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        actions = {
                            // 读取短信按钮
                            if (isReadingSms) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp).padding(end = 8.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = { triggerSmsSync() }) {
                                    Icon(
                                        imageVector = Icons.Default.Sms,
                                        contentDescription = "读取 12306 短信"
                                    )
                                }
                            }

                            // 邮箱同步按钮
                            if (isSyncingMail) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp).padding(end = 8.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = { triggerMailSync() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "同步邮箱"
                                    )
                                }
                            }

                            // 检查更新按钮
                            IconButton(onClick = { triggerCheckUpdate() }) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = "检查更新")
                                }
                            }

                            // 设置按钮
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "设置"
                                )
                            }
                        }
                    )

                    // 三阶段选项卡：未出行、在途中、已结束
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("未出行")
                                    if (upcomingTrips.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Badge { Text("${upcomingTrips.size}") }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("在途中")
                                    if (inTransitTrips.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                            Text("${inTransitTrips.size}")
                                        }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("已结束")
                                    if (completedTrips.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                            Text("${completedTrips.size}")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showImportDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("导入行程") }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> {
                        // TAB 0: 未出行
                        if (upcomingTrips.isEmpty()) {
                            item {
                                EmptyUpcomingCard(
                                    onReadSms = { triggerSmsSync() },
                                    onSyncMail = { triggerMailSync() },
                                    onManualPaste = { showImportDialog = true }
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = "即将出发",
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

                            if (upcomingTrips.size > 1) {
                                item {
                                    Text(
                                        text = "后续车次 (${upcomingTrips.size - 1})",
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

                        // 小部件一键添加引导卡片
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            WidgetPinGuideCard(
                                onPin2x2 = { requestPinWidget(TripWidget2x2Receiver::class.java) },
                                onPin4x2 = { requestPinWidget(TripWidget4x2Receiver::class.java) },
                                onPin4x4 = { requestPinWidget(TripWidget4x4Receiver::class.java) }
                            )
                        }
                    }

                    1 -> {
                        // TAB 1: 在途中
                        if (inTransitTrips.isEmpty()) {
                            item {
                                InTransitEmptyCard()
                            }
                        } else {
                            item {
                                Text(
                                    text = "当前运行中 (${inTransitTrips.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                                )
                            }
                            items(inTransitTrips, key = { it.orderNo }) { trip ->
                                HeroTripCard(
                                    trip = trip,
                                    onArchive = {
                                        db.setArchived(trip.orderNo, true)
                                        reloadTrips()
                                    },
                                    onDelete = {
                                        db.deleteTrip(trip.orderNo)
                                        reloadTrips()
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: 已结束
                        if (completedTrips.isEmpty()) {
                            item {
                                CompletedEmptyCard()
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "历史行程 (${completedTrips.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            items(completedTrips, key = { it.orderNo }) { trip ->
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
                }

                item {
                    Spacer(modifier = Modifier.height(88.dp))
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
                            snackbarHostState.showSnackbar("已导入 ${parsed.size} 条车票信息")
                        } else {
                            snackbarHostState.showSnackbar("未能识别出有效车票，请检查内容。")
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
                        isSyncingMail = true
                        val res = withContext(Dispatchers.IO) {
                            if (mode == 0) {
                                CloudMailClient.sync(cloudUrl, cloudUser, cloudPass, db)
                            } else {
                                val portInt = imapPort.toIntOrNull() ?: 993
                                StandardImapClient.sync(imapHost, portInt, imapUser, imapPass, imapSsl, db)
                            }
                        }
                        isSyncingMail = false
                        reloadTrips()

                        if (res.isSuccess) {
                            snackbarHostState.showSnackbar("同步成功！已获取 ${res.getOrNull()?.size ?: 0} 条行程数据")
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
                onDownloadAndInstall = { useMirror ->
                    if (info.downloadUrl.isNotBlank()) {
                        scope.launch {
                            isDownloadingUpdate = true
                            downloadProgress = 0
                            val res = withContext(Dispatchers.IO) {
                                UpdateChecker.downloadAndInstall(context, info.downloadUrl, useMirror) { progress ->
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
                        UpdateChecker.openBrowserDownload(context, info.htmlUrl, useMirror)
                        updateInfo = null
                    }
                },
                onOpenBrowser = { useMirror ->
                    UpdateChecker.openBrowserDownload(context, info.htmlUrl, useMirror)
                    updateInfo = null
                }
            )
        }

        // MIUI/Android Pin Widget Blocked Dialog
        if (showPinBlockedDialog) {
            AlertDialog(
                onDismissRequest = { showPinBlockedDialog = false },
                title = { Text("小部件添加被系统拦截", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "小米 MIUI 14 / 澎湃 OS 默认限制了第三方应用的「桌面快捷方式」权限。\n\n" +
                                "解决方案：\n" +
                                "1. 点击下方按钮进入权限管理，找到「桌面快捷方式」并选择【始终允许】；\n" +
                                "2. 返回应用重新点击添加；\n" +
                                "3. 或双指捏合手机桌面 ➔ 滑到底部「安卓小部件」手动拖拽。",
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showPinBlockedDialog = false
                        openAppPermissions(context)
                    }) {
                        Text("前往开启权限")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinBlockedDialog = false }) {
                        Text("知道了")
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyUpcomingCard(
    onReadSms: () -> Unit,
    onSyncMail: () -> Unit,
    onManualPaste: () -> Unit
) {
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
            Text("🚄", fontSize = 44.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "暂无待出行车次",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "可一键扫描本地 12306 购票短信，或连接邮箱自动拉取行程。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row {
                Button(onClick = onReadSms) {
                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("读取短信")
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(onClick = onSyncMail) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("邮箱同步")
                }
            }
        }
    }
}

@Composable
private fun InTransitEmptyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🛤️", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "当前无在途列车",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "当列车发车后，此处将实时展示行驶进度、预计到站时刻及沿途停靠站。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompletedEmptyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📋", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "暂无已完成行程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "已过到站时间的车次将自动整理归档至此处。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WidgetPinGuideCard(
    onPin2x2: () -> Unit,
    onPin4x2: () -> Unit,
    onPin4x4: () -> Unit
) {
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
                    text = "一键添加到桌面小组件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "点击下方按钮直接调起系统添加弹窗（完美适配 MIUI 14 / HyperOS）：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPin2x2,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("添加 2×2 磁贴", fontSize = 12.sp)
                }
                Button(
                    onClick = onPin4x2,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("添加 4×2 横卡", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onPin4x4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("添加 4×4 全景看板（含途经时刻表与刷新按钮）", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            Column {
                Text(
                    text = "💡 小米 MIUI 14 / 澎湃 OS 提示：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "长按桌面图标提示「该应用此版本没有小部件」，是因为该入口专供小米商店云端过审卡片。\n\n" +
                            "添加本应用小部件的 2 种快捷途径：\n" +
                            "• 途径 ①：在桌面长按本 App 图标，点击弹出的快捷菜单「添加横卡」或「添加磁贴」；\n" +
                            "• 途径 ②：点击下方按钮开启权限后，点击上方「添加 4×2 横卡」，由系统弹窗直接一键添加；\n" +
                            "• 途径 ③：桌面双指捏合 ➔ 添加小部件 ➔ 滑动到底部点击「安卓小部件」即可拖拽。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        MainActivity.openAppPermissions(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("前往开启「桌面快捷方式」权限 ↗", fontSize = 12.sp)
                }
            }
        }
    }
}
