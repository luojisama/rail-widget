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

    @Test
    fun testTrainFareAdjustmentTicketSms() {
        val sms = "【12306】E2Z0565205（列车补票），2月12日D3946次，桂林至桂林北，二等座3车14F号，补差价6.0元。补票信息可通过12306手机客户端“本人车票”功能查询，通过12306网站、车站售票窗口、自动售票机申领电子发票。"
        val trip = Parser12306.parseSms(sms)
        assertNotNull(trip)
        trip!!

        assertEquals("E2Z0565205", trip.orderNo)
        assertEquals("D3946", trip.trainCode)
        assertEquals("桂林", trip.departureStation)
        assertEquals("桂林北", trip.arrivalStation)
        assertEquals("3车", trip.carriage)
        assertEquals("14F号", trip.seat)
        assertEquals("二等座", trip.seatType)
        assertEquals("列车补票", trip.ticketType)
        assertEquals("6.0元", trip.price)
        assertEquals("", trip.departureTime)
    }

    @Test
    fun testUserNoSeatRealEmailParsing() {
        val email = """
            原始邮件
            发件人：12306 <12306@rails.com.cn>
            发件时间：2026年10月6日 15:19
            收件人：2534316454@qq.com <2534316454@qq.com>
            主题：网上购票系统-用户支付通知
            尊敬的 李全航先生：
            您好！
            您于2026年10月06日在中国铁路客户服务中心网站([12306.cn](http://www.12306.cn/)) 成功购买了1张车票，票款共计176.00元，订单号码 EQ97461082 。 所购车票信息如下：
            1.李全航，2026年10月07日11:14开，普洱站-昆明南站，D236次列车，3车无座，二等座，成人票，票价176.0元，电子客票。
            温馨提示
            （1）订单信息查询有效期限为30日。
            （2）为了确保旅客人身安全和列车运行秩序，车站将在开车时间之前提前停止售票、检票，请合理安排出行时间，提前到乘车站办理换票、安检、验证并到指定场所候车，以免耽误乘车。
            （3）购票后如需报销凭证，可在行程结束后180天内通过中国铁路12306网站（含12306移动端）或车站售票窗口（不含客票代售点）、自动售票机申请开具铁路电子发票。在香港西九龙站或港铁公司代售点购票的，仅限在香港西九龙站换取报销凭证。
            （4）改签、变更到站、退票相关规则详见[退改说明](https://mobile.12306.cn/otsmobile/h5/otsbussiness/info/orderWarmTips.html)。
            （5）禁限品和托运物品详细规定详见[《铁路旅客禁止、限制携带和托运物品目录》](https://kyfw.12306.cn/otn/gonggao/saleTicketMeans.html?linktypeid=means6)。
            （6）未尽事项，请详见[《铁路旅客运输规程》](https://kyfw.12306.cn/otn/gonggao/saleTicketMeans.html?linktypeid=means2)、 [《铁路旅客运输办理细则》](https://kyfw.12306.cn/otn/gonggao/saleTicketMeans.html?linktypeid=means4)、 [《铁路旅客电子客票暂行实施办法》](https://kyfw.12306.cn/otn/gonggao/saleTicketMeans.html?linktypeid=means8)、 [《铁路互联网售票暂行办法》](https://kyfw.12306.cn/otn/gonggao/saleTicketMeans.html?linktypeid=means1)等规定和车站公告。
            感谢您使用中国铁路客户服务中心网站12306.cn！ 本邮件由系统自动发出，请勿回复。
            祝旅途愉快！
            中国铁路客户服务中心
            2026年10月06日
        """.trimIndent()

        val trips = Parser12306.parseEmail(email)
        assertEquals(1, trips.size)
        val trip = trips[0]

        assertEquals("EQ97461082", trip.orderNo)
        assertEquals("李全航", trip.passengerName)
        assertEquals("D236", trip.trainCode)
        assertEquals("普洱", trip.departureStation)
        assertEquals("昆明南", trip.arrivalStation)
        // 关键断言：发车日期必须为 10月7日，严禁被发件时间/下单时间 10月6日 篡改
        assertEquals("2026-10-07", trip.departureDate)
        assertEquals("11:14", trip.departureTime)
        assertEquals("3车", trip.carriage)
        assertEquals("无座", trip.seat)
        assertEquals("二等座", trip.seatType)
        // 关键断言：票种必须为 成人票，严禁被温馨提示中的“改签规则”误判为改签票
        assertEquals("成人票", trip.ticketType)
        assertEquals("176.0元", trip.price)
        // 关键断言：电子客票严禁被当作检票口
        assertEquals("", trip.ticketGate)
    }

    @Test
    fun testSleeperEmailParsing() {
        val email = """
            尊敬的 王五先生：
            您好！订单号码 E123456789 。 所购车票信息如下：
            1.王五，2026年10月08日20:30开，北京站-沈阳站，K123次列车，05车18号下铺，硬卧，成人票，票价210.0元，电子客票。
        """.trimIndent()

        val trips = Parser12306.parseEmail(email)
        assertEquals(1, trips.size)
        val trip = trips[0]

        assertEquals("E123456789", trip.orderNo)
        assertEquals("王五", trip.passengerName)
        assertEquals("K123", trip.trainCode)
        assertEquals("北京", trip.departureStation)
        assertEquals("沈阳", trip.arrivalStation)
        assertEquals("2026-10-08", trip.departureDate)
        assertEquals("20:30", trip.departureTime)
        assertEquals("05车", trip.carriage)
        assertEquals("18号下铺", trip.seat)
        assertEquals("硬卧", trip.seatType)
        assertEquals("成人票", trip.ticketType)
        assertEquals("210.0元", trip.price)
    }

    @Test
    fun testMultiTicketsEmailParsing() {
        val email = """
            主题：网上购票系统-用户支付通知
            尊敬的 李先生：
            您于2026年10月06日成功购买了2张车票，订单号码 EQ97461082 。 所购车票信息如下：
            1.李全航，2026年10月07日11:14开，普洱站-昆明南站，D236次列车，3车无座，二等座，成人票，票价176.0元，电子客票。
            2.李小航，2026年10月07日11:14开，普洱站-昆明南站，D236次列车，3车无座，二等座，儿童票，票价88.0元，电子客票。
            温馨提示
            （4）改签、变更到站相关规则详见退改说明。
        """.trimIndent()

        val trips = Parser12306.parseEmail(email)
        assertEquals(2, trips.size)

        val trip1 = trips[0]
        assertEquals("EQ97461082", trip1.orderNo)
        assertEquals("李全航", trip1.passengerName)
        assertEquals("成人票", trip1.ticketType)
        assertEquals("176.0元", trip1.price)

        val trip2 = trips[1]
        assertEquals("EQ97461082-2", trip2.orderNo)
        assertEquals("李小航", trip2.passengerName)
        assertEquals("儿童票", trip2.ticketType)
        assertEquals("88.0元", trip2.price)
    }

    @Test
    fun testGenericTextFallbackDefensiveParsing() {
        val email = """
            主题：网上购票系统-用户支付通知
            发件时间： 2026年10月6日 15:19
            您好！您于2026年10月06日成功购买了1张车票，订单号码 EQ97461082 。
            1.李全航，2026年10月07日11:14开，普洱站-昆明南站，D236次列车，3车无座，二等座，成人票，票价176.0元，电子客票。
            温馨提示：（4）改签规则详见说明。
        """.trimIndent()

        // 强行用 parseGenericText 处理整篇邮件，检验兜底防御性
        val trip = Parser12306.parseGenericText(email, "EQ97461082", "李全航", "EMAIL")
        assertNotNull(trip)
        trip!!

        assertEquals("D236", trip.trainCode)
        assertEquals("普洱", trip.departureStation)
        assertEquals("昆明南", trip.arrivalStation)
        assertEquals("2026-10-07", trip.departureDate)
        assertEquals("11:14", trip.departureTime)
        assertEquals("3车", trip.carriage)
        assertEquals("无座", trip.seat)
        assertEquals("二等座", trip.seatType)
        assertEquals("成人票", trip.ticketType)
    }
}


