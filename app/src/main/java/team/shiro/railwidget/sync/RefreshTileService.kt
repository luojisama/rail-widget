package team.shiro.railwidget.sync

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.shiro.railwidget.data.local.TripDatabaseHelper

class RefreshTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val trip = TripDatabaseHelper.getInstance(this).getLatestUpcomingTrip()
        tile.state = Tile.STATE_INACTIVE
        if (trip != null) {
            tile.label = "刷新 · ${trip.trainCode}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "${trip.computeStatus()} · ${TripRefresh.formatFreshness(trip.dataCheckedAt, trip.liveCheckedAt)}"
            }
        } else {
            tile.label = "刷新行程"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "暂无出行计划"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "正在刷新…"
        }
        tile.updateTile()

        serviceScope.launch {
            val res = TripRefresh.refresh(applicationContext, force = true)
            withContext(Dispatchers.Main) {
                val currentTile = qsTile
                if (currentTile != null) {
                    currentTile.state = Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        currentTile.subtitle = res.message
                    }
                    val latestTrip = TripDatabaseHelper.getInstance(this@RefreshTileService).getLatestUpcomingTrip()
                    if (latestTrip != null) {
                        currentTile.label = "刷新 · ${latestTrip.trainCode}"
                    } else {
                        currentTile.label = "刷新行程"
                    }
                    currentTile.updateTile()
                }
                runCatching {
                    Toast.makeText(applicationContext, res.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
