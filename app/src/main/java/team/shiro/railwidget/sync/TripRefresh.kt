package team.shiro.railwidget.sync

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.shiro.railwidget.data.api.LulutongData
import team.shiro.railwidget.data.api.RailwayApiService
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.island.LiveIslandScheduler
import team.shiro.railwidget.island.LiveIslandService
import team.shiro.railwidget.widget.TripStateScheduler
import team.shiro.railwidget.widget.TripWidgetRenderer
import java.util.concurrent.TimeUnit

object TripRefresh {
    const val ACTION_CHANGED = "team.shiro.railwidget.action.TRIPS_CHANGED"
    private val attempts = mutableMapOf<String, Long>()
    data class Result(val requested: Int, val updated: Int) {
        val message: String get() = when {
            requested == 0 -> "暂无需要刷新的行程，状态已更新"
            updated == 0 -> "状态已更新，暂未取得新的列车数据，保留已有信息"
            updated < requested -> "已更新 $updated/$requested 个行程，其余保留已有信息"
            else -> "行程状态与列车资料已更新"
        }
    }

    /** Serializes UI, service and worker fetches; repeated taps reuse the latest result. */
    @Synchronized
    fun refresh(context: Context, orderNo: String? = null, force: Boolean = false): Result {
        val db = TripDatabaseHelper.getInstance(context)
        val now = System.currentTimeMillis()
        val trips = if (orderNo != null) listOfNotNull(db.getTrip(orderNo)) else db.getActiveTrips().filter {
            it.getDepartureTimeMillis() - now <= 86_400_000L && now < it.getArrivalTimeMillis() + 900_000L
        }
        var updated = 0
        trips.forEach { trip ->
            val last = attempts[trip.orderNo] ?: 0L
            if (now - last < if (force) 10_000L else 300_000L) {
                if (trip.dataCheckedAt > 0 && trip.dataCheckedAt >= last) updated++
                return@forEach
            }
            attempts[trip.orderNo] = now
            val enriched = LulutongData.enrich(context, RailwayApiService.enrichTrip(trip))
            // A network request must not resurrect a deleted trip or overwrite a user edit.
            val current = db.getTrip(trip.orderNo) ?: return@forEach
            if (current.trainCode != trip.trainCode || current.departureDate != trip.departureDate ||
                current.departureStation != trip.departureStation || current.arrivalStation != trip.arrivalStation) return@forEach
            if (enriched.dataCheckedAt > trip.dataCheckedAt) {
                db.insertOrUpdateTrip(current.copy(
                    trainNo = enriched.trainNo, stops = enriched.stops,
                    departureTime = if (current.departureTime == trip.departureTime) enriched.departureTime else current.departureTime,
                    arrivalTime = if (current.arrivalTime == trip.arrivalTime) enriched.arrivalTime else current.arrivalTime,
                    ticketGate = if (current.ticketGateManual) current.ticketGate else enriched.ticketGate,
                    trainModel = enriched.trainModel, railwayBureau = enriched.railwayBureau,
                    diningCar = enriched.diningCar, platform = enriched.platform,
                    totalMileageKm = enriched.totalMileageKm, rideMileageKm = enriched.rideMileageKm,
                    arrivalDayOffset = enriched.arrivalDayOffset, dataSource = enriched.dataSource,
                    dataCheckedAt = enriched.dataCheckedAt, liveCheckedAt = enriched.liveCheckedAt))
                updated++
            }
        }
        publish(context)
        return Result(trips.size, updated)
    }

    fun publish(context: Context) {
        TripWidgetRenderer.updateAllWidgets(context)
        if (LiveIslandService.isServiceRunning) LiveIslandService.update(context)
        LiveIslandScheduler.scheduleAutoLaunch(context)
        TripStateScheduler.schedule(context)
        context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName))
    }

    fun enqueue(context: Context) {
        // Local state renders immediately, even if offline. Worker survives receiver/process exit.
        TripWidgetRenderer.updateAllWidgets(context)
        WorkManager.getInstance(context).enqueueUniqueWork("trip-refresh", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TripRefreshWorker>().build())
    }

    fun initialize(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("trip-refresh-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TripRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        TripStateScheduler.schedule(context)
        LiveIslandScheduler.scheduleAutoLaunch(context)
    }
}

class TripRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            TripRefresh.refresh(applicationContext)
            Result.success()
        } catch (error: Exception) {
            android.util.Log.w("TripRefresh", "刷新未完成，保留缓存", error)
            Result.failure()
        }
    }
}
