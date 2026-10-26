package team.shiro.railwidget

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import team.shiro.railwidget.data.api.TrainDetails
import team.shiro.railwidget.data.model.*

class TrainDetailsTest {
    private val trip = Trip("t", "甲", "G101", departureStation = "北京南", arrivalStation = "上海虹桥",
        departureDate = "2026-10-26", departureTime = "08:00", ticketGate = "旧检票口",
        stops = listOf(StopInfo("01", "北京南", "----", "08:00", "始发"), StopInfo("02", "上海虹桥", "12:30", "----", "终到")))
    private fun response(code: String = "G101") = JSONObject("""{"data":{"trainDetail":{
        "stationTrainCodeAll":"$code","stopTime":[{"stationNo":"01","stationName":"北京南",
        "wicket":"12A","platform":"7","jiaolu_train_style":"CR400AF","jiaolu_corporation_code":"北京局集团",
        "fact_train_date_time":"2026-10-26 08:02:00"}]}}}""")

    @Test fun gateAndPlatformRemainSeparate() {
        val result = TrainDetails.applyInfo(trip, response(), 100)
        assertEquals("12A", result.ticketGate)
        assertEquals("7", result.platform)
        assertEquals("CR400AF", result.trainModel)
        assertEquals("北京局集团", result.railwayBureau)
        assertEquals(TripTimeline.parse("2026-10-26", "08:02"), result.stops[0].actualDeparture)
    }
    @Test fun wrongTrainAndEmptyResponsesDoNotChangeTicket() {
        assertEquals(trip, TrainDetails.applyInfo(trip, response("G102"), 100))
        assertEquals(trip, TrainDetails.applyInfo(trip, JSONObject("{}"), 100))
        assertEquals(trip, TrainDetails.applyStyle(trip, JSONObject("{}"), 100))
    }
    @Test fun manualGateSurvivesOnlineRefresh() {
        val result = TrainDetails.applyInfo(trip.copy(ticketGateManual = true), response(), 100)
        assertEquals(trip.ticketGate, result.ticketGate)
        assertEquals("7", result.platform)
    }
    @Test fun missingFieldsDoNotEraseExistingMetadata() {
        val t = trip.copy(trainModel = "旧车型", railwayBureau = "原担当", platform = "3")
        val json = response()
        val first = json.getJSONObject("data").getJSONObject("trainDetail").getJSONArray("stopTime").getJSONObject(0)
        first.remove("jiaolu_train_style"); first.remove("jiaolu_corporation_code"); first.remove("platform")
        assertEquals("旧车型", TrainDetails.applyInfo(t, json, 100).trainModel)
        assertEquals("原担当", TrainDetails.applyInfo(t, json, 100).railwayBureau)
        assertEquals("3", TrainDetails.applyInfo(t, json, 100).platform)
    }
    @Test fun acceptsExplicitDiningCarAndDoesNotInferFromModel() {
        val response = JSONObject("""{"content":{"data":{"trainStyle":"CR400AF","carInfo":[{"pictureName":"餐车位置","pictureValue":"05车"}]}}}""")
        assertEquals("05车", TrainDetails.applyStyle(trip, response, 100).diningCar)
        response.getJSONObject("content").getJSONObject("data").remove("carInfo")
        assertEquals("", TrainDetails.applyStyle(trip, response, 100).diningCar)
    }
    @Test fun calculatesTrainAndRideMileageFromCumulativeSeries() {
        val json = JSONObject("""{"data":{"trainDetail":{"stationTrainCodeAll":"G101","stopTime":[
            {"stationNo":"01","stationName":"北京南","distance":"0","startTime":"08:00"},
            {"stationNo":"02","stationName":"南京南","distance":"1023","arriveTime":"11:00","startTime":"11:05"},
            {"stationNo":"03","stationName":"上海虹桥","distance":"1318","arriveTime":"12:30"}]}}}""")
        val t = trip.copy(departureStation = "南京南", departureTime = "11:05", stops = emptyList())
        val result = TrainDetails.applyInfo(t, json, 100)
        assertEquals(1318.0, result.totalMileageKm!!, 0.01)
        assertEquals(295.0, result.rideMileageKm!!, 0.01)
        assertEquals(1023.0, result.stops[1].mileageKm!!, 0.01)
        // A segment-distance series cannot be treated as cumulative distance.
        json.getJSONObject("data").getJSONObject("trainDetail").getJSONArray("stopTime").getJSONObject(2).put("distance", "295")
        assertNull(TrainDetails.applyInfo(t, json, 100).totalMileageKm)
    }
    @Test fun incompleteMileageDoesNotEraseCachedMileage() {
        val t = trip.copy(totalMileageKm = 1318.0, rideMileageKm = 1318.0)
        assertEquals(1318.0, TrainDetails.applyInfo(t, response(), 100).totalMileageKm!!, 0.01)
    }
}
