package team.shiro.railwidget.data.model

data class StopInfo(
    val stationNo: String,
    val stationName: String,
    val arriveTime: String,
    val startTime: String,
    val stopoverTime: String
)

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
    val rawSource: String = "MANUAL",
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
) {
    /**
     * Compute human-readable check-in / travel status:
     * - 候车中 (Waiting)
     * - 正在检票 (Checking in: typically 15-20 min before departure)
     * - 停止检票 (Stopped: 5 min before departure)
     * - 已发车 (Departed)
     * - 已到达 (Arrived)
     */
    fun computeStatus(currentTimeMillis: Long = System.currentTimeMillis()): String {
        return try {
            val depFullStr = "$departureDate $departureTime"
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            val depDate = format.parse(depFullStr) ?: return "候车中"
            val depMillis = depDate.time

            val arrMillis = if (arrivalTime.isNotBlank()) {
                val arrFullStr = "$departureDate $arrivalTime"
                format.parse(arrFullStr)?.time ?: (depMillis + 2 * 3600 * 1000)
            } else {
                depMillis + 2 * 3600 * 1000
            }

            val checkInStart = depMillis - 20 * 60 * 1000
            val checkInStop = depMillis - 5 * 60 * 1000

            when {
                currentTimeMillis >= arrMillis -> "已到达"
                currentTimeMillis >= depMillis -> "列车运行中"
                currentTimeMillis >= checkInStop -> "停止检票"
                currentTimeMillis >= checkInStart -> "正在检票"
                else -> "候车中"
            }
        } catch (_: Exception) {
            "候车中"
        }
    }

    /**
     * Returns clean ticket gate string (e.g. "16A" from "呈贡昆明南站 16A")
     */
    fun getCleanTicketGate(): String {
        if (ticketGate.isBlank()) return "暂无"
        val regex = Regex("([0-9]+[A-Za-z]?|[A-Za-z]?[0-9]+)")
        val match = regex.find(ticketGate)
        return match?.value ?: ticketGate
    }
}
