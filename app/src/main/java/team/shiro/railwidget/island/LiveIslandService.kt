package team.shiro.railwidget.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import team.shiro.railwidget.R
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripStage
import team.shiro.railwidget.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 灵动胶囊 / 桌面灵动岛前台生命周期服务
 * 负责在系统桌面和其他应用上层维持实时车次胶囊，并在发车/到达后自动安全销毁
 */
class LiveIslandService : Service() {

    private var islandView: LiveIslandView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentTrip: Trip? = null
    private var lastRefreshRequest = 0L

    private val tickerRunnable = Runnable {
        refreshTripState()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                TripDatabaseHelper.getInstance(this).setSetting("island_dismissed_order", currentOrderNo.orEmpty())
                TripDatabaseHelper.getInstance(this).setSetting("island_active_order", "")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val orderNo = intent?.getStringExtra(EXTRA_ORDER_NO) ?: intent?.getStringExtra("order_no")
                val db = TripDatabaseHelper.getInstance(this)
                val restoredOrder = orderNo ?: currentOrderNo ?: db.getSetting("island_active_order").takeIf { it.isNotBlank() }
                if (intent == null && restoredOrder == null) { stopSelf(); return START_NOT_STICKY }
                loadTrip(restoredOrder)
                if (action == ACTION_START && intent != null) db.setSetting("island_automatic", if (intent.getBooleanExtra("automatic", false)) "1" else "0")

                val trip = currentTrip?.takeUnless { it.isArchived }
                if (trip != null) {
                    try {
                        val notification = buildForegroundNotification(trip)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        } else {
                            startForeground(NOTIFICATION_ID, notification)
                        }

                        isServiceRunning = true
                        db.setSetting("island_active_order", trip.orderNo)
                        db.setSetting("island_start_status", "灵动岛运行中")
                        if (IslandPermissionHelper.hasOverlayPermission(this)) {
                            if (islandView == null) {
                                islandView = LiveIslandView(this)
                                check(islandView?.attach() == true) { "无法显示悬浮窗" }
                            }
                            islandView?.updateTrip(trip)
                        }

                    } catch (error: RuntimeException) {
                        db.setSetting("island_start_status", "灵动岛启动失败：${error.javaClass.simpleName}")
                        android.util.Log.w("LiveIslandService", "Unable to attach island", error)
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    refreshTripState()
                } else {
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private fun loadTrip(orderNo: String?) {
        val db = TripDatabaseHelper.getInstance(this)
        val trip = if (!orderNo.isNullOrBlank()) {
            db.getTrip(orderNo)
        } else {
            db.getLatestUpcomingTrip()
        }
        currentTrip = trip
        currentOrderNo = trip?.orderNo
    }

    private fun refreshTripState() {
        val trip = currentTrip ?: run {
            stopSelf()
            return
        }
        val db = TripDatabaseHelper.getInstance(this)
        val refreshed = db.getTrip(trip.orderNo)?.takeUnless { it.isArchived } ?: run { stopSelf(); return }
        currentTrip = refreshed
        currentOrderNo = refreshed.orderNo

        islandView?.updateTrip(refreshed)
        notifyTrip(refreshed)

        // 统一计算并调度下一次探测或倒计时刷新
        scheduleNextCheck(refreshed)
    }

    /**
     * 智能时间调度核心逻辑：
     * 严格按 Asia/Shanghai 时区计算：
     * 1. 若当前日期早于出发日期（未来车次）：
     *    计算距离出发日当天 00:00:05（上海时间）的精确延迟，一次性深度休眠，期间 0 轮询、0 网络请求、0 耗电！
     * 2. 若当前已到达出发日当天：
     *    12306 车站大屏在出发日 00:00 之后即可查到全天所有车次检票口；
     *    - 若尚未拿到检票口，立即异步拉取并缓存至 SQLite，若暂未出结果则以 15 分钟低频重试；
     *    - 若已成功拿到检票口，以 60 秒平滑刷新发车倒计时与运行状态。
     * 3. 若行程已结束（COMPLETED），5 分钟低频状态检查或安全结束。
     */
    private fun scheduleNextCheck(trip: Trip) {
        handler.removeCallbacks(tickerRunnable)

        val shanghaiTz = TimeZone.getTimeZone("Asia/Shanghai")
        val nowCal = Calendar.getInstance(shanghaiTz)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = shanghaiTz
        }
        val todayStr = sdf.format(nowCal.time)
        val depDate = trip.departureDate.trim()

        // 1. 未来车次：深度休眠至发车日当天 00:00:05 (上海时间)
        if (depDate.isNotBlank() && depDate > todayStr) {
            try {
                val targetCal = Calendar.getInstance(shanghaiTz).apply {
                    time = sdf.parse(depDate) ?: nowCal.time
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 5)
                    set(Calendar.MILLISECOND, 0)
                }
                val delay = targetCal.timeInMillis - nowCal.timeInMillis
                handler.postDelayed(tickerRunnable, minOf(60_000L, delay.coerceAtLeast(1000L)))
            } catch (_: Exception) {
                handler.postDelayed(tickerRunnable, 3600_000L) // 兜底 1 小时
            }
            return
        }

        // 2. 行程到达与中转换乘智能接力 / 自动安全关闭
        val stage = trip.getStage(nowCal.timeInMillis)
        if (stage == TripStage.COMPLETED) {
            val db = TripDatabaseHelper.getInstance(this)
            val isTransferEnabled = LiveIslandScheduler.isTransferHandoverEnabled(this)
            val nextTransfer = if (isTransferEnabled) db.findNextTransferTrip(trip) else null

            if (nextTransfer != null) {
                // 智能中转换乘接力：不关闭灵动岛，平滑无缝切换至下一程！
                currentTrip = nextTransfer
                currentOrderNo = nextTransfer.orderNo
                islandView?.updateTrip(nextTransfer)
                notifyTrip(nextTransfer)

                // 同步广播通知桌面小组件局部更新
                val widgetIntent = Intent("team.shiro.railwidget.action.REFRESH_WIDGET").apply {
                    setPackage(packageName)
                }
                sendBroadcast(widgetIntent)

                // 若第二程尚未获取到检票口，立即触发异步探测
                if (nextTransfer.getCleanTicketGate().isBlank() || nextTransfer.getCleanTicketGate() == "暂无") {
                    team.shiro.railwidget.sync.TripRefresh.enqueue(this)
                }

                // 立即按第二程发车时间开始下一阶段调度
                scheduleNextCheck(nextTransfer)
                return
            }

            // 无中转换乘行程：检查是否开启到站后自动关闭
            val isAutoCloseEnabled = LiveIslandScheduler.isAutoCloseOnArrivalEnabled(this)
            if (isAutoCloseEnabled) {
                val arrMillis = trip.getArrivalTimeMillis()
                val elapsedSinceArrival = nowCal.timeInMillis - arrMillis
                val bufferMillis = 15 * 60 * 1000L // 15 分钟出站缓冲时间
                if (elapsedSinceArrival in 0 until bufferMillis) {
                    // 仅当处于自然到站的 15 分钟出站缓冲期内时，在剩余缓冲倒计时满后自动安全退出
                    val remaining = (bufferMillis - elapsedSinceArrival).coerceAtLeast(5000L)
                    handler.postDelayed({
                        if (currentOrderNo == trip.orderNo) {
                            stopSelf()
                        }
                    }, remaining)
                    return
                } else if (elapsedSinceArrival >= bufferMillis && db.getSetting("island_automatic") == "1") {
                    stopSelf()
                    return
                } else if (elapsedSinceArrival >= bufferMillis) {
                    // 启动时就已经到达超过 15 分钟（用户手动开启查看已结束/历史车票）：
                    // 严禁在此处立即 stopSelf() 闪退秒杀！正常维持已到达状态展示，由用户随时手动关闭
                    handler.postDelayed(tickerRunnable, 3600_000L)
                    return
                }
            } else {
                // 用户关闭了到站自动关闭：以 5 分钟低频维持已到达状态
                handler.postDelayed(tickerRunnable, 5 * 60 * 1000L)
                return
            }
        }

        // Local countdown refresh is independent of online fetch success or gate availability.
        if (System.currentTimeMillis() - lastRefreshRequest >= 300_000L) {
            lastRefreshRequest = System.currentTimeMillis()
            team.shiro.railwidget.sync.TripRefresh.enqueue(this)
        }
        handler.postDelayed(tickerRunnable, 60_000L)
    }

