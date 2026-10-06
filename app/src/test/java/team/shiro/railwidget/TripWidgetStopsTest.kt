package team.shiro.railwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.widget.TripWidgetRenderer

class TripWidgetStopsTest {

    // 经典案例 1：D236 中老铁路/成昆线（全线 14 站）
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

    // 经典案例 2：京沪高铁标杆车次 G2（上海虹桥 -> 北京南，全线 6 站）
    private val g2JingHuStops = listOf(
        StopInfo("01", "上海虹桥", "----", "09:00", "始发"),
        StopInfo("02", "常州北", "09:42", "09:44", "2分"),
        StopInfo("03", "南京南", "10:15", "10:17", "2分"),
        StopInfo("04", "济南西", "12:08", "12:10", "2分"),
        StopInfo("05", "天津南", "13:05", "13:07", "2分"),
        StopInfo("06", "北京南", "13:28", "----", "终到")
    )

    // 经典案例 3：广深港跨境干线 G6501（全线 7 站）
    private val g6501GuangShenGang = listOf(
        StopInfo("01", "广州南", "----", "07:00", "始发"),
        StopInfo("02", "庆盛", "07:13", "07:15", "2分"),
        StopInfo("03", "虎门", "07:27", "07:29", "2分"),
        StopInfo("04", "光明城", "07:45", "07:47", "2分"),
        StopInfo("05", "深圳北", "07:58", "08:02", "4分"),
        StopInfo("06", "福田", "08:11", "08:14", "3分"),
        StopInfo("07", "香港西九龙", "08:28", "----", "终到")
    )

    // 经典案例 4：海南东环+西环闭环高铁 C7304（起点海口，终点海口，环线同名站）
    private val c7304HainanLoop = listOf(
        StopInfo("01", "海口", "----", "08:00", "始发"),
        StopInfo("02", "美兰", "08:10", "08:12", "2分"),
        StopInfo("03", "琼海", "08:45", "08:47", "2分"),
        StopInfo("04", "万宁", "09:05", "09:07", "2分"),
        StopInfo("05", "陵水", "09:30", "09:32", "2分"),
        StopInfo("06", "亚龙湾", "09:50", "09:52", "2分"),
        StopInfo("07", "三亚", "10:05", "10:10", "5分"),
        StopInfo("08", "东方", "11:20", "11:22", "2分"),
        StopInfo("09", "临高南", "12:10", "12:12", "2分"),
        StopInfo("10", "海口", "12:45", "----", "终到")
    )

    // 经典案例 5：跨越 25 站的长途普速大列车 K598（包头 -> 广州）
    private val k598LongDistance = (1..25).map { i ->
        val names = listOf(
            "包头", "呼和浩特", "集宁南", "大同", "阳高", "张家口", "宣化",
            "沙城", "北京丰台", "保定", "石家庄", "邢台", "邯郸", "安阳",
            "新乡", "郑州", "许昌", "漯河", "驻马店", "信阳", "广水",
            "汉口", "武昌", "长沙", "广州"
        )
        StopInfo(
            stationNo = String.format("%02d", i),
            stationName = names[i - 1],
            arriveTime = String.format("%02d:00", (i * 2) % 24),
            startTime = String.format("%02d:05", (i * 2) % 24),
            stopoverTime = "5分"
        )
    }

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
        assertEquals("乘车区间 3站 · 全线 14站", result.badgeText)
        assertEquals(3, result.items.size)

        // 第 1 站为上车站“普洱”
        val stop1 = result.items[0]
        assertEquals("普洱", stop1.stop.stationName)
        assertTrue(stop1.isBoarding)
        assertFalse(stop1.isAlighting)
        assertTrue(stop1.nameText.contains("04 普洱 (上车)"))
        assertEquals("11:10到 / 11:14开", stop1.timeText)

        // 第 2 站为中途“玉溪”
        val stop2 = result.items[1]
        assertEquals("玉溪", stop2.stop.stationName)
        assertFalse(stop2.isBoarding)
        assertFalse(stop2.isAlighting)
        assertTrue(stop2.nameText.contains("05 玉溪"))
        assertEquals("13:05到 / 13:08开 (停3分)", stop2.timeText)

        // 第 3 站为下车站“昆明南”
        val stop3 = result.items[2]
        assertEquals("昆明南", stop3.stop.stationName)
        assertFalse(stop3.isBoarding)
        assertTrue(stop3.isAlighting)
        assertTrue(stop3.nameText.contains("06 昆明南 (下车)"))
        assertEquals("13:47 到", stop3.timeText)

