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
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    data class Event(val index: Int, val arrival: Long?, val departure: Long?)

    fun events(trip: Trip): List<Event> {
        val base = parse(trip.departureDate, "00:00") ?: return emptyList()
        var previous: Long? = null
        fun resolve(time: String, explicitDay: Int?): Long? {
            val clock = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
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
    }

    fun position(trip: Trip, now: Long): Position? {
        val events = events(trip)
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
