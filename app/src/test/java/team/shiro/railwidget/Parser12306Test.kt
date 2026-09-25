package team.shiro.railwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import team.shiro.railwidget.data.parser.Parser12306

class Parser12306Test {

    @Test
    fun testReal12306EmailParsing() {
        val emailContent = """
            发件人： 12306 <12306@rails.com.cn> 发件时间： 2026年9月24日 11:11 收件人： user@example.com 主题： 网上购票系统-用户支付通知
            尊敬的 张三先生：
            您好！
            您于2026年09月24日在中国铁路客户服务中心网站( 12306.cn ) 成功购买了1张车票，票款共计161.00元，订单号码 EQ33472264 。
            所购车票信息如下：
            1.张三，2026年09月25日16:00开，昆明南站-普洱站，C315次列车，6车1D号，二等座，成人票，票价161.0元，检票口呈贡昆明南站 16A，电子客票。
        """.trimIndent()

        val trips = Parser12306.parseEmail(emailContent)
        assertEquals(1, trips.size)

        val trip = trips[0]
        assertEquals("EQ33472264", trip.orderNo)
        assertEquals("张三", trip.passengerName)
        assertEquals("C315", trip.trainCode)
        assertEquals("昆明南", trip.departureStation)
        assertEquals("普洱", trip.arrivalStation)
        assertEquals("2026-09-25", trip.departureDate)
        assertEquals("16:00", trip.departureTime)
        assertEquals("6车", trip.carriage)
        assertEquals("1D号", trip.seat)
        assertEquals("二等座", trip.seatType)
        assertEquals("16A", trip.ticketGate)
    }

    @Test
    fun testReal12306SmsParsing() {
        val smsContent = "【铁路12306】订单EQ33472264：张三先生，您已购9月25日C315次6车1D号（二等座）昆明南站16:00开，普洱站。检票口：16A。请凭有效身份证件乘车。"

        val trip = Parser12306.parseSms(smsContent)
        assertNotNull(trip)
        trip!!

        assertEquals("EQ33472264", trip.orderNo)
        assertEquals("张三", trip.passengerName)
        assertEquals("C315", trip.trainCode)
        assertEquals("6车", trip.carriage)
        assertEquals("1D号", trip.seat)
        assertEquals("16A", trip.ticketGate)
        assertEquals("16:00", trip.departureTime)
    }

    @Test
    fun testStandard12306SmsWithShortLink() {
        // 测试截图中典型的 12306 官方极简短信格式（含发车时间、出发站及详情短链接，使用脱敏姓名）
        val sms = "【12306】张三购票成功，8月15日D3236次，杭州东站13:20开。详情点击s.12306.cn/s/g/kQwueJ"
        val trip = Parser12306.parseSms(sms)
        assertNotNull(trip)
        trip!!

        assertEquals("张三", trip.passengerName)
        assertEquals("D3236", trip.trainCode)
        assertEquals("杭州东", trip.departureStation)
        assertEquals("13:20", trip.departureTime)
        assertEquals("https://s.12306.cn/s/g/kQwueJ", trip.detailUrl)
    }

    @Test
    fun testJingHuHighSpeedTrainSms() {
        // 京沪高铁标杆车次 G2 测试
        val sms = "【12306】李四购票成功，10月1日G2次，上海虹桥站09:00开。详情点击s.12306.cn/s/g/example"
        val trip = Parser12306.parseSms(sms)
        assertNotNull(trip)
        trip!!

        assertEquals("李四", trip.passengerName)
        assertEquals("G2", trip.trainCode)
        assertEquals("上海虹桥", trip.departureStation)
        assertEquals("09:00", trip.departureTime)
    }

    @Test
    fun testHuKunHighSpeedTrainSms() {
        // 沪昆高铁干线车次 G1373 测试
        val sms = "【12306】王五购票成功，10月2日G1373次，上海虹桥站08:50开。详情点击s.12306.cn/s/g/hukun"
        val trip = Parser12306.parseSms(sms)
        assertNotNull(trip)
        trip!!

        assertEquals("王五", trip.passengerName)
        assertEquals("G1373", trip.trainCode)
        assertEquals("上海虹桥", trip.departureStation)
        assertEquals("08:50", trip.departureTime)
    }

    @Test
    fun testFormatReleaseNotesAndMirrors() {
        val rawMd = """
            ### 🚄 铁行卡片 v1.0.1 更新日志
            #### ✨ 新增特性
            - **更名优化**：正式命名为`铁行卡片`
            - **短信同步**：支持主动扫描
            1. 第一项说明
        """.trimIndent()

        val formatted = team.shiro.railwidget.sync.UpdateChecker.formatReleaseNotes(rawMd)
        // 验证不再包含 ###, **, ` 等 markdown 标记
        org.junit.Assert.assertFalse(formatted.contains("###"))
        org.junit.Assert.assertFalse(formatted.contains("**"))
        org.junit.Assert.assertFalse(formatted.contains("`"))
        org.junit.Assert.assertTrue(formatted.contains("• 更名优化：正式命名为铁行卡片"))

        // 验证镜像生成
        val testUrl = "https://github.com/luojisama/rail-widget/releases/download/v1.0.1/test.apk"
        val mirrorUrl = team.shiro.railwidget.sync.UpdateChecker.getMirrorUrl(testUrl)
        org.junit.Assert.assertTrue(mirrorUrl.startsWith("https://ghfast.top/"))
    }

