package team.shiro.railwidget.data.model

data class StopInfo(
    val stationNo: String,
    val stationName: String,
    val arriveTime: String,
    val startTime: String,
    val stopoverTime: String
)

enum class TripStage(val title: String) {
    UPCOMING("未出行"),
    IN_TRANSIT("在途中"),
    COMPLETED("已结束")
}

data class Trip(
    val orderNo: String,
    val passengerName: String,
    val trainCode: String,
    val trainNo: String = "",
    val departureStation: String,
    val arrivalStation: String,
    val departureDate: String, // YYYY-MM-DD
    val departureTime: String, // HH:mm
    val arrivalTime: String = "", // HH:mm
    val carriage: String, // e.g. "6车" or "06车"
    val seat: String, // e.g. "1D号"
    val seatType: String = "二等座",
    val ticketGate: String = "", // e.g. "16A"
    val ticketType: String = "成人票",
    val price: String = "",
    val stopoverTime: String = "",
    val stops: List<StopInfo> = emptyList(),
    val detailUrl: String = "", // 12306 官方电子客票短链接
    val rawSource: String = "MANUAL",
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
) {
    /**
     * 计算行程处于哪个核心阶段：未出行、在途中、已结束
     */
    fun getStage(currentTimeMillis: Long = System.currentTimeMillis()): TripStage {
        val (depMillis, arrMillis) = calculateTimes()
        return when {
            currentTimeMillis < depMillis -> TripStage.UPCOMING
            currentTimeMillis in depMillis..arrMillis -> TripStage.IN_TRANSIT
            else -> TripStage.COMPLETED
        }
    }

    /**
     * 规范出行提示：候车中、正在检票、停止检票、运行中、已到达
     */
    fun computeStatus(currentTimeMillis: Long = System.currentTimeMillis()): String {
        val (depMillis, arrMillis) = calculateTimes()
        val checkInStart = depMillis - 20 * 60 * 1000
        val checkInStop = depMillis - 5 * 60 * 1000

        return when {
            currentTimeMillis >= arrMillis -> "已到达"
            currentTimeMillis >= depMillis -> "运行中"
            currentTimeMillis >= checkInStop -> "停止检票"
            currentTimeMillis >= checkInStart -> "正在检票"
            else -> "候车中"
        }
    }

    private fun calculateTimes(): Pair<Long, Long> {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            val depFullStr = "$departureDate $departureTime"
            val depDate = format.parse(depFullStr)
            val depMillis = depDate?.time ?: System.currentTimeMillis()

            val arrMillis = if (arrivalTime.isNotBlank()) {
                val arrFullStr = "$departureDate $arrivalTime"
                val parsedArr = format.parse(arrFullStr)?.time ?: (depMillis + 2 * 3600 * 1000)
                // 若到站时间跨日（数字小于发车时间），按次日推算
                if (parsedArr < depMillis) parsedArr + 24 * 3600 * 1000 else parsedArr
            } else {
                depMillis + 2 * 3600 * 1000
            }
            Pair(depMillis, arrMillis)
        } catch (_: Exception) {
            Pair(System.currentTimeMillis(), System.currentTimeMillis() + 2 * 3600 * 1000)
        }
    }

    fun getCleanTicketGate(): String {
        if (ticketGate.isBlank()) return "暂无"
        val regex = Regex("([0-9]+[A-Za-z]?|[A-Za-z]?[0-9]+)")
        val match = regex.find(ticketGate)
        return match?.value ?: ticketGate
    }
}

