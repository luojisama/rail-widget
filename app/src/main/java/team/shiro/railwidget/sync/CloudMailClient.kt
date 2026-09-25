package team.shiro.railwidget.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import team.shiro.railwidget.data.api.RailwayApiService
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.parser.Parser12306
import java.util.concurrent.TimeUnit

object CloudMailClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Synchronize emails from Cloud Mail instance (e.g. mail.shiro.team)
     * Returns the list of parsed and saved trips.
     */
    fun sync(
        baseUrl: String,
        email: String,
        password: String,
        dbHelper: TripDatabaseHelper
    ): Result<List<Trip>> {
        return try {
            val rootUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val token = login(rootUrl, email, password)
                ?: return Result.failure(Exception("登录失败，请检查账号密码或邮箱地址"))

            val emails = fetchAllEmails(rootUrl, token)
            val parsedTrips = mutableListOf<Trip>()

            for (emailItem in emails) {
                val subject = emailItem.optString("subject")
                val content = emailItem.optString("content")

                // Only parse emails likely from 12306
                if (subject.contains("12306") || subject.contains("购票") || content.contains("12306.cn") || content.contains("中国铁路")) {
                    val trips = Parser12306.parseEmail(content)
                    for (t in trips) {
                        // Enrich with public 12306 timetable
                        val enriched = RailwayApiService.enrichTrip(t)
                        dbHelper.insertOrUpdateTrip(enriched)
                        parsedTrips.add(enriched)
                    }
                }
            }

            Result.success(parsedTrips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Call POST /api/login to retrieve JWT Token
     */
    fun login(baseUrl: String, email: String, pass: String): String? {
        val loginUrl = "$baseUrl/api/login"
        val jsonPayload = JSONObject().apply {
            put("email", email)
            put("password", pass)
        }

        val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(loginUrl)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val respBody = response.body?.string() ?: return null
            val obj = JSONObject(respBody)
            if (obj.optInt("code") == 200) {
                val data = obj.optJSONObject("data")
                return data?.optString("token")
            }
        }
        return null
    }

    /**
     * Call GET /api/allEmail/list to fetch emails
     */
    fun fetchAllEmails(baseUrl: String, token: String): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        val url = "$baseUrl/api/allEmail/list?size=30"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", token)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return list
            val respBody = response.body?.string() ?: return list
            val obj = JSONObject(respBody)
            val data = obj.optJSONObject("data")
            val arr = data?.optJSONArray("list") ?: return list

            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i))
            }
        }
        return list
    }
}
