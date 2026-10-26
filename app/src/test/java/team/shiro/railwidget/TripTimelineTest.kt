package team.shiro.railwidget

import org.junit.Assert.*
import org.junit.Test
import team.shiro.railwidget.data.model.*
import java.util.TimeZone

class TripTimelineTest {
    private fun trip() = Trip("t", "乘客", "Z1", departureStation = "乙", arrivalStation = "丁",
        departureDate = "2026-10-26", departureTime = "23:30", arrivalTime = "02:00", stops = listOf(
            StopInfo("01", "甲", "----", "22:00", "始发"),
            StopInfo("02", "乙", "23:20", "23:30", "10"),
            StopInfo("03", "丙", "00:30", "00:40", "10"),
            StopInfo("04", "丁", "02:00", "----", "终到")
        ))
    private fun at(date: String, time: String) = TripTimeline.parse(date, time)!!

    @Test fun legacyInvalidDatesCannotCrashStopRendering() {
        val legacy = trip().copy(departureDate = "2026年10月26日")
        assertNull(legacy.currentPosition(at("2026-10-26", "23:35")))
    }
    @Test fun acceptsSingleDigitHoursFromImportedTickets() {
        assertEquals(at("2026-10-26", "08:00"), TripTimeline.parse("2026-10-26", "8:00"))
    }
    @Test fun timezoneDoesNotChangeRailwayTimes() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(at("2026-10-26", "23:30"), trip().getDepartureTimeMillis())
            assertEquals(at("2026-10-27", "02:00"), trip().getArrivalTimeMillis())
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun arrivalBoundaryHasConsistentStatusAndStage() {
        val trip = trip()
        val arrival = trip.getArrivalTimeMillis()
        assertEquals(TripStage.IN_TRANSIT, trip.getStage(arrival - 1))
        assertEquals(TripStage.COMPLETED, trip.getStage(arrival))
        assertEquals("已到达", trip.computeStatus(arrival))
    }
    @Test fun highlightsStopAndNextStopAcrossMidnight() {
        val t = trip()
        assertNull(t.currentPosition(at("2026-10-26", "22:30")))
        assertEquals(TripTimeline.Position(2, false, false), t.currentPosition(at("2026-10-27", "00:10")))
        assertEquals(TripTimeline.Position(2, true, false), t.currentPosition(at("2026-10-27", "00:35")))
        assertEquals(TripTimeline.Position(3, false, false), t.currentPosition(at("2026-10-27", "00:40")))
        assertNull(t.currentPosition(at("2026-10-27", "02:00")))
    }
    @Test fun boardingAfterTrainFirstMidnightUsesTicketDate() {
        val t = trip().copy(departureStation = "丙", departureTime = "00:40")
        assertEquals(at("2026-10-26", "02:00"), t.getArrivalTimeMillis())
        assertEquals("2026-10-25", TripTimeline.originDate(t))
        assertEquals(TripTimeline.Position(2, true, false), t.currentPosition(at("2026-10-26", "00:35")))
    }
    @Test fun handlesMoreThanOneMidnightAndMidnightDeparture() {
        val t = trip().copy(departureStation = "甲", departureTime = "00:00", stops = listOf(
            StopInfo("1", "甲", "----", "00:00", ""),
            StopInfo("2", "乙", "23:00", "23:10", ""),
            StopInfo("3", "丙", "22:00", "22:10", ""),
            StopInfo("4", "丁", "02:00", "----", "")
        ))
        assertEquals(at("2026-10-28", "02:00"), t.getArrivalTimeMillis())
        assertEquals(TripStage.UPCOMING, t.getStage(at("2026-10-25", "23:59")))
    }
    @Test fun freshActualArrivalOverridesPublishedSchedule() {
        val now = at("2026-10-27", "01:00")
        val t = trip().let { it.copy(stops = it.stops.mapIndexed { i, stop ->
            if (i == 2) stop.copy(actualArrival = now - 60_000L) else stop
        }, liveCheckedAt = now) }
        assertEquals(TripTimeline.Position(2, true, true), t.currentPosition(now))
        assertFalse(t.currentPosition(now + 360_000L)!!.realtime)
    }
    @Test fun explicitDaysSupportLongGaps() {
        val t = trip().copy(stops = trip().stops.mapIndexed { i, s ->
            if (i == 3) s.copy(arrivalDay = 3) else s
        })
        assertEquals(at("2026-10-29", "02:00"), t.getArrivalTimeMillis())
    }
}
