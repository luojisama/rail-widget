package team.shiro.railwidget.data.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripTimeline
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Downloaded reference data stays private to the app; no passenger/ticket data is sent. */
object LulutongData {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private const val MAX_PACKAGE = 32 * 1024 * 1024

    private val MANIFEST_URLS = listOf(
        "http://down.lltskb.com/android.ver",
        "http://223.107.87.50:8011/android.ver"
    )

    private val FALLBACK_DATA_URLS = listOf(
        "http://down.lltskb.com/an.db",
        "http://223.107.87.50:8011/an.db"
    )

    private val RECOGNIZED_ENTRIES = setOf("routes.dat", "jlb.dat", "extra.dat", "t.i", "s.i", "plat.dat")

    internal data class JlbInfo(
        val trainModel: String,
        val formation: String,
        val diningCar: String,
        val diningCarNote: String,
        val depotIndex: Int,
        val rawDesc: String
    )

    internal class LulutongCache(
        val version: String,
        val lineMileages: Map<String, String>,
        val trainToJlb: Map<String, JlbInfo>,
        val trainIndexMap: Map<String, Int>,
        val stationIndexMap: Map<String, Int>,
        val extraStrings: List<String>,
        val trainToExtra: Map<Int, Int>,
        val platforms: Map<Long, String>
    )

    @Volatile
    private var memoryCache: LulutongCache? = null

    @Synchronized
    fun enrich(context: Context, trip: Trip): Trip = runCatching {
        val dir = File(context.filesDir, "lulutong").apply { mkdirs() }
        val checked = File(dir, "checked")
        if (!checked.exists() || System.currentTimeMillis() - checked.lastModified() > 86_400_000L) {
            checked.writeText("")
            runCatching { update(dir) }
        }
        val packageFile = File(dir, "data.zip")
        if (!packageFile.exists()) return trip
        val version = File(dir, "version").takeIf { it.exists() }?.readText().orEmpty()

        var cache = memoryCache
        if (cache == null || cache.version != version) {
            val entries = unpack(packageFile.readBytes())
            cache = parsePackage(entries, version)
            memoryCache = cache
        }

        enrichFromCache(trip, cache)
    }.getOrDefault(trip)

    internal fun enrichFromCache(trip: Trip, cache: LulutongCache): Trip {
        val codeUpper = trip.trainCode.trim().uppercase()
        val jlb = cache.trainToJlb[codeUpper]
        val tIdx = cache.trainIndexMap[codeUpper]

        val model = jlb?.trainModel.orEmpty()
        val formation = jlb?.formation.orEmpty()
        val diningCar = jlb?.diningCar.orEmpty()
        val diningNote = jlb?.diningCarNote.orEmpty()

        val extraStr = if (tIdx != null && cache.trainToExtra.containsKey(tIdx)) {
            val eIdx = cache.trainToExtra[tIdx]!!
            cache.extraStrings.getOrNull(eIdx).orEmpty()
        } else if (jlb != null && jlb.depotIndex in cache.extraStrings.indices) {
            cache.extraStrings[jlb.depotIndex]
        } else ""

        val (bureau, depot) = parseBureauAndDepot(extraStr)

        var depPlatform = trip.platform
        var depPlatformRef = trip.platformReference

        val stops = trip.stops.map { stop ->
            val sNorm = TripTimeline.station(stop.stationName)
            val sIdx = cache.stationIndexMap[sNorm] ?: cache.stationIndexMap[stop.stationName]
            val refPlat = if (tIdx != null && sIdx != null) {
                cache.platforms[(tIdx.toLong() shl 32) or sIdx.toLong()]
            } else null

            val usableRefPlat = refPlat?.takeIf { it.isNotBlank() && it != "待定" }
            val newStopPlat = stop.platform.ifBlank { usableRefPlat.orEmpty() }
            val newStopPlatRef = if (stop.platform.isBlank() && usableRefPlat != null) true else stop.platformReference

            if (sNorm == TripTimeline.station(trip.departureStation) && depPlatform.isBlank() && usableRefPlat != null) {
                depPlatform = usableRefPlat
                depPlatformRef = true
            }

            stop.copy(
                lineMileage = cache.lineMileages[stop.stationName].orEmpty().ifBlank { stop.lineMileage },
                platform = newStopPlat,
                platformReference = newStopPlatRef
            )
        }

        if (depPlatform.isBlank() && tIdx != null) {
            val depNorm = TripTimeline.station(trip.departureStation)
            val depIdx = cache.stationIndexMap[depNorm] ?: cache.stationIndexMap[trip.departureStation]
            val refPlat = if (depIdx != null) cache.platforms[(tIdx.toLong() shl 32) or depIdx.toLong()] else null
            if (refPlat != null && refPlat.isNotBlank() && refPlat != "待定") {
                depPlatform = refPlat
                depPlatformRef = true
            }
        }

        val version = cache.version
        val newModel = trip.trainModel.ifBlank { model }
        val newBureau = trip.railwayBureau.ifBlank { bureau }
        val newDepot = trip.crewDepot.ifBlank { depot }
        val newFormation = trip.formation.ifBlank { formation }
        val newDiningCar = trip.diningCar.ifBlank { diningCar }
        val newDiningNote = trip.diningCarNote.ifBlank { diningNote }

        val hasChanges = newModel != trip.trainModel || newBureau != trip.railwayBureau ||
            newDepot != trip.crewDepot || newFormation != trip.formation ||
            newDiningCar != trip.diningCar || newDiningNote != trip.diningCarNote ||
            depPlatform != trip.platform || stops != trip.stops

        if (!hasChanges && version.isBlank()) return trip

        return trip.copy(
            trainModel = newModel,
            railwayBureau = newBureau,
            crewDepot = newDepot,
            formation = newFormation,
            diningCar = newDiningCar,
            diningCarNote = newDiningNote,
            platform = depPlatform,
            platformReference = depPlatformRef,
            stops = stops,
            lulutongVersion = version.ifBlank { trip.lulutongVersion },
            dataSource = (trip.dataSource.split(" · ") + "路路通参考$version").filter { it.isNotBlank() }.distinct().joinToString(" · "),
            dataCheckedAt = System.currentTimeMillis()
        )
    }