    private fun notifyTrip(trip: Trip) {
        if (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildForegroundNotification(trip))
        }
    }

    private fun buildForegroundNotification(trip: Trip): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_ORDER_NO", trip.orderNo)
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveIslandService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val gate = trip.getCleanTicketGate()
        val gateText = if (gate.isNotBlank() && gate != "暂无") "检票口 $gate" else "检票口待公布"
        val seatText = if (trip.carriage.isNotBlank() || trip.seat.isNotBlank()) "${trip.carriage} ${trip.seat}" else "席位待出"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setContentTitle("${trip.trainCode} · $gateText")
            .setContentText(trip.currentPosition()?.label(trip) ?: "${trip.departureStation} → ${trip.arrivalStation} | $seatText")
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "关闭灵动岛", pendingStop)

        // 兼容 HyperOS 焦点通知拓展参数
        val focusParam = """
            {
                "param_v2": {
                    "business": "TRAIN_TRIP",
                    "orderId": "${trip.orderNo}",
                    "param_island": {
                        "islandProperty": 1
                    }
                }
            }
        """.trimIndent()
        builder.extras.putString("miui.focus.param", focusParam)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "铁行灵动胶囊",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在桌面顶部显示实时列车发车倒计时与检票口胶囊"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        currentOrderNo = null
        handler.removeCallbacksAndMessages(null)
        islandView?.detach()
        islandView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "channel_live_island"
        private const val NOTIFICATION_ID = 20260928

        const val ACTION_START = "team.shiro.railwidget.action.ISLAND_START"
        const val ACTION_UPDATE = "team.shiro.railwidget.action.ISLAND_UPDATE"
        const val ACTION_STOP = "team.shiro.railwidget.action.ISLAND_STOP"
        const val EXTRA_ORDER_NO = "EXTRA_ORDER_NO"

        var isServiceRunning = false
            private set
        var currentOrderNo: String? = null
            private set

        private var lastStartAt = 0L

        fun start(context: Context, orderNo: String? = null, automatic: Boolean = false): Boolean {
            val now = System.currentTimeMillis()
            if (!isServiceRunning && now - lastStartAt < 3000L) return true
            val db = TripDatabaseHelper.getInstance(context)
            if (!automatic) db.setSetting("island_dismissed_order", "")
            val intent = Intent(context, LiveIslandService::class.java).apply {
                action = ACTION_START
                putExtra("automatic", automatic)
                if (!orderNo.isNullOrBlank()) putExtra(EXTRA_ORDER_NO, orderNo)
            }
            return try {
                context.startForegroundService(intent)
                lastStartAt = now
                true
            } catch (error: RuntimeException) {
                db.setSetting("island_start_status", "启动受限：${error.javaClass.simpleName}，请检查后台及自启动权限")
                android.util.Log.w("LiveIslandService", "Unable to start island", error)
                false
            }
        }

        fun update(context: Context, orderNo: String? = null) {
            if (!isServiceRunning) return
            val intent = Intent(context, LiveIslandService::class.java).apply {
                action = ACTION_UPDATE
                if (!orderNo.isNullOrBlank()) {
                    putExtra(EXTRA_ORDER_NO, orderNo)
                }
            }
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val db = TripDatabaseHelper.getInstance(context)
            db.setSetting("island_dismissed_order", currentOrderNo ?: db.getSetting("island_active_order"))
            db.setSetting("island_active_order", "")
            context.stopService(Intent(context, LiveIslandService::class.java))
        }
    }
}
