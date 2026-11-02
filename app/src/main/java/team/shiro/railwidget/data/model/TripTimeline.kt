package team.shiro.railwidget.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** All railway dates are China dates, independent of the phone's timezone. */
object TripTimeline {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private const val DAY = 86_400_000L
    fun station(value: String) = value.trim().removeSuffix("站")
    fun parse(date: String, time: String): Long? = runCatching {
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time.trim().padStart(5, '0'))).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    data class Event(val index: Int, val arrival: Long?, val departure: Long?)

    fun originDate(trip: Trip): String {
        val first = events(trip).firstOrNull()
        val time = first?.departure ?: first?.arrival ?: return trip.departureDate
        return java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toString()
    }

    fun events(trip: Trip): List<Event> {
        val base = parse(trip.departureDate, "00:00") ?: return emptyList()
        var previous: Long? = null
        fun resolve(time: String, explicitDay: Int?): Long? {
            val clock = runCatching { LocalTime.parse(time.trim().padStart(5, '0')) }.getOrNull() ?: return null
            var value = base + clock.toSecondOfDay() * 1000L + (explicitDay ?: 0) * DAY
            if (explicitDay == null) while (previous != null && value < previous!!) value += DAY
            previous = value
            return value
        }
        val raw = trip.stops.mapIndexed { i, s -> Event(i, resolve(s.arriveTime, s.arrivalDay), resolve(s.startTime, s.departureDay)) }
        val boarding = trip.stops.indexOfFirst { station(it.stationName) == station(trip.departureStation) }
        val boardingTime = raw.getOrNull(boarding)?.departure ?: raw.getOrNull(boarding)?.arrival
        val dep = parse(trip.departureDate, trip.departureTime)
        val shift = if (boardingTime != null && dep != null) Math.floorDiv(boardingTime - dep + DAY / 2, DAY) * DAY else 0L
        return raw.map { it.copy(arrival = it.arrival?.minus(shift), departure = it.departure?.minus(shift)) }
    }

    fun arrival(trip: Trip): Long {
        val dep = parse(trip.departureDate, trip.departureTime) ?: 0L
        val boarding = trip.stops.indexOfFirst { station(it.stationName) == station(trip.departureStation) }
        val end = trip.stops.indices.firstOrNull { it > boarding && station(trip.stops[it].stationName) == station(trip.arrivalStation) }
        events(trip).getOrNull(end ?: -1)?.arrival?.let { if (it >= dep) return it }
        var result = parse(trip.departureDate, trip.arrivalTime) ?: return dep + 2 * 3_600_000L
        if (trip.arrivalDayOffset != null) result += trip.arrivalDayOffset * DAY
        else if (result < dep) result += DAY
        return result
    }

    data class Position(val index: Int, val atStation: Boolean, val realtime: Boolean) {
        fun label(trip: Trip): String = (if (atStation) "当前到站：" else "下一站：") +
            trip.stops[index].stationName + if (realtime) " · 实时到发" else " · 按时刻表推算"

        fun stationName(trip: Trip): String = trip.stops.getOrNull(index)?.stationName.orEmpty()

        fun statusBadge(): String = if (atStation) "当前停靠" else "即将到达"

        fun sourceBadge(): String = if (realtime) "实时到发" else "时刻表推算"

        fun detailLabel(trip: Trip, now: Long = System.currentTimeMillis()): String {
            val stop = trip.stops.getOrNull(index) ?: return label(trip)
            val ev = events(trip).getOrNull(index)
            return if (atStation) {
                val depTime = stop.startTime.takeIf { it.isNotBlank() && !it.contains("-") }
                val depSuffix = if (depTime != null) " · $depTime 开" else ""
                "当前到站：${stop.stationName}$depSuffix"
            } else {
                val arrTime = stop.arriveTime.takeIf { it.isNotBlank() && !it.contains("-") } ?: stop.startTime
                val etaMillis = ev?.arrival ?: ev?.departure
                val mins = if (etaMillis != null && etaMillis > now) ((etaMillis - now + 59_999L) / 60_000L) else null
                val timePart = if (arrTime.isNotBlank()) " · $arrTime 到" else ""
                val etaPart = if (mins != null && mins in 1..600) "（约 ${mins} 分钟）" else ""
                "下一站：${stop.stationName}$timePart$etaPart"
            }
        }
    }

    fun progress(trip: Trip, now: Long = System.currentTimeMillis()): Float {
        val dep = trip.getDepartureTimeMillis()
        val arr = trip.getArrivalTimeMillis()
        if (dep <= 0L || arr <= dep) return 0f
        return when {
            now <= dep -> 0f
            now >= arr -> 1f
            else -> ((now - dep).toDouble() / (arr - dep).toDouble()).toFloat().coerceIn(0f, 1f)
        }
    }

    fun position(trip: Trip, now: Long): Position? {
        val events = events(trip)
        if (events.size != trip.stops.size) return null
        val board = trip.stops.indexOfFirst { station(it.stationName) == station(trip.departureStation) }
        val end = trip.stops.indices.firstOrNull { it > board && station(trip.stops[it].stationName) == station(trip.arrivalStation) } ?: return null
        if (board < 0) return null
        val fresh = trip.liveCheckedAt > 0 && now - trip.liveCheckedAt in 0..300_000L
        for (i in board..end) {
            val stop = trip.stops[i]
            val event = events[i]
            val realArr = if (fresh) stop.actualArrival?.takeIf { it <= now } else null
            val realDep = if (fresh) stop.actualDeparture?.takeIf { it <= now } else null
            if (realDep != null) continue
            val arr = realArr ?: event.arrival ?: event.departure ?: continue
            val dep = event.departure ?: arr
            if (now < arr) return if (i > board) Position(i, false, false) else null
            if (i == end && now >= arr) return null
            // A recent reported arrival is useful even when the published departure has passed.
            if (now < dep || (realArr != null && now - realArr < 600_000L)) {
                return Position(i, true, realArr != null)
            }
        }
        return null
    }
}
