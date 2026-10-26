package team.shiro.railwidget

import android.app.Application
import team.shiro.railwidget.data.local.TripDatabaseHelper

class RailWidgetApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val db = TripDatabaseHelper.getInstance(this)
        team.shiro.railwidget.sync.TripRefresh.initialize(this)
        // Default placeholder settings (no private credentials)
        if (db.getSetting("cloudmail_url").isBlank()) {
            db.setSetting("cloudmail_url", "https://mail.example.com")
        }
    }
}

