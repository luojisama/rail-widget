package team.shiro.railwidget.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import team.shiro.railwidget.data.api.RailwayApiService
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.parser.Parser12306
import team.shiro.railwidget.widget.TripWidgetRenderer
import java.util.concurrent.Executors

class SmsBroadcastReceiver : BroadcastReceiver() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val fullMessage = StringBuilder()
        var sender = ""

        for (sms in messages) {
            sender = sms.displayOriginatingAddress ?: ""
            fullMessage.append(sms.displayMessageBody ?: "")
        }

        val text = fullMessage.toString()
        // If message is from 12306 or content contains 12306 / 车票
        if (sender.contains("12306") || text.contains("12306") || text.contains("铁路")) {
            val pendingResult = goAsync()
            executor.execute {
                try {
                    val trip = Parser12306.parseSms(text)
                    if (trip != null) {
                        val db = TripDatabaseHelper.getInstance(context)
                        val enriched = RailwayApiService.enrichTrip(trip)
                        db.insertOrUpdateTrip(enriched)
                        // Trigger widget refresh
                        TripWidgetRenderer.updateAllWidgets(context)
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
