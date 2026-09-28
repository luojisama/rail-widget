package team.shiro.railwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.Toast
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.sync.CloudMailClient
import java.util.concurrent.Executors

class TripWidget2x2Receiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val db = TripDatabaseHelper.getInstance(context)
        val trip = db.getLatestUpcomingTrip()
        for (id in appWidgetIds) {
            val views = TripWidgetRenderer.render2x2(context, trip)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class TripWidget4x2Receiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val db = TripDatabaseHelper.getInstance(context)
        val trip = db.getLatestUpcomingTrip()
        for (id in appWidgetIds) {
            val views = TripWidgetRenderer.render4x2(context, trip)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class TripWidget4x4Receiver : AppWidgetProvider() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val db = TripDatabaseHelper.getInstance(context)
        val trip = db.getLatestUpcomingTrip()
        for (id in appWidgetIds) {
            val views = TripWidgetRenderer.render4x4(context, trip)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TripWidgetRenderer.ACTION_REFRESH_SYNC) {
            val db = TripDatabaseHelper.getInstance(context)
            val mailUrl = db.getSetting("cloudmail_url", "")
            val mailUser = db.getSetting("cloudmail_user", "")
            val mailPass = db.getSetting("cloudmail_pass", "")

            if (mailUrl.isBlank() || mailUser.isBlank() || mailPass.isBlank()) {
                Toast.makeText(context, "请先在应用内设置邮箱账号", Toast.LENGTH_SHORT).show()
                return
            }

            // 桌面即时交互反馈：立刻局部更新刷新按钮为“⟳ 同步中...”状态，消除桌面迟钝感
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val comp4x4 = android.content.ComponentName(context, TripWidget4x4Receiver::class.java)
                val ids4x4 = appWidgetManager.getAppWidgetIds(comp4x4)
                if (ids4x4.isNotEmpty()) {
                    val quickViews = android.widget.RemoteViews(context.packageName, team.shiro.railwidget.R.layout.widget_trip_4x4)
                    quickViews.setTextViewText(team.shiro.railwidget.R.id.btn_refresh, "⟳ 同步中...")
                    appWidgetManager.partiallyUpdateAppWidget(ids4x4, quickViews)
                }
            } catch (_: Exception) {}

            executor.execute {
                val result = CloudMailClient.sync(mailUrl, mailUser, mailPass, db)
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post {
                    if (result.isSuccess) {
                        Toast.makeText(context, "小部件已同步最新行程", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "同步失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                    TripWidgetRenderer.updateAllWidgets(context)
                }
            }
        }
    }
}