    @Test
    fun testSmsWithDepAndArrStationsAndTimes() {
        val sms = "【铁路12306】订单号E123456789，张三您好，您购买9月25日G2次列车上海虹桥站14:00开、北京南站18:28到，06车01D号二等座，票价626.0元，检票口16A。祝您旅途愉快！"
        val trip = Parser12306.parseSms(sms)
        assertNotNull(trip)
        trip!!

        assertEquals("E123456789", trip.orderNo)
        assertEquals("张三", trip.passengerName)
        assertEquals("G2", trip.trainCode)
        assertEquals("上海虹桥", trip.departureStation)
        assertEquals("14:00", trip.departureTime)
        assertEquals("北京南", trip.arrivalStation)
        assertEquals("18:28", trip.arrivalTime)
        assertEquals("06车", trip.carriage)
        assertEquals("01D号", trip.seat)
        assertEquals("二等座", trip.seatType)
        assertEquals("626.0元", trip.price)
        assertEquals("16A", trip.ticketGate)
    }

    @Test
    fun testHistoricalSmsTimestampYearDeduction() {
        // 模拟 2023年11月20日 接收到的购票短信，短信正文仅有“11月21日”
        val cal = java.util.Calendar.getInstance()
        cal.set(2023, java.util.Calendar.NOVEMBER, 20, 10, 0, 0)
        val smsTimestamp = cal.timeInMillis

        val sms = "【铁路12306】张三先生，您已购11月21日G1373次列车上海虹桥站08:30开、昆明南站19:15到，03车05A号二等座，订单号E888888888。"
        val trip = Parser12306.parseSms(sms, smsTimestamp)
        assertNotNull(trip)
        trip!!

        assertEquals("E888888888", trip.orderNo)
        assertEquals("张三", trip.passengerName)
        assertEquals("G1373", trip.trainCode)
        assertEquals("2023-11-21", trip.departureDate)
        assertEquals("上海虹桥", trip.departureStation)
        assertEquals("08:30", trip.departureTime)
        assertEquals("昆明南", trip.arrivalStation)
        assertEquals("19:15", trip.arrivalTime)
    }

    @Test
    fun testUserScreenshotRealSms() {
        val sms1 = "【12306】李四购票成功，8月15日D3236次，杭州东站13:20开。详情点击s.12306.cn/s/g/kQwueJ"
        val trip1 = Parser12306.parseSms(sms1)
        assertNotNull(trip1)
        assertEquals("李四", trip1!!.passengerName)
        assertEquals("D3236", trip1.trainCode)
        assertEquals("杭州东", trip1.departureStation)
        assertEquals("13:20", trip1.departureTime)
        assertEquals("https://s.12306.cn/s/g/kQwueJ", trip1.detailUrl)

        val sms2 = "【12306】李四改签成功，1月10日D274次，昆明站16:05开。详情点击s.12306.cn/s/l/PpXQoj"
        val trip2 = Parser12306.parseSms(sms2)
        assertNotNull(trip2)
        assertEquals("李四", trip2!!.passengerName)
        assertEquals("D274", trip2.trainCode)
        assertEquals("昆明", trip2.departureStation)
        assertEquals("16:05", trip2.departureTime)
        assertEquals("https://s.12306.cn/s/l/PpXQoj", trip2.detailUrl)

        val sms3 = "【12306】李四购票成功，2月23日C308次，普洱站10:49开。详情点击s.12306.cn/s/m/SqQtQ3"
        val trip3 = Parser12306.parseSms(sms3)
        assertNotNull(trip3)
        assertEquals("李四", trip3!!.passengerName)
        assertEquals("C308", trip3.trainCode)
        assertEquals("普洱", trip3.departureStation)
        assertEquals("10:49", trip3.departureTime)
        assertEquals("https://s.12306.cn/s/m/SqQtQ3", trip3.detailUrl)
    }

    @Test
    fun testParseMultiSms() {
        val multiSmsText = """
            【12306】李四购票成功，8月15日D3236次，杭州东站13:20开。详情点击s.12306.cn/s/g/kQwueJ
            【12306】李四改签成功，1月10日D274次，昆明站16:05开。详情点击s.12306.cn/s/l/PpXQoj
            【12306】李四购票成功，2月23日C308次，普洱站10:49开。详情点击s.12306.cn/s/m/SqQtQ3
        """.trimIndent()

        val trips = Parser12306.parseMultiSms(multiSmsText)
        assertEquals(3, trips.size)
        assertEquals("D3236", trips[0].trainCode)
        assertEquals("D274", trips[1].trainCode)
        assertEquals("C308", trips[2].trainCode)
    }
}