    private fun get(url: String, max: Int): ByteArray = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        check(r.isSuccessful)
        r.body!!.byteStream().use { input -> input.readBytesBounded(max) }
    }

    private fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            check(out.size() + count <= max) { "路路通数据包超过限制" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun update(dir: File) {
        var manifestText = ""
        for (url in MANIFEST_URLS) {
            runCatching {
                manifestText = get(url, 256 * 1024).toString(Charsets.UTF_8).removePrefix("\uFEFF")
            }
            if (manifestText.isNotBlank()) break
        }
        check(manifestText.isNotBlank()) { "无法获取路路通版本信息" }

        val cleanedManifest = manifestText.replace("\"name\": 应用宝", "\"name\": \"应用宝\"")
            .replace("\"name\": 应用宝\"", "\"name\": \"应用宝\"")
        val json = JSONObject(cleanedManifest)
        val version = json.getJSONObject("version").getString("data")
        check(version.matches(Regex("\\d{8}")))

        val versionFile = File(dir, "version")
        if (versionFile.exists() && versionFile.readText() == version && File(dir, "data.zip").exists()) return

        val candidateUrls = mutableListOf<String>()
        val dataUrlArray = json.optJSONArray("dataUrl")
        if (dataUrlArray != null) {
            for (i in 0 until dataUrlArray.length()) {
                val u = dataUrlArray.optString(i).trim()
                if (u.isNotBlank()) candidateUrls += u
            }
        }
        for (u in FALLBACK_DATA_URLS) {
            if (u !in candidateUrls) candidateUrls += u
        }

        var downloadedBytes: ByteArray? = null
        for (u in candidateUrls) {
            runCatching {
                downloadedBytes = get(u, MAX_PACKAGE)
            }
            if (downloadedBytes != null) break
        }
        val bytes = checkNotNull(downloadedBytes) { "路路通数据包下载失败" }
        val entries = unpack(bytes)
        check(entries.containsKey("routes.dat") || entries.containsKey("jlb.dat"))

        val temp = File(dir, "data.tmp").apply { writeBytes(bytes) }
        check(temp.renameTo(File(dir, "data.zip")))
        versionFile.writeText(version)
    }

    internal fun unpack(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                check(++count <= 512)
                val data = zip.readBytesBounded(16 * 1024 * 1024)
                total += data.size
                check(total <= 64 * 1024 * 1024)
                if (entry.name in RECOGNIZED_ENTRIES) result[entry.name] = data
                zip.closeEntry()
            }
        }
        return result
    }

    internal fun expandTrainCodes(raw: String): Set<String> {
        val results = mutableSetOf<String>()
        val trainCodeRegex = Regex("""^([A-Za-z]*)(\d+)([A-Za-z]*)$""")
        for (item in raw.split('#')) {
            val trimmed = item.trim()
            if (trimmed.isEmpty()) continue
            val clean = trimmed.trimStart('0').ifEmpty { trimmed }
            if (clean.contains('/')) {
                val parts = clean.split('/')
                val first = parts[0]
                results += first.uppercase()
                val match = trainCodeRegex.matchEntire(first)
                if (match != null) {
                    val (prefix, numStr, suffix) = match.destructured
                    val baseLen = numStr.length
                    for (i in 1 until parts.size) {
                        val part = parts[i].trim()
                        if (part.isEmpty()) continue
                        if (part.any { it.isLetter() }) {
                            val partClean = part.trimStart('0').ifEmpty { part }
                            results += partClean.uppercase()
                        } else {
                            val partLen = part.length
                            val newNum = if (partLen <= baseLen) {
                                numStr.dropLast(partLen) + part
                            } else {
                                part
                            }
                            results += (prefix + newNum + suffix).uppercase()
                        }
                    }
                } else {
                    for (i in 1 until parts.size) {
                        val part = parts[i].trim().trimStart('0')
                        if (part.isNotEmpty()) results += part.uppercase()
                    }
                }
            } else {
                results += clean.uppercase()
            }
        }
        return results
    }

    internal fun cleanModel(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        val first = s.split(',', '，', ';', '；').first().trim()
        val withoutReunion = first.replace(Regex("(?i)型?重联.*$"), "").trim()
        val withoutPower = withoutReunion.split(Regex("(?i)(?=ac380v|dc600v)")).first().trim()
        val result = withoutPower.trimEnd('.', '。')
        return result.ifBlank { s }
    }

    internal fun extractFormation(raw: String): String {
        val tags = mutableListOf<String>()
        if (raw.contains("重联")) tags += "重联"
        else if (raw.contains("长编")) tags += "长编组"

        val power = mutableListOf<String>()
        if (raw.contains("DC600V", ignoreCase = true)) power += "DC600V"
        else if (raw.contains("AC380V", ignoreCase = true)) power += "AC380V"

        if (raw.contains("双管供风") || raw.contains("双风管")) power += "双管供风"
        else if (raw.contains("单管供风")) power += "单管供风"

        if (raw.contains("集便") && !raw.contains("非集便")) power += "集便"
        else if (raw.contains("直排")) power += "直排"

        if (power.isNotEmpty()) tags += power.joinToString(" ")
        return tags.joinToString(" ")
    }

    internal fun extractDiningCar(raw: String): Pair<String, String> {
        if (!raw.contains('餐')) return Pair("", "")
        val carMatch = Regex("""(\d+(?:[、,，]\d+)*)\s*号车.*?(?:餐车|餐座)""").find(raw)
            ?: Regex("""餐车在\s*(\d+)\s*车厢""").find(raw)
        val carNumber = carMatch?.groupValues?.get(1)?.replace(',', '、')?.replace('，', '、')?.let { "${it}车" }.orEmpty()
        return Pair(carNumber, raw)
    }

    internal fun parseBureauAndDepot(raw: String): Pair<String, String> {
        val s = raw.trim()
        if (s.isEmpty() || s == "暂无" || s == "国际联运") return Pair("", "")

        val bureauPrefixes = listOf(
            "哈尔滨局" to "哈尔滨局", "哈局" to "哈尔滨局",
            "沈阳局" to "沈阳局", "沈局" to "沈阳局",
            "北京局" to "北京局", "京局" to "北京局",
            "呼和浩特局" to "呼和浩特局", "呼和局" to "呼和浩特局",
            "郑州局" to "郑州局",
            "武汉局" to "武汉局", "武局" to "武汉局",
            "西安局" to "西安局",
            "济南局" to "济南局",
            "上海局" to "上海局", "上局" to "上海局",
            "南昌局" to "南昌局",
            "广州局" to "广州局", "广铁" to "广州局",
            "南宁局" to "南宁局", "宁局" to "南宁局",
            "成都局" to "成都局",
            "昆明局" to "昆明局",
            "兰州局" to "兰州局",
            "乌鲁木齐局" to "乌鲁木齐局", "乌局" to "乌鲁木齐局",
            "青藏铁路公司" to "青藏铁路公司", "青藏公司" to "青藏铁路公司", "青藏铁路" to "青藏铁路公司"
        )

        for ((prefix, standardBureau) in bureauPrefixes) {
            if (s.startsWith(prefix)) {
                val rest = s.removePrefix(prefix).trim()
                return Pair(standardBureau, rest)
            }
        }
        if (s.endsWith("局")) return Pair(s, "")
        return Pair("", s)
    }

    internal fun parsePackage(entries: Map<String, ByteArray>, version: String): LulutongCache {
        val lineMileages = entries["routes.dat"]?.let { stationMileages(it) }.orEmpty()

        val extraStrings = mutableListOf<String>()
        val trainToExtra = mutableMapOf<Int, Int>()
        entries["extra.dat"]?.let { b ->
            runCatching {
                val input = DataInputStream(ByteArrayInputStream(b))
                val count = input.readUnsignedShort()
                repeat(count) {
                    if (input.available() > 0) {
                        val slen = input.readUnsignedByte()
                        val bytes = ByteArray(slen)
                        input.readFully(bytes)
                        extraStrings += bytes.toString(Charsets.UTF_8)
                    }
                }
                if (input.available() >= 2) {
                    val count2 = input.readUnsignedShort()
                    val totalRecords = minOf(count2, input.available() / 5)
                    repeat(totalRecords) {
                        val tIdx = input.readUnsignedShort()
                        val eIdx = input.readUnsignedShort()
                        input.readUnsignedByte() // flag
                        trainToExtra[tIdx] = eIdx
                    }
                }
            }
        }

        val trainIndexMap = mutableMapOf<String, Int>()
        entries["t.i"]?.let { b ->
            runCatching {
                val input = DataInputStream(ByteArrayInputStream(b))
                val count = input.readUnsignedShort()
                var idx = 0
                while (input.available() >= 2 && idx < count) {
                    val slen = input.readUnsignedShort()
                    if (slen > input.available()) break
                    val bytes = ByteArray(slen)
                    input.readFully(bytes)
                    val rawCode = bytes.toString(Charsets.UTF_8)
                    for (c in expandTrainCodes(rawCode)) {
                        trainIndexMap.putIfAbsent(c, idx)
                    }
                    idx++
                }
            }
        }

        val stationIndexMap = mutableMapOf<String, Int>()
        entries["s.i"]?.let { b ->
            runCatching {
                val input = DataInputStream(ByteArrayInputStream(b))
                val count = input.readUnsignedShort()
                var idx = 0
                while (input.available() >= 2 && idx < count) {
                    val slen = input.readUnsignedShort()
                    if (slen > input.available()) break
                    val bytes = ByteArray(slen)
                    input.readFully(bytes)
                    val sName = bytes.toString(Charsets.UTF_8)
                    stationIndexMap.putIfAbsent(sName, idx)
                    val normalized = TripTimeline.station(sName)
                    if (normalized != sName) {
                        stationIndexMap.putIfAbsent(normalized, idx)
                    }
                    idx++
                }
            }
        }

        val platforms = mutableMapOf<Long, String>()
        entries["plat.dat"]?.let { b ->
            runCatching {
                val input = DataInputStream(ByteArrayInputStream(b))
                val count = input.readInt()
                repeat(count) {
                    if (input.available() < 9) return@repeat
                    val tIdx = input.readInt()
                    val sIdx = input.readInt()
                    val slen = input.readUnsignedByte()
                    if (slen > input.available()) return@repeat
                    val bytes = ByteArray(slen)
                    input.readFully(bytes)
                    val plat = bytes.toString(Charsets.UTF_8)
                    val key = (tIdx.toLong() shl 32) or (sIdx.toLong() and 0xFFFFFFFFL)
                    platforms[key] = plat
                }
            }
        }

        val trainToJlb = mutableMapOf<String, JlbInfo>()
        entries["jlb.dat"]?.let { b ->
            runCatching {
                parseJlbData(b, trainToJlb)
            }
        }

        return LulutongCache(
            version = version,
            lineMileages = lineMileages,
            trainToJlb = trainToJlb,
            trainIndexMap = trainIndexMap,
            stationIndexMap = stationIndexMap,
            extraStrings = extraStrings,
            trainToExtra = trainToExtra,
            platforms = platforms
        )
    }

    private fun parseJlbData(data: ByteArray, out: MutableMap<String, JlbInfo>) {
        val marker = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1)
        var i = 0
        while (i + marker.size + 4 < data.size) {
            var matched = true
            for (m in marker.indices) {
                if (data[i + m] != marker[m]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                var p = i + marker.size
                val depotIdx = ((data[p].toInt() and 0xff) shl 8) or (data[p + 1].toInt() and 0xff)
                p += 2
                val slen1 = data[p].toInt() and 0xff
                p += 1
                if (p + slen1 <= data.size) {
                    val desc = ByteArray(slen1).also { System.arraycopy(data, p, it, 0, slen1) }.toString(Charsets.UTF_8)
                    p += slen1
                    if (p < data.size) {
                        val slen2 = data[p].toInt() and 0xff
                        p += 1
                        if (p + slen2 <= data.size) {
                            val trainsStr = ByteArray(slen2).also { System.arraycopy(data, p, it, 0, slen2) }.toString(Charsets.UTF_8)
                            p += slen2

                            val model = cleanModel(desc)
                            val formation = extractFormation(desc)
                            val (diningCar, diningNote) = extractDiningCar(desc)
                            val info = JlbInfo(
                                trainModel = model,
                                formation = formation,
                                diningCar = diningCar,
                                diningCarNote = diningNote,
                                depotIndex = depotIdx,
                                rawDesc = desc
                            )
                            for (c in expandTrainCodes(trainsStr)) {
                                out.putIfAbsent(c, info)
                            }
                        }
                    }
                }
                i = p
            } else {
                i++
            }
        }
    }

    private fun DataInputStream.string(): String {
        var length = readUnsignedByte()
        if (length and 0x80 != 0) length = ((length and 0x7f) shl 8) or readUnsignedByte()
        check(length <= available())
        return ByteArray(length).also { readFully(it) }.toString(Charsets.UTF_8)
    }

    /** Compatibility helper: preserves old signature and supports slash-expanded train codes. */
    internal fun findModel(data: ByteArray, trainCode: String): String {
        val targets = expandTrainCodes(trainCode)
        val matches = mutableSetOf<String>()
        val markerShort = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1)

        var i = 0
        while (i + markerShort.size + 4 < data.size) {
            var isMarker = true
            for (m in markerShort.indices) {
                if (data[i + m] != markerShort[m]) {
                    isMarker = false
                    break
                }
            }
            if (isMarker) {
                runCatching {
                    var p = i + markerShort.size
                    // depot index (2 bytes)
                    p += 2
                    val slen1 = data[p].toInt() and 0xff
                    p += 1
                    val desc = ByteArray(slen1).also { System.arraycopy(data, p, it, 0, slen1) }.toString(Charsets.UTF_8)
                    p += slen1
                    val slen2 = data[p].toInt() and 0xff
                    p += 1
                    val trainsStr = ByteArray(slen2).also { System.arraycopy(data, p, it, 0, slen2) }.toString(Charsets.UTF_8)

                    val expanded = expandTrainCodes(trainsStr)
                    if (targets.any { it in expanded } || trainsStr.split('#').any { it.equals(trainCode, true) }) {
                        if (desc.any { it in '\u4e00'..'\u9fff' } || desc.isNotBlank()) {
                            matches += desc
                        }
                    }
                }
            }
            i++
        }
        return matches.singleOrNull().orEmpty()
    }

    /** These are railway line chainages, NOT train mileage. Keep the line name alongside the value. */
    internal fun stationMileages(data: ByteArray): Map<String, String> = runCatching {
        val input = DataInputStream(ByteArrayInputStream(data))
        check(input.skipBytes(0x2f) == 0x2f)
        val map = mutableMapOf<String, MutableList<String>>()
        while (input.available() > 12) {
            input.readInt()
            val route = input.string()
            check(route.isNotBlank())
            val count = input.readInt()
            check(count in 0..20000)
            repeat(count) {
                val name = input.string()
                input.string()
                input.readInt()
                val mile = input.readInt()
                if (mile >= 0) map.getOrPut(name) { mutableListOf() }.add("$route $mile km")
            }
        }
        map.mapValues { it.value.distinct().joinToString("；") }
    }.getOrDefault(emptyMap())
}
