package team.shiro.railwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.Toast
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.sync.TripRefresh

class TripWidget2x2Receiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val db = TripDatabaseHelper.getInstance(context)
        val trip = db.getLatestUpcomingTrip()
        for (id in appWidgetIds) {
            val views = TripWidgetRenderer.render2x2(context, trip)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TripWidgetRenderer.ACTION_REFRESH_SYNC) {
            TripRefresh.enqueueForce(context)
            runCatching {
                Toast.makeText(context, "正在刷新行程状态与实时资料…", Toast.LENGTH_SHORT).show()
            }
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TripWidgetRenderer.ACTION_REFRESH_SYNC) {
            TripRefresh.enqueueForce(context)
            runCatching {
                Toast.makeText(context, "正在刷新行程状态与实时资料…", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class TripWidget4x4Receiver : AppWidgetProvider() {
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
            TripRefresh.enqueueForce(context)
            runCatching {
                Toast.makeText(context, "正在刷新行程状态与实时资料…", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
