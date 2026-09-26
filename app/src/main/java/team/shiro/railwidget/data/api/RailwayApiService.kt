package team.shiro.railwidget.data.api

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object RailwayApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Enrich a parsed Trip with exact arrival time, stopover duration, intermediate stops,
     * and official 12306 check-in ticket gate by querying 12306 public APIs.
     */
    fun enrichTrip(trip: Trip): Trip {
        try {
            val todayStandard = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            val todayCompact = todayStandard.replace("-", "")

            val dateFormatted = trip.departureDate.replace("-", "") // YYYYMMDD
            var trainNo = queryTrainNo(trip.trainCode, dateFormatted) ?: trip.trainNo

            // 针对历史行程（12306 仅提供近期时刻表），自动回退以今日为基准查询 trainNo
            if (trainNo.isBlank() && dateFormatted != todayCompact) {
                trainNo = queryTrainNo(trip.trainCode, todayCompact) ?: ""
            }

            if (trainNo.isBlank()) {
                return trip
            }

            val targetDepStation = trip.departureStation.replace("站", "").trim()
            val queryDateForGate = if (trip.departureDate.isNotBlank()) trip.departureDate else todayStandard
            var fetchedGate = queryTicketGate(trip.trainCode, trainNo, targetDepStation, queryDateForGate)
            if (fetchedGate.isNullOrBlank() && queryDateForGate != todayStandard) {
                fetchedGate = queryTicketGate(trip.trainCode, trainNo, targetDepStation, todayStandard)
            }
            val effectiveGate = if (!fetchedGate.isNullOrBlank()) fetchedGate else trip.ticketGate

            // 途经时刻表查询：若历史日期查询返回空，回退以今日基准获取经停站
            var stops = queryTimetable(trainNo, trip.departureDate)
            if (stops.isEmpty() && trip.departureDate != todayStandard) {
                stops = queryTimetable(trainNo, todayStandard)
            }
            if (stops.isEmpty()) {
                return trip.copy(trainNo = trainNo, ticketGate = effectiveGate)
            }

            // 在时刻表经停站中匹配出发站
            val matchedDepStop = stops.find {
                it.stationName.replace("站", "").trim().equals(targetDepStation, ignoreCase = true)
            }

            val isArrStationPlaceholder = trip.arrivalStation.isBlank() ||
                    trip.arrivalStation == "到达站" ||
                    trip.arrivalStation == "终点站" ||
                    trip.arrivalStation.contains("12306")

            val effectiveArrStation = if (isArrStationPlaceholder && stops.isNotEmpty()) {
                stops.last().stationName
            } else {
                trip.arrivalStation
            }

            val targetArrStation = effectiveArrStation.replace("站", "").trim()
            val matchedStop = stops.find {
                it.stationName.replace("站", "").trim().equals(targetArrStation, ignoreCase = true)
            } ?: stops.lastOrNull()

            val actualDepTime = if (matchedDepStop != null && matchedDepStop.startTime != "----") {
                matchedDepStop.startTime
            } else if (trip.departureTime.isNotBlank() && trip.departureTime != "00:00") {
                trip.departureTime
            } else {
                matchedDepStop?.arriveTime ?: trip.departureTime
            }

            val actualArrTime = if (matchedStop != null && matchedStop.arriveTime != "----") {
                matchedStop.arriveTime
            } else if (matchedStop != null && matchedStop.startTime != "----") {
                matchedStop.startTime
            } else {
                trip.arrivalTime
            }

            val stopover = matchedStop?.stopoverTime ?: trip.stopoverTime

            return trip.copy(
                trainNo = trainNo,
                departureStation = matchedDepStop?.stationName ?: trip.departureStation,
                arrivalStation = effectiveArrStation,
                departureTime = actualDepTime,
                arrivalTime = actualArrTime,
                ticketGate = effectiveGate,
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

    /**
     * Query 12306 official station check-in gate for a specific train at the departure station.
     * Flow:
     * 1. GET queryStopStations to find departure station telecode (e.g. 北京南 -> VNP)
     * 2. POST queryTicketCheck with trainDate, trainCode, and telecode
     */
    fun queryTicketGate(
        trainCode: String,
        trainNo: String,
        depStation: String,
        dateStandard: String
    ): String? {
        try {
            // Step 1: Query stop stations telecodes for this train
            val stopStationsUrl = "https://www.12306.cn/index/otn/index12306/queryStopStations?train_no=$trainNo&depart_date=$dateStandard"
            val stopReq = Request.Builder()
                .url(stopStationsUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.12306.cn/index/view/infos/ticket_check.html")
                .build()

            var telecode: String? = null
            fun tryResolveTelecode(d: String): String? {
                val stopStationsUrl = "https://www.12306.cn/index/otn/index12306/queryStopStations?train_no=$trainNo&depart_date=$d"
                val stopReq = Request.Builder()
                    .url(stopStationsUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://www.12306.cn/index/view/infos/ticket_check.html")
                    .build()

                return client.newCall(stopReq).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val dataObj = json.optJSONObject("data") ?: return null
                    val targetClean = depStation.replace("站", "").trim()

                    val keys = dataObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val arr = dataObj.optJSONArray(key)
                        if (arr != null && arr.length() >= 2) {
                            val stationName = arr.optString(0).replace("站", "").trim()
                            if (stationName.equals(targetClean, ignoreCase = true)) {
                                return@use arr.optString(1)
                            }
                        }
                    }
                    null
                }
            }

            telecode = tryResolveTelecode(dateStandard)
            val todayStandard = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            if (telecode.isNullOrBlank() && dateStandard != todayStandard) {
                telecode = tryResolveTelecode(todayStandard)
            }

            if (telecode.isNullOrBlank()) return null

            // Step 2: Query ticket check-in gate using telecode
            val checkUrl = "https://www.12306.cn/index/otn/index12306/queryTicketCheck"
            val formBody = FormBody.Builder()
                .add("trainDate", dateStandard)
                .add("station_train_code", trainCode)
                .add("from_station_telecode", telecode!!)
                .build()

            val checkReq = Request.Builder()
                .url(checkUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.12306.cn/index/view/infos/ticket_check.html")
                .post(formBody)
                .build()

            client.newCall(checkReq).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val dataObj = json.optJSONObject("data") ?: return null
                val rawPlatform = dataObj.optString("trainPlatform", "").trim()
                if (rawPlatform.isNotBlank()) {
                    return cleanPlatformString(rawPlatform)
                }
            }
        } catch (_: Exception) {
            // Ignore network errors
        }
        return null
    }

    /**
     * Clean 12306 raw platform string, e.g. "检票口17A、17B" -> "17A/B" or "13A13B" -> "13A/B"
     */
    fun cleanPlatformString(raw: String): String {
        val cleaned = raw.removePrefix("检票口").trim()
        if (cleaned.isBlank()) return ""

        // e.g. "17A、17B" or "13A13B" or "17A 17B"
        val multiPattern = Regex("""^(\d+)([A-Za-z])[、,/ ]*(\d+)([A-Za-z])$""")
        val multiMatch = multiPattern.find(cleaned)
        if (multiMatch != null) {
            val num1 = multiMatch.groupValues[1]
            val l1 = multiMatch.groupValues[2]
            val num2 = multiMatch.groupValues[3]
            val l2 = multiMatch.groupValues[4]
            return if (num1 == num2) "$num1$l1/$l2" else "$num1$l1/$num2$l2"
        }

        return cleaned
            .replace("、", "/")
            .replace(",", "/")
            .replace(" ", "/")
    }
}
