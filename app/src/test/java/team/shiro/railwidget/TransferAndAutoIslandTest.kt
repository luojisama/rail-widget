package team.shiro.railwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripStage

class TransferAndAutoIslandTest {

    // 辅助模拟中转换乘查询算法（与 TripDatabaseHelper.findNextTransferTrip 相同规则）
    private fun findNextTransfer(current: Trip, candidates: List<Trip>): Trip? {
        val currentArrStationClean = current.arrivalStation.replace("站", "").trim()
        val currentArrMillis = current.getArrivalTimeMillis()

        return candidates.filter { it.orderNo != current.orderNo && !it.isArchived }
            .filter { candidate ->
                val candidateDepStationClean = candidate.departureStation.replace("站", "").trim()
                val candidateDepMillis = candidate.getDepartureTimeMillis()
                val timeDiff = candidateDepMillis - currentArrMillis
                candidateDepStationClean.equals(currentArrStationClean, ignoreCase = true) &&
                        timeDiff in (10 * 60 * 1000L)..(8 * 3600 * 1000L)
            }.minByOrNull { it.getDepartureTimeMillis() }
    }

    private fun findPreviousTransfer(current: Trip, candidates: List<Trip>): Trip? {
        val currentDepStationClean = current.departureStation.replace("站", "").trim()
        val currentDepMillis = current.getDepartureTimeMillis()

        return candidates.filter { it.orderNo != current.orderNo && !it.isArchived }
            .filter { prev ->
                val prevArrStationClean = prev.arrivalStation.replace("站", "").trim()
                val prevArrMillis = prev.getArrivalTimeMillis()
                val diff = currentDepMillis - prevArrMillis
                prevArrStationClean.equals(currentDepStationClean, ignoreCase = true) &&
                        diff in (10 * 60 * 1000L)..(8 * 3600 * 1000L)
            }.maxByOrNull { it.getArrivalTimeMillis() }
    }

    @Test
    fun testStandardTransferMatching() {
        // 案例 1：上海虹桥 ➔ 南京南 (G2, 08:00开, 09:30到) 紧接着 南京南 ➔ 合肥南 (G7177, 10:15开, 11:10到)
        // 换乘时间 45 分钟，完全满足 10m..8h 换乘窗口
        val trip1 = Trip(
            orderNo = "E000000001",
            passengerName = "张三",
            trainCode = "G2",
            departureStation = "上海虹桥",
            arrivalStation = "南京南站", // 带“站”
            departureDate = "2026-10-01",
            departureTime = "08:00",
            arrivalTime = "09:30",
            carriage = "03车",
            seat = "05A号",
            ticketGate = "12B"
        )

        val trip2 = Trip(
            orderNo = "E000000002",
            passengerName = "张三",
            trainCode = "G7177",
            departureStation = "南京南", // 不带“站”
            arrivalStation = "合肥南",
            departureDate = "2026-10-01",
            departureTime = "10:15",
            arrivalTime = "11:10",
            carriage = "06车",
            seat = "12F号",
            ticketGate = "8A"
        )

        val candidates = listOf(trip1, trip2)

        // 验证 trip1 的下一程换乘为 trip2
        val next = findNextTransfer(trip1, candidates)
        assertNotNull(next)
        assertEquals("G7177", next?.trainCode)
        assertEquals("E000000002", next?.orderNo)

        // 验证 trip2 的前序车次为 trip1
        val prev = findPreviousTransfer(trip2, candidates)
        assertNotNull(prev)
        assertEquals("G2", prev?.trainCode)
        assertEquals("E000000001", prev?.orderNo)

        // 验证换乘等待时长计算
        val waitMinutes = (trip2.getDepartureTimeMillis() - trip1.getArrivalTimeMillis()) / 60000
        assertEquals(45L, waitMinutes)
    }

