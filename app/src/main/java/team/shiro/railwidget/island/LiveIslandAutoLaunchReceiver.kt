package team.shiro.railwidget.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import team.shiro.railwidget.data.local.TripDatabaseHelper

/**
 * 灵动胶囊 / 灵动岛系统定时自动唤醒广播接收器
 * 负责接收底层 AlarmManager 精准到点广播并拉起前台服务，
 * 同时监听系统开机与时区变更广播重新挂载定时器。
 */
class LiveIslandAutoLaunchReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_AUTO_LAUNCH = "team.shiro.railwidget.action.ISLAND_AUTO_LAUNCH"
        const val EXTRA_ORDER_NO = "EXTRA_ORDER_NO"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_AUTO_LAUNCH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // Re-read current tickets and settings; never start a stale alarm's deleted trip.
                LiveIslandScheduler.scheduleAutoLaunch(context)
                team.shiro.railwidget.widget.TripStateScheduler.schedule(context)
                team.shiro.railwidget.widget.TripWidgetRenderer.updateAllWidgets(context)
            }
        }
    }
}
