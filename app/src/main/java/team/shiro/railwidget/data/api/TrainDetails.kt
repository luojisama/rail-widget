package team.shiro.railwidget.data.api

import org.json.JSONObject
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripTimeline
import team.shiro.railwidget.data.model.StopInfo

/** Parsing is separate from transport so missing or changed upstream fields are testable. */
object TrainDetails {
    private fun JSONObject.text(key: String) = optString(key).takeUnless { it == "null" || it == "--" }?.trim().orEmpty()

    fun applyInfo(trip: Trip, response: JSONObject, now: Long): Trip {
        val detail = response.optJSONObject("data")?.optJSONObject("trainDetail") ?: return trip
        val codes = detail.text("stationTrainCodeAll").split('/', '／', ',')
        if (codes.none { it.equals(trip.trainCode, true) }) return trip
        val rows = detail.optJSONArray("stopTime") ?: return trip
        val indexed = (0 until rows.length()).map { rows.getJSONObject(it) }
        val first = indexed.firstOrNull() ?: return trip
        val baseStops = trip.stops.ifEmpty {
            indexed.map { row -> StopInfo(row.text("stationNo"), row.text("stationName"),
                row.text("arriveTime"), row.text("startTime"), row.text("stopover_time")) }
        }
        val distances = indexed.map { it.text("distance").toDoubleOrNull()?.takeIf { km -> km.isFinite() && km >= 0 } }
        // Only a complete, monotone series starting at the train origin is usable as cumulative km.
        val originMatches = first.text("start_station_name").let { it.isBlank() || TripTimeline.station(it) == TripTimeline.station(first.text("stationName")) }
        val endMatches = indexed.last().text("end_station_name").let { it.isBlank() || TripTimeline.station(it) == TripTimeline.station(indexed.last().text("stationName")) }
        val hasMileage = originMatches && endMatches && distances.size > 1 && distances.first() == 0.0 && distances.all { it != null } &&
            distances.zipWithNext().all { (a, b) -> b!! >= a!! } && distances.last()!! > 0
        val stops = baseStops.map { stop ->
            val matches = indexed.filter { TripTimeline.station(it.text("stationName")) == TripTimeline.station(stop.stationName) }
            val row = matches.singleOrNull() ?: matches.find { it.text("stationNo").toIntOrNull() == stop.stationNo.toIntOrNull() }
            if (row == null) stop else stop.copy(
                ticketGate = row.text("wicket").ifBlank { stop.ticketGate },
                // A gate is not a platform. Only the explicitly named platform field is accepted.
                platform = row.text("platform").ifBlank { stop.platform },
                mileageKm = if (hasMileage) distances[indexed.indexOf(row)] else stop.mileageKm,
                arrivalDay = row.text("dayDifference").toIntOrNull() ?: stop.arrivalDay,
                actualArrival = timestamp(row.text("fact_arraive_date_time")) ?: stop.actualArrival,
                actualDeparture = timestamp(row.text("fact_train_date_time")) ?: stop.actualDeparture
            )
        }
        val boarding = stops.find { TripTimeline.station(it.stationName) == TripTimeline.station(trip.departureStation) }
        val boardingIndex = stops.indexOf(boarding)
        val alighting = stops.drop(boardingIndex + 1).find { TripTimeline.station(it.stationName) == TripTimeline.station(trip.arrivalStation) }
        val rideMileage = if (boarding?.mileageKm != null && alighting?.mileageKm != null)
            (alighting.mileageKm - boarding.mileageKm).takeIf { it > 0 } else null
        return trip.copy(
            stops = stops,
            trainModel = first.text("jiaolu_train_style").ifBlank { trip.trainModel },
            railwayBureau = first.text("jiaolu_corporation_code").takeUnless { it.matches(Regex("[A-Za-z0-9]+")) }.orEmpty().ifBlank { trip.railwayBureau },
            ticketGate = if (trip.ticketGateManual) trip.ticketGate else boarding?.ticketGate.orEmpty().ifBlank { trip.ticketGate },
            platform = boarding?.platform.orEmpty().ifBlank { trip.platform },
            totalMileageKm = if (hasMileage) distances.last() else trip.totalMileageKm,
            rideMileageKm = rideMileage ?: trip.rideMileageKm,
            liveCheckedAt = now, dataCheckedAt = now,
            dataSource = (trip.dataSource.split(" · ") + "12306列车详情").filter { it.isNotBlank() }.distinct().joinToString(" · ")
        )
    }

    fun applyStyle(trip: Trip, response: JSONObject, now: Long): Trip {
        val data = response.optJSONObject("content")?.optJSONObject("data") ?: return trip
        val model = data.text("trainStyle")
        val info = data.optJSONArray("carInfo")
        var dining = ""
        for (i in 0 until (info?.length() ?: 0)) {
            val row = info!!.getJSONObject(i)
            if (row.text("pictureName") in listOf("餐车", "餐车位置", "餐车车厢")) dining = row.text("pictureValue")
        }
        if (model.isBlank() && dining.isBlank()) return trip
        return trip.copy(trainModel = model.ifBlank { trip.trainModel }, diningCar = dining.ifBlank { trip.diningCar },
            dataCheckedAt = now, dataSource = (trip.dataSource.split(" · ") + "12306车型").filter { it.isNotBlank() }.distinct().joinToString(" · "))
    }

    private fun timestamp(value: String): Long? {
        val normalized = value.replace('T', ' ')
        if (normalized.length < 16) return null
        return TripTimeline.parse(normalized.take(10), normalized.substring(11, 16))
    }
}
