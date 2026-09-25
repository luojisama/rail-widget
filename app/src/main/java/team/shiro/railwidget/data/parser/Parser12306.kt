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
    fun parseSms(message: String): Trip? {
        val clean = message.trim()
        val orderNoPattern = Pattern.compile("(?:订单号?[：:]?|订单)([A-Za-z0-9]{10})")
        val orderMatcher = orderNoPattern.matcher(clean)
        val orderNo = if (orderMatcher.find()) orderMatcher.group(1) ?: "S${System.currentTimeMillis()}" else "S${System.currentTimeMillis()}"

        // 匹配常见 12306 短信抬头: 【12306】李四购票成功，... 或 张三先生/女士
        val namePattern = Pattern.compile("(?:【(?:12306|铁路12306)】\\s*)?([^\\s，,：:!！]+?)(?:购票成功|先生|女士)")
        val nameMatcher = namePattern.matcher(clean)
        val passenger = if (nameMatcher.find()) {
            val n = nameMatcher.group(1)?.trim() ?: "乘客"
            if (n.contains("12306")) "乘客" else n
        } else "乘客"

        return parseGenericText(clean, orderNo, passenger, "SMS")
    }

    /**
     * Generic extractor for both SMS and informal ticket texts
     */
    fun parseGenericText(text: String, orderNo: String, defaultPassenger: String, source: String): Trip? {
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
            val currentCal = Calendar.getInstance()
            val currentYear = currentCal.get(Calendar.YEAR)
            val yearStr = dateMatcher.group(1)
            val month = dateMatcher.group(2)?.toIntOrNull() ?: 1
            val day = dateMatcher.group(3)?.toIntOrNull() ?: 1

            val year = if (!yearStr.isNullOrBlank()) {
                yearStr.toInt()
            } else {
                currentYear
            }
            String.format(Locale.CHINA, "%04d-%02d-%02d", year, month, day)
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            sdf.format(System.currentTimeMillis())
        }

        // 3. 出发站与时间提取:
        // 模式 A: 杭州东站13:20开 或 杭州东13:20开
        // 模式 B: 上海虹桥-北京南，09:00开
        val depMatchPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}?)(?:站)?\\s*([0-9]{1,2}:[0-9]{2})开")
        val depMatch = depMatchPattern.matcher(text)

        var depStation = ""
        var depTime = "00:00"
        var arrStation = ""

        if (depMatch.find()) {
            depStation = depMatch.group(1)?.trim() ?: ""
            depTime = depMatch.group(2)?.trim() ?: "00:00"
        }

        // 检查双站模式 (如 上海虹桥站-北京南站 或 昆明南到普洱)
        val stationsPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}?)(?:站)?(?:-|至|到|➔)([\\u4e00-\\u9fa5]{2,10}?)(?:站)?")
        val stationMatcher = stationsPattern.matcher(text)
        if (stationMatcher.find()) {
            depStation = stationMatcher.group(1)?.trim() ?: depStation
            arrStation = stationMatcher.group(2)?.trim() ?: ""
        }

        if (depStation.isBlank()) {
            depStation = "出发站"
        }
        if (arrStation.isBlank()) {
            arrStation = "以12306终点为准"
        }

        // 4. 详情短链接 (如 s.12306.cn/s/g/kQwueJ)
        val urlPattern = Pattern.compile("(?i)(https?://)?(s\\.12306\\.cn/s/g/[A-Za-z0-9]+)")
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

        // 6. 检票口提取
        val gatePattern = Pattern.compile("(?:检票口|检票)[：:;\\s]*([\\u4e00-\\u9fa5A-Za-z0-9\\s]{1,20}?)(?:[，,。；;]|$)")
        val gateMatcher = gatePattern.matcher(text)
        val rawGate = if (gateMatcher.find()) gateMatcher.group(1)?.trim() ?: "" else ""

        return Trip(
            orderNo = orderNo,
            passengerName = defaultPassenger,
            trainCode = trainCode,
            departureStation = depStation,
            arrivalStation = arrStation,
            departureDate = dateStandard,
            departureTime = depTime,
            carriage = carriage,
            seat = seat,
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
}
