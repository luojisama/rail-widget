package team.shiro.railwidget

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TripDatabaseMigrationTest {
    @Test fun upgradePreservesSettingsAndSupportsNewFields() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("rail_widget.db")
        val path = context.getDatabasePath("rail_widget.db")
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("""CREATE TABLE trips (
                order_no TEXT PRIMARY KEY, passenger_name TEXT NOT NULL, train_code TEXT NOT NULL,
                train_no TEXT, departure_station TEXT NOT NULL, arrival_station TEXT NOT NULL,
                departure_date TEXT NOT NULL, departure_time TEXT NOT NULL, arrival_time TEXT,
                carriage TEXT, seat TEXT, seat_type TEXT, ticket_gate TEXT, ticket_type TEXT, price TEXT,
                stopover_time TEXT, stops_json TEXT, detail_url TEXT, raw_source TEXT, updated_at INTEGER,
                is_archived INTEGER DEFAULT 0)""")
            old.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT)")
            old.execSQL("INSERT INTO settings VALUES ('island_auto_lead_minutes','30')")
            old.execSQL("""INSERT INTO trips(order_no,passenger_name,train_code,departure_station,arrival_station,
                departure_date,departure_time,stops_json,is_archived) VALUES
                ('old','甲','G1','北京南','上海虹桥','2026-10-26','08:00','[]',1)""")
            old.version = 2
        }
        val helper = TripDatabaseHelper(context)
        assertEquals("30", helper.getSetting("island_auto_lead_minutes"))
        assertTrue(helper.getTrip("old")!!.isArchived)
        val t = helper.getTrip("old")!!.copy(trainModel = "CR400AF", diningCar = "05车", platform = "7",
            dataCheckedAt = 123L, stops = listOf(StopInfo("1", "北京南", "----", "08:00", "", actualDeparture = 100L)))
        helper.insertOrUpdateTrip(t)
        assertEquals(t, helper.getTrip("old"))
        // A new SMS import must not wipe cached train metadata or stop details.
        helper.insertOrUpdateTrip(t.copy(dataCheckedAt = 0L, trainModel = "", stops = emptyList()))
        assertEquals("CR400AF", helper.getTrip("old")!!.trainModel)
        assertEquals(100L, helper.getTrip("old")!!.stops.single().actualDeparture)
        helper.close()
        context.deleteDatabase("rail_widget.db")
    }
}
