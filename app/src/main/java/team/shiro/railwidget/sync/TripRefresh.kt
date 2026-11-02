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
import androidx.work.workDataOf
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
    const val ACTION_SHORTCUT_REFRESH = "team.shiro.railwidget.action.REFRESH_STATUS"
    private val attempts = mutableMapOf<String, Long>()
    data class Result(val requested: Int, val updated: Int) {
        val message: String get() = when {
            requested == 0 -> "暂无需要刷新的行程，状态已更新"
            updated == 0 -> "状态已更新，暂未取得新的列车数据，保留已有信息"
            updated < requested -> "已更新 $updated/$requested 个行程，其余保留已有信息"
            else -> "行程状态与列车资料已更新"
        }
    }

    fun formatFreshness(dataCheckedAt: Long, liveCheckedAt: Long = 0L, now: Long = System.currentTimeMillis()): String {
        val ts = maxOf(dataCheckedAt, liveCheckedAt)
        if (ts <= 0L) return "待刷新"
        val diffMin = ((now - ts).coerceAtLeast(0L)) / 60_000L
        return when {
            diffMin < 1 -> "刚刚更新"
            diffMin < 60 -> "${diffMin}分钟前更新"
            diffMin < 1440 -> "${diffMin / 60}小时前更新"
            else -> "${diffMin / 1440}天前更新"
        }
    }

    /** Serializes UI, service and worker fetches; repeated taps reuse the latest result. */
    @Synchronized
    fun refresh(context: Context, orderNo: String? = null, force: Boolean = false): Result {
        val db = TripDatabaseHelper.getInstance(context)
        val now = System.currentTimeMillis()
        val trips = if (orderNo != null) {
            listOfNotNull(db.getTrip(orderNo))
        } else {
            val activeWindow = db.getActiveTrips().filter {
                it.getDepartureTimeMillis() - now <= 86_400_000L && now < it.getArrivalTimeMillis() + 900_000L
            }
            if (force) {
                val extra = db.getAllTrips()
                    .filter { t -> activeWindow.none { it.orderNo == t.orderNo } }
                    .sortedByDescending { it.departureDate }
                    .filter { it.lulutongVersion.isBlank() || it.rideMileageKm == null || it.stops.isEmpty() }
                    .take(5)
                activeWindow + extra
            } else {
                activeWindow
            }
        }
        var updated = 0
        var requestedCount = trips.size
        trips.forEach { trip ->
            val last = attempts[trip.orderNo] ?: 0L
            if (now - last < if (force) 3_000L else 300_000L) {
                if (trip.dataCheckedAt > 0 && trip.dataCheckedAt >= last) updated++
                return@forEach
            }
            attempts[trip.orderNo] = now
            val enriched = LulutongData.enrich(context, RailwayApiService.enrichTrip(trip))
            if (db.applyTrainEnrichment(trip, enriched)) updated++
        }

        // Fast offline/cached Lulutong pass for all remaining trips missing mileage, platform, or bureau
        if (orderNo == null) {
            val allTrips = db.getAllTrips()
            for (t in allTrips) {
                if (trips.any { it.orderNo == t.orderNo }) continue
                if (t.lulutongVersion.isBlank() || t.rideMileageKm == null || t.platform.isBlank() || t.railwayBureau.endsWith("段")) {
                    val lltEnriched = LulutongData.enrich(context, t)
                    if (lltEnriched != t && db.applyTrainEnrichment(t, lltEnriched)) {
                        updated++
                        requestedCount++
                    }
                }
            }
        }

        publish(context)
        return Result(requestedCount, updated)
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
        try {
            WorkManager.getInstance(context).enqueueUniqueWork("trip-refresh", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TripRefreshWorker>().build())
        } catch (error: RuntimeException) {
            team.shiro.railwidget.diagnostics.CrashDiagnostics.record(context, "BackgroundEnqueue", error)
            android.util.Log.e("TripRefresh", "Unable to enqueue background refresh", error)
        }
    }

    fun enqueueForce(context: Context) {
        publish(context)
        try {
            val request = OneTimeWorkRequestBuilder<TripRefreshWorker>()
                .setInputData(workDataOf("force" to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("trip-refresh", ExistingWorkPolicy.REPLACE, request)
        } catch (error: RuntimeException) {
            team.shiro.railwidget.diagnostics.CrashDiagnostics.record(context, "BackgroundEnqueueForce", error)
            android.util.Log.e("TripRefresh", "Unable to enqueue background force refresh", error)
        }
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
            val force = inputData.getBoolean("force", false)
            TripRefresh.refresh(applicationContext, force = force)
            Result.success()
        } catch (error: Exception) {
            android.util.Log.w("TripRefresh", "刷新未完成，保留缓存", error)
            Result.failure()
        }
    }
}
