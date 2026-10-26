package team.shiro.railwidget

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripTimeline
import team.shiro.railwidget.island.LiveIslandAutoLaunchReceiver
import team.shiro.railwidget.island.LiveIslandScheduler
import java.time.Instant
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class IslandAlarmRecoveryTest {
    @Test fun reschedulesAfterPermissionAndBootAndCancelsDeletedTrips() {
        val context = RuntimeEnvironment.getApplication()
        val db = TripDatabaseHelper.getInstance(context)
        val departure = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(TripTimeline.zone).plusHours(3).withSecond(0).withNano(0)
        val arrival = departure.plusHours(2)
        val trip = Trip("alarm-test", "甲", "G101", departureStation = "北京南", arrivalStation = "上海虹桥",
            departureDate = departure.toLocalDate().toString(), departureTime = departure.format(DateTimeFormatter.ofPattern("HH:mm")),
            arrivalTime = arrival.format(DateTimeFormatter.ofPattern("HH:mm")))
        db.insertOrUpdateTrip(trip)
        db.setSetting(LiveIslandScheduler.KEY_AUTO_LAUNCH_ENABLED, "1")
        db.setSetting(LiveIslandScheduler.KEY_AUTO_LEAD_MINUTES, "60")
        val manager = context.getSystemService(AlarmManager::class.java)
        fun alarms() = shadowOf(manager).scheduledAlarms.filter {
            shadowOf(it.operation).savedIntent.action == LiveIslandAutoLaunchReceiver.ACTION_AUTO_LAUNCH
        }
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        LiveIslandScheduler.scheduleAutoLaunch(context)
        assertEquals(1, alarms().size)
        assertEquals(trip.getDepartureTimeMillis() - 3_600_000L, alarms().single().triggerAtTime)
        assertTrue(db.getSetting("island_alarm_status").contains("可能延迟"))

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val receiver = LiveIslandAutoLaunchReceiver()
        receiver.onReceive(context, Intent("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))
        assertEquals(1, alarms().size)
        assertTrue(db.getSetting("island_alarm_status").contains("精确定时"))
        db.setSetting(LiveIslandScheduler.KEY_AUTO_LEAD_MINUTES, "30")
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, alarms().size)
        assertEquals(trip.getDepartureTimeMillis() - 1_800_000L, alarms().single().triggerAtTime)

        db.deleteTrip(trip.orderNo)
        // Even a previously delivered alarm cannot restore the deleted ticket.
        receiver.onReceive(context, Intent(LiveIslandAutoLaunchReceiver.ACTION_AUTO_LAUNCH).putExtra(LiveIslandAutoLaunchReceiver.EXTRA_ORDER_NO, trip.orderNo))
        assertTrue(alarms().isEmpty())
        assertNull(shadowOf(context).nextStartedService)
        db.insertOrUpdateTrip(trip)
        LiveIslandScheduler.scheduleAutoLaunch(context)
        db.setSetting(LiveIslandScheduler.KEY_AUTO_LAUNCH_ENABLED, "0")
        LiveIslandScheduler.scheduleAutoLaunch(context)
        assertTrue(alarms().isEmpty())
    }
}
