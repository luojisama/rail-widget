package team.shiro.railwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.TripTimeline
import team.shiro.railwidget.sync.TripRefresh

object TripStateScheduler {
    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 20041,
        Intent(context, TripStateReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val events = TripDatabaseHelper.getInstance(context).getActiveTrips().flatMap { trip ->
            val dep = trip.getDepartureTimeMillis()
            val arr = trip.getArrivalTimeMillis()
            val minute = if (now in (dep - 3_600_000L)..arr) listOf(now + 60_000L) else emptyList()
            listOf(dep - 1_200_000L, dep - 300_000L, dep, arr) + minute +
                TripTimeline.events(trip).flatMap { listOfNotNull(it.arrival, it.departure) }
        }.filter { it > now }
        val target = events.minOrNull()
        if (target == null) manager.cancel(pending(context))
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending(context))
    }
}

class TripStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TripRefresh.publish(context)
    }
}
