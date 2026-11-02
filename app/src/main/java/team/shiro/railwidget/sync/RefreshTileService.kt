package team.shiro.railwidget.sync

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RefreshTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "刷新行程"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        TripRefresh.enqueue(applicationContext)
    }
}
