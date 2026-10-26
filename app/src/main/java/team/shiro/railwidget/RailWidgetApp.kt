package team.shiro.railwidget

import android.app.Application
import team.shiro.railwidget.data.local.TripDatabaseHelper

class RailWidgetApp : Application() {

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        team.shiro.railwidget.diagnostics.CrashDiagnostics.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (team.shiro.railwidget.diagnostics.CrashDiagnostics.read(this) != null) return
        val db = TripDatabaseHelper.getInstance(this)
        team.shiro.railwidget.sync.TripRefresh.initialize(this)
        // Default placeholder settings (no private credentials)
        if (db.getSetting("cloudmail_url").isBlank()) {
            db.setSetting("cloudmail_url", "https://mail.example.com")
        }
    }
}

