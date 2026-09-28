package team.shiro.railwidget.island

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.TripStage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 灵动胶囊 / 灵动岛智能自动化拉起与生命周期调度器
 * 负责依据发车时间、用户设定的提前拉起窗口（如提前1小时），
 * 通过 Android 系统底层 AlarmManager 精准定时并在免打扰/灭屏时准时唤醒拉起。
 */
object LiveIslandScheduler {

    const val KEY_AUTO_LAUNCH_ENABLED = "island_auto_launch_enabled"
    const val KEY_AUTO_LEAD_MINUTES = "island_auto_lead_minutes"
    const val KEY_AUTO_TRANSFER_HANDOVER = "island_auto_transfer_handover"
    const val KEY_AUTO_CLOSE_ON_ARRIVAL = "island_auto_close_on_arrival"

    // 提前分钟选项：15分、30分、60分(1h，默认)、120分(2h)、0(当天00:00)
    const val DEFAULT_LEAD_MINUTES = 60L
    private const val ALARM_REQUEST_CODE = 20260929

    fun isAutoLaunchEnabled(context: Context): Boolean {
        val db = TripDatabaseHelper.getInstance(context)
        return db.getSetting(KEY_AUTO_LAUNCH_ENABLED, "1") == "1"
    }

    fun getAutoLeadMinutes(context: Context): Long {
        val db = TripDatabaseHelper.getInstance(context)
        return db.getSetting(KEY_AUTO_LEAD_MINUTES, "$DEFAULT_LEAD_MINUTES").toLongOrNull() ?: DEFAULT_LEAD_MINUTES
    }

    fun isTransferHandoverEnabled(context: Context): Boolean {
        val db = TripDatabaseHelper.getInstance(context)
        return db.getSetting(KEY_AUTO_TRANSFER_HANDOVER, "1") == "1"
    }

    fun isAutoCloseOnArrivalEnabled(context: Context): Boolean {
        val db = TripDatabaseHelper.getInstance(context)
        return db.getSetting(KEY_AUTO_CLOSE_ON_ARRIVAL, "1") == "1"
    }

    /**
     * 重新评估并挂载发车前自动拉起灵动岛闹钟
     */
    fun scheduleAutoLaunch(context: Context) {
        val db = TripDatabaseHelper.getInstance(context)
        val isEnabled = isAutoLaunchEnabled(context)
        if (!isEnabled) {
            cancelAutoLaunch(context)
            return
        }

        // 获取即将出发的最近行程
        val now = System.currentTimeMillis()
        val allTrips = db.getAllTrips().filter { !it.isArchived }
        val upcomingTrips = allTrips.filter { it.getStage(now) == TripStage.UPCOMING }
            .sortedWith(compareBy({ it.departureDate }, { it.departureTime }))
        val inTransitTrips = allTrips.filter { it.getStage(now) == TripStage.IN_TRANSIT }

        // 如果已经在途中，且服务未运行，直接尝试拉起
        if (inTransitTrips.isNotEmpty()) {
            val currentInTransit = inTransitTrips.first()
            if (!LiveIslandService.isServiceRunning && IslandPermissionHelper.hasOverlayPermission(context)) {
                LiveIslandService.start(context, currentInTransit.orderNo)
            }
            return
        }

        val nextTrip = upcomingTrips.firstOrNull()
        if (nextTrip == null) {
            cancelAutoLaunch(context)
            return
        }

        val leadMinutes = getAutoLeadMinutes(context)
        val depMillis = nextTrip.getDepartureTimeMillis()
        val arrMillis = nextTrip.getArrivalTimeMillis()

        val targetLaunchMillis = if (leadMinutes == 0L) {
            // 特殊模式：发车日当天 00:00:05 (上海时间)
            val shanghaiTz = TimeZone.getTimeZone("Asia/Shanghai")
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { timeZone = shanghaiTz }
            try {
                val cal = Calendar.getInstance(shanghaiTz).apply {
                    time = sdf.parse(nextTrip.departureDate) ?: java.util.Date()
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 5)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            } catch (_: Exception) {
                depMillis - 3600_000L
            }
        } else {
            depMillis - leadMinutes * 60 * 1000L
        }

        if (now in targetLaunchMillis..arrMillis) {
            // 已在提前拉起时间窗口内，立即启动服务
            if (IslandPermissionHelper.hasOverlayPermission(context)) {
                if (!LiveIslandService.isServiceRunning || LiveIslandService.currentOrderNo != nextTrip.orderNo) {
                    LiveIslandService.start(context, nextTrip.orderNo)
                }
            }
            return
        }

        if (now < targetLaunchMillis) {
            // 尚未到拉起时间，注册系统底层 AlarmManager 精准定时唤醒
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, LiveIslandAutoLaunchReceiver::class.java).apply {
                action = LiveIslandAutoLaunchReceiver.ACTION_AUTO_LAUNCH
                putExtra(LiveIslandAutoLaunchReceiver.EXTRA_ORDER_NO, nextTrip.orderNo)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetLaunchMillis, pi)
                    } else {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetLaunchMillis, pi)
                    }
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, targetLaunchMillis, pi)
                }
            } catch (_: Exception) {
                // 异常优雅降级
                am.set(AlarmManager.RTC_WAKEUP, targetLaunchMillis, pi)
            }
        }
    }

    fun cancelAutoLaunch(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, LiveIslandAutoLaunchReceiver::class.java).apply {
            action = LiveIslandAutoLaunchReceiver.ACTION_AUTO_LAUNCH
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }
}
