package team.shiro.railwidget.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip

class TripDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "rail_widget.db"
        private const val DB_VERSION = 1

        private const val TABLE_TRIPS = "trips"
        private const val TABLE_SETTINGS = "settings"

        @Volatile
        private var instance: TripDatabaseHelper? = null

        fun getInstance(context: Context): TripDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: TripDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRIPS (
                order_no TEXT PRIMARY KEY,
                passenger_name TEXT NOT NULL,
                train_code TEXT NOT NULL,
                train_no TEXT,
                departure_station TEXT NOT NULL,
                arrival_station TEXT NOT NULL,
                departure_date TEXT NOT NULL,
                departure_time TEXT NOT NULL,
                arrival_time TEXT,
                carriage TEXT,
                seat TEXT,
                seat_type TEXT,
                ticket_gate TEXT,
                ticket_type TEXT,
                price TEXT,
                stopover_time TEXT,
                stops_json TEXT,
                raw_source TEXT,
                updated_at INTEGER,
                is_archived INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SETTINGS (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRIPS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SETTINGS")
        onCreate(db)
    }

    fun insertOrUpdateTrip(trip: Trip) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("order_no", trip.orderNo)
            put("passenger_name", trip.passengerName)
            put("train_code", trip.trainCode)
            put("train_no", trip.trainNo)
            put("departure_station", trip.departureStation)
            put("arrival_station", trip.arrivalStation)
            put("departure_date", trip.departureDate)
            put("departure_time", trip.departureTime)
            put("arrival_time", trip.arrivalTime)
            put("carriage", trip.carriage)
            put("seat", trip.seat)
            put("seat_type", trip.seatType)
            put("ticket_gate", trip.ticketGate)
            put("ticket_type", trip.ticketType)
            put("price", trip.price)
            put("stopover_time", trip.stopoverTime)
            put("stops_json", serializeStops(trip.stops))
            put("raw_source", trip.rawSource)
            put("updated_at", trip.updatedAt)
            put("is_archived", if (trip.isArchived) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_TRIPS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllTrips(): List<Trip> {
        val list = mutableListOf<Trip>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TRIPS ORDER BY departure_date DESC, departure_time DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToTrip(it))
            }
        }
        return list
    }

    fun getActiveTrips(): List<Trip> {
        val list = mutableListOf<Trip>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TRIPS WHERE is_archived = 0 ORDER BY departure_date ASC, departure_time ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToTrip(it))
            }
        }
        return list
    }

    fun getLatestUpcomingTrip(): Trip? {
        val active = getActiveTrips()
        if (active.isNotEmpty()) return active.first()
        val all = getAllTrips()
        return all.firstOrNull()
    }

    fun deleteTrip(orderNo: String) {
        writableDatabase.delete(TABLE_TRIPS, "order_no = ?", arrayOf(orderNo))
    }

    fun setArchived(orderNo: String, isArchived: Boolean) {
        val values = ContentValues().apply {
            put("is_archived", if (isArchived) 1 else 0)
        }
        writableDatabase.update(TABLE_TRIPS, values, "order_no = ?", arrayOf(orderNo))
    }

    fun setSetting(key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        writableDatabase.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSetting(key: String, default: String = ""): String {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT value FROM $TABLE_SETTINGS WHERE key = ?", arrayOf(key))
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0) ?: default
            }
        }
        return default
    }

    private fun cursorToTrip(c: android.database.Cursor): Trip {
        return Trip(
            orderNo = c.getString(c.getColumnIndexOrThrow("order_no")),
            passengerName = c.getString(c.getColumnIndexOrThrow("passenger_name")),
            trainCode = c.getString(c.getColumnIndexOrThrow("train_code")),
            trainNo = c.getString(c.getColumnIndexOrThrow("train_no")) ?: "",
            departureStation = c.getString(c.getColumnIndexOrThrow("departure_station")),
            arrivalStation = c.getString(c.getColumnIndexOrThrow("arrival_station")),
            departureDate = c.getString(c.getColumnIndexOrThrow("departure_date")),
            departureTime = c.getString(c.getColumnIndexOrThrow("departure_time")),
            arrivalTime = c.getString(c.getColumnIndexOrThrow("arrival_time")) ?: "",
            carriage = c.getString(c.getColumnIndexOrThrow("carriage")) ?: "",
            seat = c.getString(c.getColumnIndexOrThrow("seat")) ?: "",
            seatType = c.getString(c.getColumnIndexOrThrow("seat_type")) ?: "二等座",
            ticketGate = c.getString(c.getColumnIndexOrThrow("ticket_gate")) ?: "",
            ticketType = c.getString(c.getColumnIndexOrThrow("ticket_type")) ?: "成人票",
            price = c.getString(c.getColumnIndexOrThrow("price")) ?: "",
            stopoverTime = c.getString(c.getColumnIndexOrThrow("stopover_time")) ?: "",
            stops = deserializeStops(c.getString(c.getColumnIndexOrThrow("stops_json"))),
            rawSource = c.getString(c.getColumnIndexOrThrow("raw_source")) ?: "LOCAL",
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            isArchived = c.getInt(c.getColumnIndexOrThrow("is_archived")) == 1
        )
    }

    private fun serializeStops(stops: List<StopInfo>): String {
        val array = JSONArray()
        for (s in stops) {
            val obj = JSONObject().apply {
                put("no", s.stationNo)
                put("name", s.stationName)
                put("arr", s.arriveTime)
                put("start", s.startTime)
                put("stopover", s.stopoverTime)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeStops(json: String?): List<StopInfo> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<StopInfo>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    StopInfo(
                        stationNo = o.optString("no"),
                        stationName = o.optString("name"),
                        arriveTime = o.optString("arr"),
                        startTime = o.optString("start"),
                        stopoverTime = o.optString("stopover")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
