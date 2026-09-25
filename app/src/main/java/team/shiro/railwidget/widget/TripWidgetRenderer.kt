package team.shiro.railwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import team.shiro.railwidget.R
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.ui.MainActivity

object TripWidgetRenderer {

    const val ACTION_REFRESH_SYNC = "team.shiro.railwidget.ACTION_REFRESH_SYNC"

    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val db = TripDatabaseHelper.getInstance(context)
        val upcomingTrip = db.getLatestUpcomingTrip()

        // 2x2
        val comp2x2 = ComponentName(context, TripWidget2x2Receiver::class.java)
        val ids2x2 = appWidgetManager.getAppWidgetIds(comp2x2)
        if (ids2x2.isNotEmpty()) {
            val views = render2x2(context, upcomingTrip)
            appWidgetManager.updateAppWidget(ids2x2, views)
        }

        // 4x2
        val comp4x2 = ComponentName(context, TripWidget4x2Receiver::class.java)
        val ids4x2 = appWidgetManager.getAppWidgetIds(comp4x2)
        if (ids4x2.isNotEmpty()) {
            val views = render4x2(context, upcomingTrip)
            appWidgetManager.updateAppWidget(ids4x2, views)
        }

        // 4x4
        val comp4x4 = ComponentName(context, TripWidget4x4Receiver::class.java)
        val ids4x4 = appWidgetManager.getAppWidgetIds(comp4x4)
        if (ids4x4.isNotEmpty()) {
            val views = render4x4(context, upcomingTrip)
            appWidgetManager.updateAppWidget(ids4x4, views)
        }
    }

    fun render2x2(context: Context, trip: Trip?): RemoteViews {
        if (trip == null) {
            val views = RemoteViews(context.packageName, R.layout.widget_empty)
            views.setOnClickPendingIntent(R.id.widget_root, createOpenAppIntent(context))
            return views
        }

        val views = RemoteViews(context.packageName, R.layout.widget_trip_2x2)
        views.setTextViewText(R.id.tv_train_code, trip.trainCode)
        views.setTextViewText(R.id.tv_dep_time, "${trip.departureTime} 开")
        views.setTextViewText(R.id.tv_route, "${trip.departureStation} ➔ ${trip.arrivalStation}")
        views.setTextViewText(R.id.tv_seat, "${trip.carriage} ${trip.seat}")

        val gate = trip.getCleanTicketGate()
        views.setTextViewText(R.id.tv_gate, if (gate.isBlank()) "检票口 --" else gate)

        views.setOnClickPendingIntent(R.id.widget_root, createOpenAppIntent(context, trip.orderNo))
        return views
    }

    fun render4x2(context: Context, trip: Trip?): RemoteViews {
        if (trip == null) {
            val views = RemoteViews(context.packageName, R.layout.widget_empty)
            views.setOnClickPendingIntent(R.id.widget_root, createOpenAppIntent(context))
            return views
        }

        val views = RemoteViews(context.packageName, R.layout.widget_trip_4x2)
        views.setTextViewText(R.id.tv_train_code, trip.trainCode)
        views.setTextViewText(R.id.tv_date, trip.departureDate.substringAfter("-"))
        views.setTextViewText(R.id.tv_status, trip.computeStatus())

        views.setTextViewText(R.id.tv_dep_time, trip.departureTime)
        views.setTextViewText(R.id.tv_dep_station, trip.departureStation)

        val arrTime = if (trip.arrivalTime.isNotBlank()) trip.arrivalTime else "--:--"
        views.setTextViewText(R.id.tv_arr_time, arrTime)
        views.setTextViewText(R.id.tv_arr_station, trip.arrivalStation)

        val seatDetail = "${trip.carriage} ${trip.seat} · ${trip.seatType}"
        views.setTextViewText(R.id.tv_seat_info, seatDetail)

        val gate = trip.getCleanTicketGate()
        views.setTextViewText(R.id.tv_gate, if (gate.isBlank()) "检票口 未出" else "检票口 $gate")

        views.setOnClickPendingIntent(R.id.widget_root, createOpenAppIntent(context, trip.orderNo))
        return views
    }

    fun render4x4(context: Context, trip: Trip?): RemoteViews {
        if (trip == null) {
            val views = RemoteViews(context.packageName, R.layout.widget_empty)
            views.setOnClickPendingIntent(R.id.widget_root, createOpenAppIntent(context))
            return views
        }

        val views = RemoteViews(context.packageName, R.layout.widget_trip_4x4)
        views.setTextViewText(R.id.tv_train_code, trip.trainCode)
        views.setTextViewText(R.id.tv_passenger, trip.passengerName)
        views.setTextViewText(R.id.tv_date, trip.departureDate)
        views.setTextViewText(R.id.tv_status, trip.computeStatus())

        views.setTextViewText(R.id.tv_dep_time, trip.departureTime)
        views.setTextViewText(R.id.tv_dep_station, trip.departureStation)

        val arrTime = if (trip.arrivalTime.isNotBlank()) trip.arrivalTime else "--:--"
        views.setTextViewText(R.id.tv_arr_time, arrTime)
        views.setTextViewText(R.id.tv_arr_station, trip.arrivalStation)

        val seatDetail = "${trip.carriage} ${trip.seat} · ${trip.seatType} · ${trip.ticketType}"
        views.setTextViewText(R.id.tv_seat_detail, seatDetail)

        val gate = trip.getCleanTicketGate()
        views.setTextViewText(R.id.tv_gate, if (gate.isBlank()) "检票口 未公布" else "检票口 $gate")

        // Build stops summary string
        val stopsSummary = if (trip.stops.isNotEmpty()) {
            trip.stops.joinToString(" ➔ ") {
                val timeInfo = if (it.startTime != "----") it.startTime else it.arriveTime
                "${it.stationName} ($timeInfo)"
            }
        } else {
            "${trip.departureStation} (${trip.departureTime}) ➔ ... ➔ ${trip.arrivalStation} ($arrTime)"
        }
        views.setTextViewText(R.id.tv_stops_summary, stopsSummary)

        // Set refresh button intent
        val refreshIntent = Intent(context, TripWidget4x4Receiver::class.java).apply {
            action = ACTION_REFRESH_SYNC
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        // Set root click intent
        views.setOnClickPendingIntent(R.id.widget_root, createOpenAppIntent(context, trip.orderNo))
        return views
    }

    private fun createOpenAppIntent(context: Context, orderNo: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (orderNo != null) {
                putExtra("extra_order_no", orderNo)
            }
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
