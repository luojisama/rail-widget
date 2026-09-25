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
}

