package team.shiro.railwidget.data.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import team.shiro.railwidget.data.model.Trip
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

    @Synchronized
    fun enrich(context: Context, trip: Trip): Trip = runCatching {
        val dir = File(context.filesDir, "lulutong").apply { mkdirs() }
        val checked = File(dir, "checked")
        if (!checked.exists() || System.currentTimeMillis() - checked.lastModified() > 86_400_000L) {
            // Failed updates retain the last valid package and are retried on the next day.
            checked.writeText("")
            runCatching { update(dir) }
        }
        val packageFile = File(dir, "data.zip")
        if (!packageFile.exists()) return trip
        val entries = unpack(packageFile.readBytes())
        val model = entries["jlb.dat"]?.let { findModel(it, trip.trainCode) }.orEmpty()
        val references = entries["routes.dat"]?.let { stationMileages(it) }.orEmpty()
        val stops = trip.stops.map { stop -> stop.copy(lineMileage = references[stop.stationName].orEmpty()) }
        if (model.isBlank() && stops == trip.stops) return trip
        val version = File(dir, "version").takeIf { it.exists() }?.readText().orEmpty()
        trip.copy(trainModel = trip.trainModel.ifBlank { model }, stops = stops,
            dataSource = (trip.dataSource.split(" · ") + "路路通参考$version").filter { it.isNotBlank() }.distinct().joinToString(" · "),
            dataCheckedAt = System.currentTimeMillis())
    }.getOrDefault(trip)

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
        val manifest = get("http://down.lltskb.com/android.ver", 256 * 1024).toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val version = JSONObject(manifest.replace("\"name\": 应用宝\"", "\"name\": \"应用宝\""))
            .getJSONObject("version").getString("data")
        check(version.matches(Regex("\\d{8}")))
        val versionFile = File(dir, "version")
        if (versionFile.exists() && versionFile.readText() == version && File(dir, "data.zip").exists()) return
        val bytes = get("http://down.lltskb.com/an.db", MAX_PACKAGE)
        val entries = unpack(bytes)
        check(entries.containsKey("routes.dat") || entries.containsKey("jlb.dat"))
        // Validate before replacing the usable cache. Never extract untrusted ZIP paths.
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
                if (entry.name in setOf("routes.dat", "jlb.dat")) result[entry.name] = data
                zip.closeEntry()
            }
        }
        return result
    }

    private fun DataInputStream.string(): String {
        var length = readUnsignedByte()
        if (length and 0x80 != 0) length = ((length and 0x7f) shl 8) or readUnsignedByte()
        check(length <= available())
        return ByteArray(length).also { readFully(it) }.toString(Charsets.UTF_8)
    }

    internal fun findModel(data: ByteArray, trainCode: String): String {
        val matches = mutableSetOf<String>()
        var i = 0
        val marker = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 0, 4)
        while (i + marker.size < data.size) {
            if (marker.indices.all { data[i + it] == marker[it] }) runCatching {
                val input = DataInputStream(ByteArrayInputStream(data, i + marker.size, data.size - i - marker.size))
                val model = input.string()
                val trains = input.string().split('#')
                if (trains.any { it.equals(trainCode, true) } && model.any { it in '\u4e00'..'\u9fff' }) matches += model
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
