package team.shiro.railwidget.data.parser

import team.shiro.railwidget.data.model.Trip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object Parser12306 {

    /**
     * Parse HTML or plain text from a 12306 email notification
     */
    fun parseEmail(content: String): List<Trip> {
        val cleanText = stripHtml(content)
        val trips = mutableListOf<Trip>()

        // 1. Extract Order Number
        val orderNoPattern = Pattern.compile("订单号码\\s*([A-Za-z0-9]{10})")
        val orderMatcher = orderNoPattern.matcher(cleanText)
        val orderNo = if (orderMatcher.find()) orderMatcher.group(1) ?: "E${System.currentTimeMillis()}" else "E${System.currentTimeMillis()}"

        // 2. Extract Passenger default
        val greetingPattern = Pattern.compile("尊敬的\\s*([^\\s，,：:!！]+?)(?:先生|女士)?(?:[您！!，,：:]|$)")
        val greetingMatcher = greetingPattern.matcher(cleanText)
        val defaultPassenger = if (greetingMatcher.find()) greetingMatcher.group(1)?.trim() ?: "乘客" else "乘客"

        // 3. Extract Ticket Details lines
        // 示例: 1.张三，2026年09月25日16:00开，昆明南站-普洱站，C315次列车，6车1D号，二等座，成人票，票价161.0元，检票口呈贡昆明南站 16A，电子客票。
        val ticketRegex = Pattern.compile(
            """(?:([0-9]+)\.)?([^\s，,]+)，([0-9]{4}年[0-9]{1,2}月[0-9]{1,2}日)\s*([0-9]{1,2}:[0-9]{2})开[，,]\s*([^\s-]+?)(?:站)?-([^\s，,]+?)(?:站)?[，,]\s*([A-Za-z0-9]+)次(?:列车)?[，,]\s*([0-9]+车)([0-9A-Za-z]+号)[，,]\s*([^，,]+?)[，,]\s*([^，,]+?)[，,]\s*(?:票价)?([^，,]+?)(?:[，,](?:检票口)?([^，,]*?))?(?:[，,]|$)"""
        )
        val ticketMatcher = ticketRegex.matcher(cleanText)

        while (ticketMatcher.find()) {
            val passenger = ticketMatcher.group(2)?.trim() ?: defaultPassenger
            val dateStrRaw = ticketMatcher.group(3)?.trim() ?: ""
            val timeStr = ticketMatcher.group(4)?.trim() ?: ""
            val depStation = ticketMatcher.group(5)?.trim() ?: ""
            val arrStation = ticketMatcher.group(6)?.trim() ?: ""
            val trainCode = ticketMatcher.group(7)?.trim() ?: ""
            val carriage = ticketMatcher.group(8)?.trim() ?: ""
            val seat = ticketMatcher.group(9)?.trim() ?: ""
            val seatType = ticketMatcher.group(10)?.trim() ?: "二等座"
            val ticketType = ticketMatcher.group(11)?.trim() ?: "成人票"
            val price = ticketMatcher.group(12)?.trim() ?: ""
            val rawGate = ticketMatcher.group(13)?.trim() ?: ""

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
        val orderNoPattern = Pattern.compile("(?:订单号?[：:]?|订单)([A-Za-z0-9]{10})")
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

        // 2. 日期匹配 (如 8月15日, 2026年9月25日)
        val datePattern = Pattern.compile("(?:([0-9]{4})[年/-])?([0-9]{1,2})[月/-]([0-9]{1,2})[日号]?")
        val dateMatcher = datePattern.matcher(text)
        val dateStandard = if (dateMatcher.find()) {
            val baseCal = Calendar.getInstance()
            if (smsTimestamp != null && smsTimestamp > 0) {
                baseCal.timeInMillis = smsTimestamp
            }
            val defaultYear = baseCal.get(Calendar.YEAR)
            val yearStr = dateMatcher.group(1)
            val month = dateMatcher.group(2)?.toIntOrNull() ?: 1
            val day = dateMatcher.group(3)?.toIntOrNull() ?: 1

            val year = if (!yearStr.isNullOrBlank()) {
                yearStr.toInt()
            } else {
                defaultYear
            }
            String.format(Locale.CHINA, "%04d-%02d-%02d", year, month, day)
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val baseTime = if (smsTimestamp != null && smsTimestamp > 0) smsTimestamp else System.currentTimeMillis()
            sdf.format(baseTime)
        }

        // 3. 出发站、到达站与发到时刻提取
        var depStation = ""
        var depTime = "00:00"
        var arrStation = ""
        var arrTime = ""

        // 模式 A: 连带发到模式 (如 上海虹桥站14:00开、北京南站18:28到 或 昆明南16:00开、普洱18:38到)
        val fullTripPattern = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})开[、，,\\s]+([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})到"
        )
        val fullTripMatcher = fullTripPattern.matcher(text)

        if (fullTripMatcher.find()) {
            depStation = cleanStationName(fullTripMatcher.group(1) ?: "")
            depTime = fullTripMatcher.group(2)?.trim() ?: "00:00"
            arrStation = cleanStationName(fullTripMatcher.group(3) ?: "")
            arrTime = fullTripMatcher.group(4)?.trim() ?: ""
        } else {
            // 模式 B: 单独发车时刻匹配 (如 杭州东站13:20开 或 昆明南站16:00开)
            val depMatchPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})开")
            val depMatch = depMatchPattern.matcher(text)
            if (depMatch.find()) {
                depStation = cleanStationName(depMatch.group(1) ?: "")
                depTime = depMatch.group(2)?.trim() ?: "00:00"
            }

            // 单独到达时刻匹配 (如 北京南站18:28到)
            val arrMatchPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})到")
            val arrMatch = arrMatchPattern.matcher(text)
            if (arrMatch.find()) {
                arrStation = cleanStationName(arrMatch.group(1) ?: "")
                arrTime = arrMatch.group(2)?.trim() ?: ""
            }

            // 检查双站区间模式 (如 上海虹桥站-北京南站 或 昆明南到普洱 或 上海虹桥至北京南)
            if (depStation.isBlank() || arrStation.isBlank()) {
                val stationsPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}?)(?:站)?(?:-|至|到|➔)([\\u4e00-\\u9fa5]{2,10}?)(?:站)?")
                val stationMatcher = stationsPattern.matcher(text)
                if (stationMatcher.find()) {
                    if (depStation.isBlank()) depStation = cleanStationName(stationMatcher.group(1) ?: "")
                    if (arrStation.isBlank()) arrStation = cleanStationName(stationMatcher.group(2) ?: "")
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

        val seatPattern = Pattern.compile("([0-9]{1,2}[A-Fa-f])号?")
        val seatMatcher = seatPattern.matcher(text)
        val seat = if (seatMatcher.find()) "${seatMatcher.group(1)}号" else ""

        // 席别与票种识别
        val seatTypePattern = Pattern.compile("(商务座|一等座|二等座|无座|特等座|软卧|硬卧|硬座|软座|动卧)")
        val seatTypeMatcher = seatTypePattern.matcher(text)
        val seatType = if (seatTypeMatcher.find()) seatTypeMatcher.group(1) ?: "二等座" else "二等座"

        // 票价识别
        val pricePattern = Pattern.compile("(?:票价|票款)?([0-9]+(?:\\.[0-9]{1,2})?)元")
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
            val cleanDate = dateStandard.replace("-", "")
            val rawKey = "${trainCode}_${defaultPassenger}_${depStation}_${arrStation}_${carriage}_${seat}"
            val hash = kotlin.math.abs(rawKey.hashCode()) % 1000000
            "S${trainCode}_${cleanDate}_${String.format(Locale.CHINA, "%06d", hash)}"
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
        val p = Pattern.compile("([0-9]+[A-Za-z]?|[A-Za-z][0-9]*)")
        val m = p.matcher(gateRaw)
        return if (m.find()) m.group(1) ?: gateRaw else gateRaw
    }

    fun cleanStationName(raw: String): String {
        return raw.trim()
            .removePrefix("次列车")
            .removePrefix("次")
            .removePrefix("列车")
            .removeSuffix("站")
            .trim()
    }
}
