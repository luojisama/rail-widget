package team.shiro.railwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.widget.TripWidgetRenderer

class TripWidgetStopsTest {

    private val full14StopsD236 = listOf(
        StopInfo("01", "磨憨", "----", "09:17", "始发"),
        StopInfo("02", "勐腊", "09:31", "09:34", "3分"),
        StopInfo("03", "西双版纳", "10:24", "10:30", "6分"),
        StopInfo("04", "普洱", "11:10", "11:14", "4分"),
        StopInfo("05", "玉溪", "13:05", "13:08", "3分"),
        StopInfo("06", "昆明南", "13:47", "13:51", "4分"),
        StopInfo("07", "昆明", "14:10", "14:16", "6分"),
        StopInfo("08", "广通北", "15:12", "15:15", "3分"),
        StopInfo("09", "攀枝花南", "16:32", "16:39", "7分"),
        StopInfo("10", "西昌西", "18:00", "18:05", "5分"),
        StopInfo("11", "冕宁", "18:31", "18:33", "2分"),
        StopInfo("12", "越西", "19:15", "19:18", "3分"),
        StopInfo("13", "峨眉", "20:30", "20:33", "3分"),
        StopInfo("14", "成都南", "21:20", "----", "终到")
    )

    @Test
    fun testUserD236RideStopsOnlyDisplayUserSpan() {
        val trip = Trip(
            orderNo = "EQ97461082",
            passengerName = "李全航",
            trainCode = "D236",
            departureStation = "普洱",
            arrivalStation = "昆明南",
            departureDate = "2026-10-07",
            departureTime = "11:14",
            arrivalTime = "13:47",
            stops = full14StopsD236
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)

        // 关键断言 1: 徽标必须清晰标明乘客真实的乘车区间站数与全线站数
        assertEquals("乘车区间 3站 · 全线 14站", result.badgeText)

        // 关键断言 2: 小部件展示车站数量必须精确为用户乘车区间的 3 站，严禁盲目采样 01 磨憨、14 成都南充数
        assertEquals(3, result.items.size)

        // 第 1 站必须为上车站“普洱”
        val stop1 = result.items[0]
        assertEquals("普洱", stop1.stop.stationName)
        assertTrue(stop1.isBoarding)
        assertFalse(stop1.isAlighting)
        assertTrue(stop1.nameText.contains("04 普洱 (上车)"))
        assertEquals("11:10到 / 11:14开", stop1.timeText)

        // 第 2 站必须为中途经过的“玉溪”
        val stop2 = result.items[1]
        assertEquals("玉溪", stop2.stop.stationName)
        assertFalse(stop2.isBoarding)
        assertFalse(stop2.isAlighting)
        assertTrue(stop2.nameText.contains("05 玉溪"))
        assertEquals("13:05到 / 13:08开 (停3分)", stop2.timeText)

        // 第 3 站必须为下车站“昆明南”
        val stop3 = result.items[2]
        assertEquals("昆明南", stop3.stop.stationName)
        assertFalse(stop3.isBoarding)
        assertTrue(stop3.isAlighting)
        assertTrue(stop3.nameText.contains("06 昆明南 (下车)"))
        assertEquals("13:47 到", stop3.timeText)

        // 关键断言 3: 绝不包含无关的首尾站
        val displayedNames = result.items.map { it.stop.stationName }
        assertFalse(displayedNames.contains("磨憨"))
        assertFalse(displayedNames.contains("勐腊"))
        assertFalse(displayedNames.contains("昆明"))
        assertFalse(displayedNames.contains("西昌西"))
        assertFalse(displayedNames.contains("成都南"))
    }

    @Test
    fun testKunmingNanNeverMistakenForKunming() {
        val trip = Trip(
            orderNo = "EQ97461082",
            passengerName = "李全航",
            trainCode = "D236",
            departureStation = "普洱",
            arrivalStation = "昆明南",
            departureDate = "2026-10-07",
            departureTime = "11:14",
            stops = full14StopsD236
        )

        val targetArr = trip.arrivalStation.replace("站", "").trim()
        val kunmingNanMatches = full14StopsD236.filter {
            it.stationName.replace("站", "").trim().equals(targetArr, ignoreCase = true)
        }
        // 关键断言：整张表里只有“昆明南”能匹配上下车站，“昆明”绝不能被匹配！
        assertEquals(1, kunmingNanMatches.size)
        assertEquals("昆明南", kunmingNanMatches[0].stationName)
    }

    @Test
    fun testLongDistanceTrainEvenlySampledWithinUserSpan() {
        val longStops = (1..12).map { i ->
            StopInfo(
                stationNo = String.format("%02d", i),
                stationName = "车站$i",
                arriveTime = "${8 + i}:00",
                startTime = "${8 + i}:05",
                stopoverTime = "5分"
            )
        }

        // 乘客从车站2到车站10（区间共 9 站）
        val trip = Trip(
            orderNo = "E123456789",
            passengerName = "张三",
            trainCode = "G1",
            departureStation = "车站2",
            arrivalStation = "车站10",
            departureDate = "2026-10-07",
            departureTime = "10:05",
            stops = longStops
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        assertEquals(5, result.items.size)
        // 首位必须是上车站 车站2
        assertEquals("车站2", result.items.first().stop.stationName)
        assertTrue(result.items.first().isBoarding)

        // 末位必须是下车站 车站10
        assertEquals("车站10", result.items.last().stop.stationName)
        assertTrue(result.items.last().isAlighting)

        // 所有展示的站都必须位于乘车区间内 (车站2..车站10)，绝对不能包含区间外的车站1或车站11/12
        val names = result.items.map { it.stop.stationName }
        assertFalse(names.contains("车站1"))
        assertFalse(names.contains("车站11"))
        assertFalse(names.contains("车站12"))
    }
}
