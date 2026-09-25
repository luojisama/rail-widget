package team.shiro.railwidget.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip
import java.util.concurrent.TimeUnit

object RailwayApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Enrich a parsed Trip with exact arrival time, stopover duration, and intermediate stops
     * by querying 12306 public timetable APIs.
     */
    fun enrichTrip(trip: Trip): Trip {
        try {
            val dateFormatted = trip.departureDate.replace("-", "") // YYYYMMDD
            val trainNo = queryTrainNo(trip.trainCode, dateFormatted) ?: trip.trainNo

            if (trainNo.isBlank()) {
                return trip
            }

            val stops = queryTimetable(trainNo, trip.departureDate)
            if (stops.isEmpty()) {
                return trip.copy(trainNo = trainNo)
            }

            // Find arrival station in stops list
            val targetArrStation = trip.arrivalStation.replace("站", "").trim()
            val matchedStop = stops.find {
                it.stationName.replace("站", "").trim().equals(targetArrStation, ignoreCase = true)
            }

            val targetDepStation = trip.departureStation.replace("站", "").trim()
            val matchedDepStop = stops.find {
                it.stationName.replace("站", "").trim().equals(targetDepStation, ignoreCase = true)
            }

            val actualDepTime = if (matchedDepStop != null && matchedDepStop.startTime != "----") {
                matchedDepStop.startTime
            } else {
                trip.departureTime
            }

            val actualArrTime = if (matchedStop != null && matchedStop.arriveTime != "----") {
                matchedStop.arriveTime
            } else {
                trip.arrivalTime
            }

            val stopover = matchedStop?.stopoverTime ?: trip.stopoverTime

            return trip.copy(
                trainNo = trainNo,
                departureTime = actualDepTime,
                arrivalTime = actualArrTime,
                stopoverTime = stopover,
                stops = stops
            )
        } catch (_: Exception) {
            return trip
        }
    }

    /**
     * Step 1: Query 12306 train search to resolve internal train_no (e.g. "800000C3150E" for C315)
     */
    fun queryTrainNo(trainCode: String, dateCompact: String): String? {
        return try {
            val url = "https://search.12306.cn/search/v1/train/search?keyword=$trainCode&date=$dateCompact"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val dataArray = json.optJSONArray("data") ?: return null

                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val code = item.optString("station_train_code")
                    if (code.equals(trainCode, ignoreCase = true)) {
                        return item.optString("train_no")
                    }
                }
                // If exact match not found, take first with code starting with trainCode
                if (dataArray.length() > 0) {
                    dataArray.getJSONObject(0).optString("train_no")
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Step 2: Query 12306 full stops schedule with train_no and date (YYYY-MM-DD)
     */
    fun queryTimetable(trainNo: String, dateStandard: String): List<StopInfo> {
        val stops = mutableListOf<StopInfo>()
        try {
            val url = "https://kyfw.12306.cn/otn/czxx/queryByTrainNo?train_no=$trainNo&from_station_telecode=KMM&to_station_telecode=ZZM&depart_date=$dateStandard"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return stops
                val body = response.body?.string() ?: return stops
                val json = JSONObject(body)
                val dataObj = json.optJSONObject("data") ?: return stops
                val dataArray = dataObj.optJSONArray("data") ?: return stops

                for (i in 0 until dataArray.length()) {
                    val s = dataArray.getJSONObject(i)
                    stops.add(
                        StopInfo(
                            stationNo = s.optString("station_no"),
                            stationName = s.optString("station_name"),
                            arriveTime = s.optString("arrive_time"),
                            startTime = s.optString("start_time"),
                            stopoverTime = s.optString("stopover_time")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore network errors
        }
        return stops
    }
}