        // 绝不包含无关首尾站
        val displayedNames = result.items.map { it.stop.stationName }
        assertFalse(displayedNames.contains("磨憨"))
        assertFalse(displayedNames.contains("昆明"))
        assertFalse(displayedNames.contains("成都南"))
    }

    @Test
    fun testKunmingNanNeverMistakenForKunming() {
        val targetArr = "昆明南"
        val matched = full14StopsD236.filter {
            it.stationName.replace("站", "").trim().equals(targetArr, ignoreCase = true)
        }
        assertEquals(1, matched.size)
        assertEquals("昆明南", matched[0].stationName)
    }

    @Test
    fun testJingHuG2OriginToIntermediate() {
        // G2 从始发站上车，中间站下车：上海虹桥 -> 南京南（区间共 3 站：上海虹桥 -> 常州北 -> 南京南）
        val trip = Trip(
            orderNo = "E111111111",
            passengerName = "张三",
            trainCode = "G2",
            departureStation = "上海虹桥",
            arrivalStation = "南京南",
            departureDate = "2026-10-07",
            departureTime = "09:00",
            stops = g2JingHuStops
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        assertEquals("乘车区间 3站 · 全线 6站", result.badgeText)
        assertEquals(3, result.items.size)

        assertEquals("上海虹桥", result.items[0].stop.stationName)
        assertTrue(result.items[0].isBoarding)
        assertEquals("09:00 开", result.items[0].timeText)

        assertEquals("常州北", result.items[1].stop.stationName)
        assertFalse(result.items[1].isBoarding)
        assertFalse(result.items[1].isAlighting)

        assertEquals("南京南", result.items[2].stop.stationName)
        assertTrue(result.items[2].isAlighting)
        assertEquals("10:15 到", result.items[2].timeText)

        // 验证济南西、北京南等后续站完全不出现
        val names = result.items.map { it.stop.stationName }
        assertFalse(names.contains("济南西"))
        assertFalse(names.contains("北京南"))
    }

    @Test
    fun testJingHuG2IntermediateToDestination() {
        // G2 从中间站上车，终到站下车：济南西 -> 北京南（区间共 3 站：济南西 -> 天津南 -> 北京南）
        val trip = Trip(
            orderNo = "E222222222",
            passengerName = "李四",
            trainCode = "G2",
            departureStation = "济南西",
            arrivalStation = "北京南",
            departureDate = "2026-10-07",
            departureTime = "12:10",
            stops = g2JingHuStops
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        assertEquals("乘车区间 3站 · 全线 6站", result.badgeText)
        assertEquals(3, result.items.size)

        assertEquals("济南西", result.items[0].stop.stationName)
        assertTrue(result.items[0].isBoarding)

        assertEquals("天津南", result.items[1].stop.stationName)

        assertEquals("北京南", result.items[2].stop.stationName)
        assertTrue(result.items[2].isAlighting)
        assertEquals("13:28 到", result.items[2].timeText)

        // 验证前半程的上海虹桥、常州北、南京南完全被剔除
        val names = result.items.map { it.stop.stationName }
        assertFalse(names.contains("上海虹桥"))
        assertFalse(names.contains("南京南"))
    }

    @Test
    fun testJingHuG2FullJourneySampling() {
        // G2 全程乘坐：上海虹桥 -> 北京南（全线 6 站，大于 5 站，触发等距采样）
        val trip = Trip(
            orderNo = "E333333333",
            passengerName = "王五",
            trainCode = "G2",
            departureStation = "上海虹桥",
            arrivalStation = "北京南",
            departureDate = "2026-10-07",
            departureTime = "09:00",
            stops = g2JingHuStops
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        assertEquals(5, result.items.size)

        // 首站强制锁定为上海虹桥 (上车)
        assertEquals("上海虹桥", result.items.first().stop.stationName)
        assertTrue(result.items.first().isBoarding)

        // 末站强制锁定为北京南 (下车)
        assertEquals("北京南", result.items.last().stop.stationName)
        assertTrue(result.items.last().isAlighting)

        // 验证站序严格单调递增
        val indicesInOriginal = result.items.map { g2JingHuStops.indexOf(it.stop) }
        for (i in 0 until indicesInOriginal.size - 1) {
            assertTrue(indicesInOriginal[i] < indicesInOriginal[i + 1])
        }
    }

    @Test
    fun testGuangzhouShenzhenHongKongDisambiguation() {
        // G6501：深圳北 -> 香港西九龙（测试同城车站及跨境站：广州南 vs 深圳北 vs 福田 vs 香港西九龙）
        val trip = Trip(
            orderNo = "E444444444",
            passengerName = "赵六",
            trainCode = "G6501",
            departureStation = "深圳北",
            arrivalStation = "香港西九龙",
            departureDate = "2026-10-07",
            departureTime = "08:02",
            stops = g6501GuangShenGang
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        assertEquals("乘车区间 3站 · 全线 7站", result.badgeText)
        assertEquals(3, result.items.size)

        assertEquals("深圳北", result.items[0].stop.stationName)
        assertTrue(result.items[0].isBoarding)

        assertEquals("福田", result.items[1].stop.stationName)

        assertEquals("香港西九龙", result.items[2].stop.stationName)
        assertTrue(result.items[2].isAlighting)
        assertEquals("08:28 到", result.items[2].timeText)

        // 严禁包含广州南、庆盛、虎门
        val names = result.items.map { it.stop.stationName }
        assertFalse(names.contains("广州南"))
        assertFalse(names.contains("虎门"))
    }

    @Test
    fun testHainanLoopTrainLoopStationMatching() {
        // C7304 海南环岛列车：始发站和终点站同为“海口”！
        // 乘客从“三亚”坐到终点站“海口”（站序 07 三亚 -> 08 东方 -> 09 临高南 -> 10 海口）
        val trip = Trip(
            orderNo = "E555555555",
            passengerName = "钱七",
            trainCode = "C7304",
            departureStation = "三亚",
            arrivalStation = "海口",
            departureDate = "2026-10-07",
            departureTime = "10:10",
            stops = c7304HainanLoop
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        // 关键断言：海口必须匹配到终点站 10 海口，而不是第 01 站海口！区间为 4 站
        assertEquals("乘车区间 4站 · 全线 10站", result.badgeText)
        assertEquals(4, result.items.size)

        assertEquals("三亚", result.items[0].stop.stationName)
        assertTrue(result.items[0].isBoarding)

        assertEquals("东方", result.items[1].stop.stationName)
        assertEquals("临高南", result.items[2].stop.stationName)

        // 最后一站必须是 10 海口 (下车)
        assertEquals("海口", result.items[3].stop.stationName)
        assertEquals("10", result.items[3].stop.stationNo)
        assertTrue(result.items[3].isAlighting)
        assertEquals("12:45 到", result.items[3].timeText)
    }

    @Test
    fun testLongDistance25StopsTrainSampling() {
        // K598 包头 -> 广州，全线 25 站
        // 乘客乘坐中途：石家庄（11） -> 汉口（22），区间共 12 站
        val trip = Trip(
            orderNo = "E666666666",
            passengerName = "孙八",
            trainCode = "K598",
            departureStation = "石家庄",
            arrivalStation = "汉口",
            departureDate = "2026-10-07",
            departureTime = "22:05",
            stops = k598LongDistance
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        assertEquals(5, result.items.size)

        // 首站必为石家庄
        assertEquals("石家庄", result.items.first().stop.stationName)
        assertTrue(result.items.first().isBoarding)

        // 末站必为汉口
        assertEquals("汉口", result.items.last().stop.stationName)
        assertTrue(result.items.last().isAlighting)

        // 所有采样车站都必须位于石家庄（11）到汉口（22）之间，绝不能包含区间外的包头、大同、北京丰台、武昌、长沙、广州
        val displayedNames = result.items.map { it.stop.stationName }
        assertFalse(displayedNames.contains("包头"))
        assertFalse(displayedNames.contains("大同"))
        assertFalse(displayedNames.contains("北京丰台"))
        assertFalse(displayedNames.contains("武昌"))
        assertFalse(displayedNames.contains("广州"))

        // 站序在全线中必须单调递增
        val indices = result.items.map { k598LongDistance.indexOf(it.stop) }
        for (i in 0 until indices.size - 1) {
            assertTrue(indices[i] < indices[i + 1])
        }
    }

    @Test
    fun testAdjacentCommuterTwoStops() {
        // 相邻直达通勤车：上海 -> 昆山南（区间仅 2 站）
        val twoStops = listOf(
            StopInfo("01", "上海", "----", "08:00", "始发"),
            StopInfo("02", "昆山南", "08:18", "08:20", "2分"),
            StopInfo("03", "苏州", "08:35", "----", "终到")
        )

        val trip = Trip(
            orderNo = "E777777777",
            passengerName = "周九",
            trainCode = "G7001",
            departureStation = "上海",
            arrivalStation = "昆山南",
            departureDate = "2026-10-07",
            departureTime = "08:00",
            stops = twoStops
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        assertEquals("乘车区间 2站 · 全线 3站", result.badgeText)
        assertEquals(2, result.items.size)

        assertEquals("上海", result.items[0].stop.stationName)
        assertTrue(result.items[0].isBoarding)
        assertEquals("08:00 开", result.items[0].timeText)

        assertEquals("昆山南", result.items[1].stop.stationName)
        assertTrue(result.items[1].isAlighting)
        assertEquals("08:18 到", result.items[1].timeText)
    }

    @Test
    fun testGracefulFallbackWhenStationsUnmatched() {
        // 极端异常测试：车票站名（如火星站、月球站）完全与时刻表不匹配，系统必须优雅降级，严禁 NPE 闪退
        val trip = Trip(
            orderNo = "E888888888",
            passengerName = "吴十",
            trainCode = "G9999",
            departureStation = "火星基地",
            arrivalStation = "月球轨道",
            departureDate = "2026-10-07",
            departureTime = "12:00",
            stops = g2JingHuStops
        )

        val result = TripWidgetRenderer.resolveWidgetStops(trip)
        assertEquals("共 6 站", result.badgeText)
        assertEquals(5, result.items.size)
        // 验证兜底情况下依然能正常呈现前 5 站或采样站
        assertEquals("上海虹桥", result.items.first().stop.stationName)
    }
}
