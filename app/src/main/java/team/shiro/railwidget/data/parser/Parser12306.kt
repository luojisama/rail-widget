package team.shiro.railwidget.data.parser

import team.shiro.railwidget.data.model.Trip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object Parser12306 {

    private val INVALID_STATION_KEYWORDS = listOf(
        "系统", "通知", "服务", "中心", "网站", "提示", "订单", "12306",
        "铁路", "客户", "支付", "购票", "客票", "邮箱", "邮件", "规则",
        "说明", "退票", "改签", "办理", "有效", "发票", "乘车", "车站",
        "车次", "列车", "时间", "日期", "尊敬", "先生", "女士", "您好"
    )

    /**
     * Parse HTML or plain text from a 12306 email notification
     */
    fun parseEmail(content: String): List<Trip> {
        val cleanText = stripHtml(content)
        val trips = mutableListOf<Trip>()

        // 1. Extract Order Number
        val orderNoPattern = Pattern.compile("(?:订单号码|订单号|订单)\\s*[:：]?\\s*([A-Za-z0-9]{10})")
        val orderMatcher = orderNoPattern.matcher(cleanText)
        val orderNo = if (orderMatcher.find()) orderMatcher.group(1) ?: "E${System.currentTimeMillis()}" else "E${System.currentTimeMillis()}"

        // 2. Extract Passenger default
        val greetingPattern = Pattern.compile("尊敬的\\s*([^\\s，,：:!！]+?)(?:先生|女士)?(?:[您！!，,：:]|$)")
        val greetingMatcher = greetingPattern.matcher(cleanText)
        val defaultPassenger = if (greetingMatcher.find()) greetingMatcher.group(1)?.trim() ?: "乘客" else "乘客"

        // 3. Extract Ticket Details lines
        // 示例 1: 1.李全航，2026年10月07日11:14开，普洱站-昆明南站，D236次列车，3车无座，二等座，成人票，票价176.0元，电子客票。
        // 示例 2: 1.张三，2026年09月25日16:00开，昆明南站-普洱站，C315次列车，6车1D号，二等座，成人票，票价161.0元，检票口呈贡昆明南站 16A，电子客票。
        val ticketRegex = Pattern.compile(
            """(?:([0-9]{1,2})[.、．]\s*)?([^\s，,]+)[，,]\s*([0-9]{4}年[0-9]{1,2}月[0-9]{1,2}日)\s*([0-9]{1,2}:[0-9]{2})开[，,]\s*([^\s-]+?)(?:站)?\s*[-—至到]\s*([^\s，,]+?)(?:站)?[，,]\s*([A-Za-z0-9]+)次(?:列车)?[，,]\s*([^。]+?)(?:[。]|$|(?=[0-9]{1,2}[.、．]\s*[^\s，,]+[，,]\s*[0-9]{4}年)|(?=温馨提示)|(?=感谢您使用)|(?=祝旅途愉快))"""
        )
        val ticketMatcher = ticketRegex.matcher(cleanText)

        while (ticketMatcher.find()) {
            val passenger = ticketMatcher.group(2)?.trim() ?: defaultPassenger
            val dateStrRaw = ticketMatcher.group(3)?.trim() ?: ""
            val timeStr = ticketMatcher.group(4)?.trim() ?: ""
            val depStation = cleanStationName(ticketMatcher.group(5) ?: "")
            val arrStation = cleanStationName(ticketMatcher.group(6) ?: "")
            val trainCode = ticketMatcher.group(7)?.trim() ?: ""
            val extraInfo = ticketMatcher.group(8)?.trim() ?: ""

            // 解析车厢
            val carriageMatcher = Pattern.compile("([0-9]{1,2})车").matcher(extraInfo)
            val carriage = if (carriageMatcher.find()) "${carriageMatcher.group(1)}车" else ""

            // 解析座席（支持无座、卧铺铺位、高铁动车编号、普速座位编号）
            val seat = when {
                extraInfo.contains("无座") -> "无座"
                else -> {
                    val berthMatcher = Pattern.compile("([0-9]{1,2}号?[上中下]铺)").matcher(extraInfo)
                    if (berthMatcher.find()) {
                        berthMatcher.group(1) ?: ""
                    } else {
                        val hsMatcher = Pattern.compile("([0-9]{1,2}[A-Fa-f])号?").matcher(extraInfo)
                        if (hsMatcher.find()) {
                            "${hsMatcher.group(1)}号"
                        } else {
                            val normalMatcher = Pattern.compile("([0-9]{1,3}号)").matcher(extraInfo)
                            if (normalMatcher.find()) normalMatcher.group(1) ?: "" else ""
                        }
                    }
                }
            }

            // 解析席别（优先匹配二等座、一等座、硬卧等实体席别，避免被座席“无座”干扰）
            val seatTypeMatcher = Pattern.compile("(商务座|特等座|一等座|二等座|高级软卧|高级动卧|硬卧|软卧|动卧|硬座|软座)").matcher(extraInfo)
            val seatType = if (seatTypeMatcher.find()) {
                seatTypeMatcher.group(1) ?: "二等座"
            } else {
                "二等座"
            }

            // 解析票种（优先在车票明细行内识别，绝不受邮件尾部温馨提示干扰）
            val ticketTypeMatcher = Pattern.compile("(成人票|儿童票|学生票|残军票|列车补票)").matcher(extraInfo)
            val ticketType = if (ticketTypeMatcher.find()) ticketTypeMatcher.group(1) ?: "成人票" else "成人票"

            // 解析票价
            val priceMatcher = Pattern.compile("(?:票价|票款|补差价)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)元").matcher(extraInfo)
            val price = if (priceMatcher.find()) "${priceMatcher.group(1)}元" else ""

            // 解析检票口
            val rawGate = if (extraInfo.contains("检票口")) {
                val gateMatcher = Pattern.compile("检票口\\s*([^，,。\n]+)").matcher(extraInfo)
                if (gateMatcher.find()) gateMatcher.group(1)?.trim() ?: "" else ""
            } else {
                ""
            }

            val dateStandard = normalizeDate(dateStrRaw)

            trips.add(
                Trip(
                    orderNo = if (trips.isEmpty()) orderNo else "$orderNo-${trips.size + 1}",
                    passengerName = passenger,
                    trainCode = trainCode,
                    departureStation = depStation,
                    arrivalStation = arrStation,
                    departureDate = dateStandard,
                    departureTime = timeStr,
                    carriage = carriage,
                    seat = seat,
                    seatType = seatType,
                    ticketGate = extractGateCode(rawGate),
                    ticketType = ticketType,
                    price = price,
                    rawSource = "EMAIL"
                )
            )
        }

        // Fallback for variant email text
        if (trips.isEmpty()) {
            val fallback = parseGenericText(cleanText, orderNo, defaultPassenger, "EMAIL")
            if (fallback != null) {
                trips.add(fallback)
            }
        }

        return trips
    }

    /**
     * Parse 12306 SMS message text
     */
    fun parseSms(message: String, smsTimestamp: Long? = null): Trip? {
        val clean = message.trim()
        val orderNoPattern = Pattern.compile("(?:(?:订单号?[：:]?|订单号码?|订单)\\s*|【(?:12306|铁路12306)】\\s*)([A-Za-z0-9]{10})")
        val orderMatcher = orderNoPattern.matcher(clean)
        val extractedOrderNo = if (orderMatcher.find()) orderMatcher.group(1) ?: "" else ""

        // 匹配常见 12306 短信抬头: 【12306】李四购票成功，... 或 张三先生/女士 或 改签成功
        val namePattern = Pattern.compile("(?:【(?:12306|铁路12306)】\\s*)?([^\\s，,：:!！]+?)(?:购票成功|改签成功|先生|女士|您好|已购)")
        val nameMatcher = namePattern.matcher(clean)
        val passenger = if (nameMatcher.find()) {
            val n = nameMatcher.group(1)?.trim() ?: "乘客"
            if (n.contains("12306") || n.contains("铁路") || n.contains("订单")) "乘客" else n
        } else "乘客"

        return parseGenericText(clean, extractedOrderNo, passenger, "SMS", smsTimestamp)
    }

    /**
     * 支持一次性批量解析多条 12306 短信文本（如用户从系统短信直接复制长文本或多条短信合集）
     */
    fun parseMultiSms(rawText: String): List<Trip> {
        val clean = rawText.trim()
        if (clean.isBlank()) return emptyList()

        val results = mutableListOf<Trip>()

        // 尝试按照 【12306】 或 【铁路12306】 进行消息分块
        val blocks = if (clean.contains("【12306】") || clean.contains("【铁路12306】")) {
            clean.split(Regex("(?=【(?:12306|铁路12306)】)"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } else {
            // 按行拆分，过滤包含 车次 或 购票 或 开 的行
            clean.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && (it.contains("次") || it.contains("开") || it.contains("购票") || it.contains("改签")) }
        }

        for (block in blocks) {
            val trip = parseSms(block)
            if (trip != null && results.none { it.orderNo == trip.orderNo }) {
                results.add(trip)
            }
        }

        // 若分块未匹配到，则尝试整体解析
        if (results.isEmpty()) {
            val single = parseSms(clean)
            if (single != null) {
                results.add(single)
            }
        }

        return results
    }

    /**
     * Generic extractor for both SMS and informal ticket texts
     */
    fun parseGenericText(
        text: String,
        orderNo: String,
        defaultPassenger: String,
        source: String,
        smsTimestamp: Long? = null
    ): Trip? {
        // 1. 车次匹配 (如 G2, D3236, K1557, C315 等)
        val trainPatternWithSuffix = Pattern.compile("(?<![0-9])([GCDZTKYSY][0-9]{1,4}|[1-9][0-9]{3})(?:次|列车)")
        val trainMatcherSuffix = trainPatternWithSuffix.matcher(text)
        val trainCode = if (trainMatcherSuffix.find()) {
            trainMatcherSuffix.group(1) ?: return null
        } else {
            val trainPatternFallback = Pattern.compile("(?<!12306)(?<![0-9])([GCDZTKYSY][0-9]{1,4})(?![0-9])")
            val fallbackMatcher = trainPatternFallback.matcher(text)
            if (fallbackMatcher.find()) fallbackMatcher.group(1) ?: return null else return null
        }

        // 2. 日期匹配 (优先提取紧邻发车时刻、车次或已购关键词的乘车日期，排除邮件发件时间与下单时间干扰)
        val depDatePatterns = listOf(
            Pattern.compile("(?:([0-9]{4})[年/-])?([0-9]{1,2})[月/-]([0-9]{1,2})[日号]?\\s*(?:[0-9]{1,2}:[0-9]{2})?\\s*开"),
            Pattern.compile("(?:([0-9]{4})[年/-])?([0-9]{1,2})[月/-]([0-9]{1,2})[日号]?\\s*([GCDZTKYSY][0-9]{1,4}|[1-9][0-9]{3})(?:次|列车)"),
            Pattern.compile("(?:已购|购买)\\s*(?:([0-9]{4})[年/-])?([0-9]{1,2})[月/-]([0-9]{1,2})[日号]?")
        )

        var matchedYear: Int? = null
        var matchedMonth: Int? = null
        var matchedDay: Int? = null

        for (pattern in depDatePatterns) {
            val m = pattern.matcher(text)
            if (m.find()) {
                val yStr = m.group(1)
                matchedYear = yStr?.toIntOrNull()
                matchedMonth = m.group(2)?.toIntOrNull()
                matchedDay = m.group(3)?.toIntOrNull()
                break
            }
        }

        // 若前置强绑定规则未命中，排除发件/下单时间后兜底搜索
        if (matchedMonth == null || matchedDay == null) {
            val sanitizedForDate = text
                .replace(Regex("发件时间[：:][^\\n]+"), "")
                .replace(Regex("您于[0-9]{4}年[0-9]{1,2}月[0-9]{1,2}日[^\\n，,。]*?(?:成功购买|支付成功)"), "")
            val fallbackDatePattern = Pattern.compile("(?:([0-9]{4})[年/-])?([0-9]{1,2})[月/-]([0-9]{1,2})[日号]?")
            val fbMatcher = fallbackDatePattern.matcher(sanitizedForDate)
            if (fbMatcher.find()) {
                matchedYear = fbMatcher.group(1)?.toIntOrNull()
                matchedMonth = fbMatcher.group(2)?.toIntOrNull()
                matchedDay = fbMatcher.group(3)?.toIntOrNull()
            }
        }

        val baseCal = Calendar.getInstance()
        if (smsTimestamp != null && smsTimestamp > 0) {
            baseCal.timeInMillis = smsTimestamp
        }
        val defaultYear = baseCal.get(Calendar.YEAR)

        val dateStandard = if (matchedMonth != null && matchedDay != null) {
            val year = matchedYear ?: defaultYear
            String.format(Locale.CHINA, "%04d-%02d-%02d", year, matchedMonth, matchedDay)
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val baseTime = if (smsTimestamp != null && smsTimestamp > 0) smsTimestamp else System.currentTimeMillis()
            sdf.format(baseTime)
        }

        // 3. 出发站、到达站与发到时刻提取
        var depStation = ""
        var depTime = ""
        var arrStation = ""
        var arrTime = ""

        // 模式 A: 连带发到模式 (如 上海虹桥站14:00开、北京南站18:28到 或 昆明南16:00开、普洱18:38到)
        val fullTripPattern = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})开[、，,\\s]+([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})到"
        )
        val fullTripMatcher = fullTripPattern.matcher(text)

        if (fullTripMatcher.find()) {
            val s1 = cleanStationName(fullTripMatcher.group(1) ?: "")
            val s2 = cleanStationName(fullTripMatcher.group(3) ?: "")
            if (s1.isNotBlank()) depStation = s1
            if (s2.isNotBlank()) arrStation = s2
            depTime = fullTripMatcher.group(2)?.trim() ?: ""
            arrTime = fullTripMatcher.group(4)?.trim() ?: ""
        } else {
            // 模式 B1: 发车时间后紧跟站名区间 (如 11:14开，普洱站-昆明南站)
            val timeFirstPattern = Pattern.compile("([0-9]{1,2}:[0-9]{2})开[，,\\s]+([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*[-—至到➔]\\s*([\\u4e00-\\u9fa5]{2,10}?)(?:站)?(?=[，,。、\\s（(【\\[]|二等|一等|商务|无座|硬卧|软卧|软座|硬座|特等|动卧|[0-9]{1,2}车|次|$)")
            val timeFirstMatcher = timeFirstPattern.matcher(text)
            if (timeFirstMatcher.find()) {
                depTime = timeFirstMatcher.group(1)?.trim() ?: ""
                val s1 = cleanStationName(timeFirstMatcher.group(2) ?: "")
                val s2 = cleanStationName(timeFirstMatcher.group(3) ?: "")
                if (s1.isNotBlank()) depStation = s1
                if (s2.isNotBlank()) arrStation = s2
            } else {
                // 模式 B2: 站名在发车时刻前 (如 杭州东站13:20开 或 昆明南站16:00开)
                val depMatchPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})开")
                val depMatch = depMatchPattern.matcher(text)
                if (depMatch.find()) {
                    val s1 = cleanStationName(depMatch.group(1) ?: "")
                    if (s1.isNotBlank()) depStation = s1
                    depTime = depMatch.group(2)?.trim() ?: ""
                } else {
                    // 单纯提取时间
                    val pureTimeMatcher = Pattern.compile("([0-9]{1,2}:[0-9]{2})开").matcher(text)
                    if (pureTimeMatcher.find()) {
                        depTime = pureTimeMatcher.group(1)?.trim() ?: ""
                    }
                }

                // 单独到达时刻匹配 (如 北京南站18:28到)
                val arrMatchPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})到")
                val arrMatch = arrMatchPattern.matcher(text)
                if (arrMatch.find()) {
                    val s2 = cleanStationName(arrMatch.group(1) ?: "")
                    if (s2.isNotBlank()) arrStation = s2
                    arrTime = arrMatch.group(2)?.trim() ?: ""
                }
            }

            // 检查双站区间模式 (排除非站名干扰，必须两端均通过车站合法性校验)
            if (depStation.isBlank() || arrStation.isBlank()) {
                val stationsPattern = Pattern.compile(
                    "([\\u4e00-\\u9fa5]{2,10}?)(?:站)?(?:-|至|到|➔)([\\u4e00-\\u9fa5]{2,10}?)(?:站)?(?=[，,。、\\s（(【\\[]|二等|一等|商务|无座|硬卧|软卧|软座|硬座|特等|动卧|[0-9]{1,2}车|$)"
                )
                val stationMatcher = stationsPattern.matcher(text)
                while (stationMatcher.find()) {
                    val s1 = cleanStationName(stationMatcher.group(1) ?: "")
                    val s2 = cleanStationName(stationMatcher.group(2) ?: "")
                    if (s1.isNotBlank() && s2.isNotBlank()) {
                        if (depStation.isBlank()) depStation = s1
                        if (arrStation.isBlank()) arrStation = s2
                        break
                    }
                }
            }
        }

        if (depStation.isBlank()) {
            depStation = "出发站"
        }
        if (arrStation.isBlank()) {
            arrStation = "终点站"
        }

        // 4. 详情短链接 (如 s.12306.cn/s/g/kQwueJ, s.12306.cn/s/l/YK6xhV, s.12306.cn/s/m/SqQtQ3)
        val urlPattern = Pattern.compile("(?i)(https?://)?(s\\.12306\\.cn/s/[a-z]/[A-Za-z0-9]+)")
        val urlMatcher = urlPattern.matcher(text)
        val detailUrl = if (urlMatcher.find()) {
            val raw = urlMatcher.group(0) ?: ""
            if (!raw.startsWith("http")) "https://$raw" else raw
        } else ""

        // 5. 车厢与座位提取
        val carriagePattern = Pattern.compile("([0-9]{1,2})车")
        val carriageMatcher = carriagePattern.matcher(text)
        val carriage = if (carriageMatcher.find()) "${carriageMatcher.group(1)}车" else ""

        val seat = when {
            text.contains("无座") -> "无座"
            else -> {
                val berthMatcher = Pattern.compile("([0-9]{1,2}号?[上中下]铺)").matcher(text)
                if (berthMatcher.find()) {
                    berthMatcher.group(1) ?: ""
                } else {
                    val hsMatcher = Pattern.compile("([0-9]{1,2}[A-Fa-f])号?").matcher(text)
                    if (hsMatcher.find()) {
                        "${hsMatcher.group(1)}号"
                    } else {
                        val normalMatcher = Pattern.compile("([0-9]{1,3}号)").matcher(text)
                        if (normalMatcher.find()) normalMatcher.group(1) ?: "" else ""
                    }
                }
            }
        }

        // 席别与票种识别（优先匹配二等座、一等座等实体席别，避免被无座干扰）
        val seatTypePattern = Pattern.compile("(商务座|特等座|一等座|二等座|高级软卧|高级动卧|硬卧|软卧|动卧|硬座|软座)")
        val seatTypeMatcher = seatTypePattern.matcher(text)
        val seatType = if (seatTypeMatcher.find()) seatTypeMatcher.group(1) ?: "二等座" else "二等座"

        // 优先匹配明示票种
        val explicitTicketTypeMatcher = Pattern.compile("(成人票|儿童票|学生票|残军票|列车补票)").matcher(text)
        val ticketType = if (explicitTicketTypeMatcher.find()) {
            explicitTicketTypeMatcher.group(1) ?: "成人票"
        } else {
            val cleanForType = text.replace(Regex("温馨提示[\\s\\S]*"), "")
                .replace(Regex("退改说明|改签规则|退票规则|旅客须知"), "")
            when {
                cleanForType.contains("补票") || cleanForType.contains("补差价") -> "列车补票"
                cleanForType.contains("改签成功") || cleanForType.contains("已改签") || cleanForType.contains("改签通知") -> "改签票"
                cleanForType.contains("学生") -> "学生票"
                cleanForType.contains("儿童") -> "儿童票"
                cleanForType.contains("残军") || cleanForType.contains("军人") -> "残军票"
                else -> "成人票"
            }
        }

        // 票价识别 (兼容 票价/票款/补差价)
        val pricePattern = Pattern.compile("(?:票价|票款|补差价)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)元")
        val priceMatcher = pricePattern.matcher(text)
        val price = if (priceMatcher.find()) "${priceMatcher.group(1)}元" else ""

        // 6. 检票口提取
        val gatePattern = Pattern.compile("(?:检票口|检票)[：:;\\s]*([\\u4e00-\\u9fa5A-Za-z0-9\\s]{1,20}?)(?:[，,。；;]|$)")
        val gateMatcher = gatePattern.matcher(text)
        val rawGate = if (gateMatcher.find()) gateMatcher.group(1)?.trim() ?: "" else ""

        // 7. 确定性订单号生成（若未提取到官方E订单号，依据车次、日期、乘客名与席位生成确定哈希，避免重复入库）
        val finalOrderNo = if (orderNo.isNotBlank()) {
            orderNo
        } else {
            val internalOrderMatcher = Pattern.compile("(?:(?:订单号?[：:]?|订单号码?|订单)\\s*|【(?:12306|铁路12306)】\\s*)([A-Za-z0-9]{10})").matcher(text)
            if (internalOrderMatcher.find()) {
                internalOrderMatcher.group(1) ?: ""
            } else {
                val cleanDate = dateStandard.replace("-", "")
                val rawKey = "${trainCode}_${defaultPassenger}_${depStation}_${arrStation}_${carriage}_${seat}"
                val hash = kotlin.math.abs(rawKey.hashCode()) % 1000000
                "S${trainCode}_${cleanDate}_${String.format(Locale.CHINA, "%06d", hash)}"
            }
        }

        return Trip(
            orderNo = finalOrderNo,
            passengerName = defaultPassenger,
            trainCode = trainCode,
            departureStation = depStation,
            arrivalStation = arrStation,
            departureDate = dateStandard,
            departureTime = depTime,
            arrivalTime = arrTime,
            carriage = carriage,
            seat = seat,
            seatType = seatType,
            ticketType = ticketType,
            price = price,
            ticketGate = extractGateCode(rawGate),
            detailUrl = detailUrl,
            rawSource = source
        )
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeDate(dateStr: String): String {
        val pattern = Pattern.compile("([0-9]{4})年([0-9]{1,2})月([0-9]{1,2})日")
        val matcher = pattern.matcher(dateStr)
        return if (matcher.find()) {
            val y = matcher.group(1)
            val m = matcher.group(2)?.padStart(2, '0')
            val d = matcher.group(3)?.padStart(2, '0')
            "$y-$m-$d"
        } else {
            dateStr
        }
    }

    fun extractGateCode(gateRaw: String): String {
        if (gateRaw.isBlank()) return ""
        if (gateRaw.contains("客票") || gateRaw.contains("提示") || gateRaw.contains("报销")) return ""
        val p = Pattern.compile("([0-9]+[A-Za-z]?(?:[/-][0-9]+[A-Za-z]?)?|[A-Za-z][0-9]*)")
        val m = p.matcher(gateRaw)
        return if (m.find()) m.group(1) ?: "" else ""
    }

    fun isValidStationName(name: String): Boolean {
        val clean = name.trim()
        if (clean.length < 2 || clean.length > 10) return false
        if (INVALID_STATION_KEYWORDS.any { clean.contains(it) }) return false
        if (clean.endsWith("日") || clean.endsWith("月") || clean.endsWith("年")) return false
        return clean.all { it in '\u4e00'..'\u9fa5' || it.isLetter() }
    }

    fun cleanStationName(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("次列车")
            .removePrefix("次")
            .removePrefix("列车")
            .removeSuffix("站")
            .trim()
        return if (isValidStationName(cleaned)) cleaned else ""
    }
}
