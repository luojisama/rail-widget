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

/**
 * 灵动胶囊 / 桌面灵动岛前台生命周期服务
 * 负责在系统桌面和其他应用上层维持实时车次胶囊，并在发车/到达后自动安全销毁
 */
class LiveIslandService : Service() {

    private var islandView: LiveIslandView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentTrip: Trip? = null

    private val tickerRunnable = object : Runnable {
        override fun run() {
            refreshTripState()
            handler.postDelayed(this, 30_000) // 每 30 秒平滑刷新倒计时与检票状态
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val orderNo = intent?.getStringExtra(EXTRA_ORDER_NO)
                loadTrip(orderNo)

                val trip = currentTrip
                if (trip != null) {
                    val notification = buildForegroundNotification(trip)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }

                    if (IslandPermissionHelper.hasOverlayPermission(this)) {
                        if (islandView == null) {
                            islandView = LiveIslandView(this)
                            islandView?.attach()
                        }
                        islandView?.updateTrip(trip)
                    }

                    handler.removeCallbacks(tickerRunnable)
                    handler.post(tickerRunnable)
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
            db.getTrip(orderNo) ?: db.getLatestUpcomingTrip()
        } else {
            db.getLatestUpcomingTrip()
        }
        currentTrip = trip
        currentOrderNo = trip?.orderNo
    }

    private fun refreshTripState() {
        val trip = currentTrip ?: return
        val db = TripDatabaseHelper.getInstance(this)
        val refreshed = db.getTrip(trip.orderNo) ?: trip
        currentTrip = refreshed
        currentOrderNo = refreshed.orderNo

        val stage = refreshed.getStage()
        islandView?.updateTrip(refreshed)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildForegroundNotification(refreshed))
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚄 ${trip.trainCode} · $gateText")
            .setContentText("${trip.departureStation} ➔ ${trip.arrivalStation} | $seatText")
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
        handler.removeCallbacks(tickerRunnable)
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

        fun start(context: Context, orderNo: String? = null) {
            val intent = Intent(context, LiveIslandService::class.java).apply {
                action = ACTION_START
                if (!orderNo.isNullOrBlank()) {
                    putExtra(EXTRA_ORDER_NO, orderNo)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveIslandService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