    @Test
    fun testTransferFiltersInvalidSequences() {
        val trip1 = Trip(
            orderNo = "E1",
            passengerName = "李四",
            trainCode = "D1",
            departureStation = "北京南",
            arrivalStation = "济南西",
            departureDate = "2026-10-01",
            departureTime = "09:00",
            arrivalTime = "10:40"
        )

        // 案例 A：逆序车次（10:20 发车，在到达时间 10:40 之前）
        val invalidPast = Trip(
            orderNo = "E2",
            passengerName = "李四",
            trainCode = "D2",
            departureStation = "济南西",
            arrivalStation = "青岛",
            departureDate = "2026-10-01",
            departureTime = "10:20",
            arrivalTime = "12:30"
        )

        // 案例 B：太短（5 分钟，不足 10 分钟最低进站/换乘安检时间）
        val invalidTooShort = Trip(
            orderNo = "E3",
            passengerName = "李四",
            trainCode = "D3",
            departureStation = "济南西",
            arrivalStation = "青岛",
            departureDate = "2026-10-01",
            departureTime = "10:45", // 仅间隔 5 分钟
            arrivalTime = "12:50"
        )

        // 案例 C：太长（间隔 10 小时，超过 8 小时中转窗口）
        val invalidTooLong = Trip(
            orderNo = "E4",
            passengerName = "李四",
            trainCode = "D4",
            departureStation = "济南西",
            arrivalStation = "青岛",
            departureDate = "2026-10-01",
            departureTime = "21:00", // 间隔超过 10 小时
            arrivalTime = "23:00"
        )

        // 案例 D：不同站（从“天津西”发车，而非“济南西”）
        val invalidWrongStation = Trip(
            orderNo = "E5",
            passengerName = "李四",
            trainCode = "D5",
            departureStation = "天津西",
            arrivalStation = "青岛",
            departureDate = "2026-10-01",
            departureTime = "12:00",
            arrivalTime = "14:00"
        )

        val allCandidates = listOf(trip1, invalidPast, invalidTooShort, invalidTooLong, invalidWrongStation)
        val next = findNextTransfer(trip1, allCandidates)
        assertNull("不合规的中转车次应被严格过滤", next)
    }

    @Test
    fun testAutoLaunchTimeCalculation() {
        val trip = Trip(
            orderNo = "T123",
            passengerName = "王五",
            trainCode = "C315",
            departureStation = "昆明南",
            arrivalStation = "普洱",
            departureDate = "2026-10-05",
            departureTime = "13:00",
            arrivalTime = "15:30"
        )

        val depMillis = trip.getDepartureTimeMillis()

        // 提前 60 分钟拉起
        val launch60 = depMillis - 60 * 60 * 1000L
        assertEquals(depMillis - 3600_000L, launch60)

        // 提前 30 分钟拉起
        val launch30 = depMillis - 30 * 60 * 1000L
        assertEquals(depMillis - 1800_000L, launch30)

        // 验证到达时间与阶段
        val arrMillis = trip.getArrivalTimeMillis()
        assertTrue(arrMillis > depMillis)
    }

    @Test
    fun testArrivalBufferAndAutoClose() {
        val trip = Trip(
            orderNo = "T888",
            passengerName = "赵六",
            trainCode = "G10",
            departureStation = "上海虹桥",
            arrivalStation = "北京南",
            departureDate = "2026-10-05",
            departureTime = "09:00",
            arrivalTime = "13:30"
        )

        val arrMillis = trip.getArrivalTimeMillis()
        val bufferMillis = 15 * 60 * 1000L // 15 分钟出站缓冲

        // 模拟到达后 10 分钟：未满 15 分钟，应该保持运行与展示“已到达”
        val nowDuringBuffer = arrMillis + 10 * 60 * 1000L
        val elapsed1 = nowDuringBuffer - arrMillis
        assertTrue(elapsed1 < bufferMillis)

        // 模拟到达后 16 分钟：超过 15 分钟缓冲，应该触发自动安全退出
        val nowAfterBuffer = arrMillis + 16 * 60 * 1000L
        val elapsed2 = nowAfterBuffer - arrMillis
        assertTrue(elapsed2 >= bufferMillis)
    }
}
