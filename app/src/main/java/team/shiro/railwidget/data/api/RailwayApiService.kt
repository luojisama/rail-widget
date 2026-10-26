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
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object RailwayApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Enrich a parsed Trip with exact arrival time, stopover duration, intermediate stops,
     * and official 12306 check-in ticket gate by querying 12306 public APIs.
     */
    fun enrichTrip(trip: Trip): Trip {
        val now = System.currentTimeMillis()
        val trainNo = queryTrainNo(trip.trainCode, trip.departureDate.replace("-", "")) ?: trip.trainNo
        var result = trip
        if (trainNo.isNotBlank()) {
            val stops = queryTimetable(trainNo, trip.departureDate, trip.departureStation, trip.arrivalStation)
            val dep = stops.indexOfFirst { cleanStation(it.stationName) == cleanStation(trip.departureStation) }
            val arr = stops.indices.firstOrNull { it > dep && cleanStation(stops[it].stationName) == cleanStation(trip.arrivalStation) }
            if (dep >= 0 && arr != null) {
                // Preserve optional live fields when a timetable response contains only static data.
                val merged = stops.map { stop ->
                    val old = trip.stops.find { it.stationNo == stop.stationNo && it.stationName == stop.stationName }
                    if (old == null) stop else old.copy(arriveTime = stop.arriveTime, startTime = stop.startTime,
                        stopoverTime = stop.stopoverTime, arrivalDay = stop.arrivalDay, departureDay = stop.departureDay)
                }
                result = trip.copy(trainNo = trainNo, stops = merged,
                    departureTime = stops[dep].startTime.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: trip.departureTime,
                    arrivalTime = stops[arr].arriveTime.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: trip.arrivalTime,
                    dataCheckedAt = now, dataSource = "12306时刻表")
            }
            if (!trip.ticketGateManual) {
                val gate = queryTicketGate(trip.trainCode, trainNo, trip.departureStation, trip.departureDate)
                if (!gate.isNullOrBlank()) result = result.copy(ticketGate = gate, dataCheckedAt = now)
            }
        }
        // Query only the travel date. Today's platform must never overwrite another day's ticket.
        runCatching {
            val form = FormBody.Builder().add("trainCode", trip.trainCode).add("startDay", trip.departureDate.replace("-", "")).build()
            val request = Request.Builder().url("https://mobile.12306.cn/wxxcx/wechat/main/travelServiceQrcodeTrainInfo")
                .header("User-Agent", USER_AGENT).post(form).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) result = TrainDetails.applyInfo(result, JSONObject(response.body?.string() ?: "{}"), now)
            }
        }
        if (result.trainModel.isBlank() || result.diningCar.isBlank()) runCatching {
            val url = okhttp3.HttpUrl.Builder().scheme("https").host("mobile.12306.cn")
                .encodedPath("/wxxcx/openplatform-inner/miniprogram/wifiapps/appFrontEnd/v2/lounge/open-smooth-common/trainStyleBatch/getCarDetail")
                .addQueryParameter("trainCode", trip.trainCode).addQueryParameter("runningDay", trip.departureDate.replace("-", ""))
                .addQueryParameter("reqType", "form").addQueryParameter("carCode", trip.trainCode).build()
            client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
                if (response.isSuccessful) result = TrainDetails.applyStyle(result, JSONObject(response.body?.string() ?: "{}"), now)
            }
        }
        return result
    }

    private fun cleanStation(value: String) = value.trim().removeSuffix("站")

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
                    if (code.split('/').any { it.equals(trainCode, ignoreCase = true) }) {
                        return item.optString("train_no")
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Step 2: Query 12306 full stops schedule with train_no and date (YYYY-MM-DD)
     */
    fun queryTimetable(trainNo: String, dateStandard: String, fromStation: String = "", toStation: String = ""): List<StopInfo> {
        val stops = mutableListOf<StopInfo>()
        try {
            val stationCodes = stationCodes()
            val from = stationCodes[cleanStation(fromStation)].orEmpty()
            val to = stationCodes[cleanStation(toStation)].orEmpty()
            val url = okhttp3.HttpUrl.Builder().scheme("https").host("kyfw.12306.cn").encodedPath("/otn/czxx/queryByTrainNo")
                .addQueryParameter("train_no", trainNo).addQueryParameter("from_station_telecode", from)
                .addQueryParameter("to_station_telecode", to).addQueryParameter("depart_date", dateStandard).build()
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
                            stopoverTime = s.optString("stopover_time"),
                            arrivalDay = s.optString("arrive_day_diff").toIntOrNull(),
                            departureDay = s.optString("start_day_diff").toIntOrNull()
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
                if (rawPlatform.contains("检票口")) {
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

    @Volatile private var cachedStationCodes: Map<String, String> = emptyMap()
    private fun stationCodes(): Map<String, String> {
        if (cachedStationCodes.isNotEmpty()) return cachedStationCodes
        return runCatching {
            val request = Request.Builder().url("https://kyfw.12306.cn/otn/resources/js/framework/station_name.js").build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful)
                val text = response.body?.string().orEmpty()
                text.split('@').mapNotNull { entry ->
                    val parts = entry.split('|')
                    if (parts.size >= 3 && parts[2].matches(Regex("[A-Z]{3}"))) cleanStation(parts[1]) to parts[2] else null
                }.toMap().also { cachedStationCodes = it }
            }
        }.getOrDefault(emptyMap())
    }
}
