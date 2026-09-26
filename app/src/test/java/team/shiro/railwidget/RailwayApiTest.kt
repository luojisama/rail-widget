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

    @Test
    fun testPlatformStringCleaning() {
        assertEquals("17A/B", RailwayApiService.cleanPlatformString("检票口17A、17B"))
        assertEquals("13A/B", RailwayApiService.cleanPlatformString("检票口13A13B"))
        assertEquals("17A/B", RailwayApiService.cleanPlatformString("检票口17A 17B"))
        assertEquals("17A/18B", RailwayApiService.cleanPlatformString("检票口17A、18B"))
        assertEquals("A5", RailwayApiService.cleanPlatformString("检票口A5"))
        assertEquals("一层检票口2", RailwayApiService.cleanPlatformString("检票口一层检票口2"))
        assertEquals("16A", RailwayApiService.cleanPlatformString("16A"))
    }

    @Test
    fun testLiveTicketGateQuery() {
        val testTrip = Trip(
            orderNo = "E123456789",
            passengerName = "李四",
            trainCode = "C2001",
            departureStation = "北京南",
            arrivalStation = "天津",
            departureDate = "2026-09-26",
            departureTime = "06:00",
            carriage = "2车",
            seat = "15A号",
            ticketGate = "" // Blank, expect enrich to populate it
        )

        val enriched = RailwayApiService.enrichTrip(testTrip)
        assertNotNull(enriched)
        if (enriched.trainNo.isNotBlank()) {
            // Check that ticketGate was successfully fetched from 12306
            assertTrue("Ticket gate should not be blank", enriched.ticketGate.isNotBlank())
            assertTrue("Ticket gate should contain 20", enriched.ticketGate.contains("20"))
        }
    }
}
