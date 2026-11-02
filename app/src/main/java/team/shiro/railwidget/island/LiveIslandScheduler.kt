package team.shiro.railwidget.island

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripTimeline
import java.text.SimpleDateFormat
import java.util.Date
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
        cancelAutoLaunch(context)
        val db = TripDatabaseHelper.getInstance(context)
        if (!isAutoLaunchEnabled(context)) {
            db.setSetting("island_alarm_status", "未开启自动唤起")
            db.setSetting("island_next_alarm_millis", "")
            return
        }

        val now = System.currentTimeMillis()
        val dismissed = db.getSetting("island_dismissed_order")
        val trips = db.getActiveTrips().filter {
            it.getArrivalTimeMillis() > now && it.orderNo != dismissed
        }.sortedBy { it.getDepartureTimeMillis() }

        val lead = getAutoLeadMinutes(context)
        val eligible = trips.firstOrNull { now >= launchTime(it, lead) }

        if (eligible != null && !LiveIslandService.isServiceRunning && IslandPermissionHelper.hasOverlayPermission(context)) {
            // Android 14/15 豁免：先挂载可见悬浮窗，再拉起前台服务
            IslandOverlayHost.ensureAttached(context, eligible)
            LiveIslandService.start(context, eligible.orderNo, automatic = true)
        }

        val targets = trips.map { launchTime(it, lead) }.filter { it > now }.toMutableList()
        // 当存在正在运行或符合条件的行程时，在行程到达后重新评估接力行程
        if (eligible != null) targets += eligible.getArrivalTimeMillis() + 1000L

        val target = targets.minOrNull()
        if (target == null) {
            db.setSetting("island_alarm_status", "当前无待唤起行程")
            db.setSetting("island_next_alarm_millis", "")
            return
        }

        db.setSetting("island_next_alarm_millis", target.toString())

        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, LiveIslandAutoLaunchReceiver::class.java).setAction(LiveIslandAutoLaunchReceiver.ACTION_AUTO_LAUNCH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val timeStr = timeFormat.format(Date(target))

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                val useAlarmClock = db.getSetting("island_use_alarm_clock", "1") == "1"
                if (useAlarmClock) {
                    val showIntent = PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, team.shiro.railwidget.ui.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(target, showIntent), pending)
                } else {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending)
                }
                db.setSetting("island_alarm_status", "已安排精确定时唤起（$timeStr）")
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending)
                db.setSetting("island_alarm_status", "未授予精确闹钟权限，唤起可能延迟")
            }
        } catch (error: RuntimeException) {
            db.setSetting("island_alarm_status", "闹钟设置失败：${error.javaClass.simpleName}")
            android.util.Log.w("LiveIslandScheduler", "Unable to schedule island", error)
        }
    }

    fun launchTime(trip: Trip, lead: Long): Long =
        if (lead == 0L) TripTimeline.parse(trip.departureDate, "00:00")?.plus(5000L)
            ?: (trip.getDepartureTimeMillis() - 3_600_000L)
        else trip.getDepartureTimeMillis() - lead * 60_000L

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
