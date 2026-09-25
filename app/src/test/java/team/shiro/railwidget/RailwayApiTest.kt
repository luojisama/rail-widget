package team.shiro.railwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.shiro.railwidget.data.api.RailwayApiService
import team.shiro.railwidget.data.model.Trip

class RailwayApiTest {

    @Test
    fun testPublicRailwayApiIntegration() {
        val testTrip = Trip(
            orderNo = "EQ33472264",
            passengerName = "张三",
            trainCode = "C315",
            departureStation = "昆明南",
            arrivalStation = "普洱",
            departureDate = "2026-09-25",
            departureTime = "16:00",
            carriage = "6车",
            seat = "1D号",
            ticketGate = "16A"
        )

        val enriched = RailwayApiService.enrichTrip(testTrip)
        assertNotNull(enriched)
        assertEquals("C315", enriched.trainCode)

        // If network is reachable, check that train_no and arrivalTime were populated
        if (enriched.trainNo.isNotBlank()) {
            assertTrue(enriched.trainNo.contains("C315"))
            assertEquals("18:38", enriched.arrivalTime)
            assertTrue(enriched.stops.isNotEmpty())
            val puerStop = enriched.stops.find { it.stationName.contains("普洱") }
            assertNotNull(puerStop)
            assertEquals("18:38", puerStop?.arriveTime)
        }
    }
}
