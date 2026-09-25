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

        val greetingPattern = Pattern.compile("([^\\s，,：:!！]+?)(?:先生|女士)")
        val greetingMatcher = greetingPattern.matcher(clean)
        val passenger = if (greetingMatcher.find()) greetingMatcher.group(1)?.trim() ?: "乘客" else "乘客"

        return parseGenericText(clean, orderNo, passenger, "SMS")
    }

    /**
     * Generic extractor for both SMS and informal ticket texts
     */
    fun parseGenericText(text: String, orderNo: String, defaultPassenger: String, source: String): Trip? {
        // Extract train code (e.g. G123, C315, D9, Z1, K202, or 4-digit number), prioritizing code followed by 次/列车
        val trainPatternWithSuffix = Pattern.compile("(?<![0-9])([GCDZTKYSY][0-9]{1,4}|[1-9][0-9]{3})(?:次|列车)")
        val trainMatcherSuffix = trainPatternWithSuffix.matcher(text)
        val trainCode = if (trainMatcherSuffix.find()) {
            trainMatcherSuffix.group(1) ?: return null
        } else {
            val trainPatternFallback = Pattern.compile("(?<!12306)(?<![0-9])([GCDZTKYSY][0-9]{1,4})(?![0-9])")
            val fallbackMatcher = trainPatternFallback.matcher(text)
            if (fallbackMatcher.find()) fallbackMatcher.group(1) ?: return null else return null
        }

        // Extract date (e.g. 2026年9月25日 or 9月25日 or 2026-09-25)
        val datePattern = Pattern.compile("(?:([0-9]{4})[年/-])?([0-9]{1,2})[月/-]([0-9]{1,2})[日号]?")
        val dateMatcher = datePattern.matcher(text)
        val dateStandard = if (dateMatcher.find()) {
            val year = dateMatcher.group(1) ?: Calendar.getInstance().get(Calendar.YEAR).toString()
            val month = dateMatcher.group(2)?.padStart(2, '0') ?: "01"
            val day = dateMatcher.group(3)?.padStart(2, '0') ?: "01"
            "$year-$month-$day"
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            sdf.format(System.currentTimeMillis())
        }

        // Extract departure time (e.g. 16:00开 or 16:00)
        val timePattern = Pattern.compile("([0-9]{1,2}:[0-9]{2})(?:开|出发)?")
        val timeMatcher = timePattern.matcher(text)
        val depTime = if (timeMatcher.find()) timeMatcher.group(1) ?: "00:00" else "00:00"

        // Extract stations (e.g. 昆明南站-普洱站, 昆明南到普洱, 昆明南至普洱)
        val stationsPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}?)(?:站)?(?:-|至|到|➔)([\\u4e00-\\u9fa5]{2,10}?)(?:站)?")
        val stationMatcher = stationsPattern.matcher(text)
        val (depStation, arrStation) = if (stationMatcher.find()) {
            Pair(stationMatcher.group(1)?.trim() ?: "", stationMatcher.group(2)?.trim() ?: "")
        } else {
            Pair("出发站", "到达站")
        }

        // Extract carriage and seat
        val carriagePattern = Pattern.compile("([0-9]{1,2})车")
        val carriageMatcher = carriagePattern.matcher(text)
        val carriage = if (carriageMatcher.find()) "${carriageMatcher.group(1)}车" else ""

        val seatPattern = Pattern.compile("([0-9]{1,2}[A-Fa-f])号?")
        val seatMatcher = seatPattern.matcher(text)
        val seat = if (seatMatcher.find()) "${seatMatcher.group(1)}号" else ""

        // Extract ticket gate (e.g. 检票口：16A, 检票口呈贡昆明南站 16A)
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
