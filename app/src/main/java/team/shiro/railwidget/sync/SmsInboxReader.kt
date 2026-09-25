package team.shiro.railwidget.sync

import android.content.Context
import android.provider.Telephony
import team.shiro.railwidget.data.api.RailwayApiService
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.parser.Parser12306

object SmsInboxReader {

    data class SmsRecord(
        val id: Long,
        val address: String,
        val body: String,
        val timestamp: Long
    )

    /**
     * 读取系统短信收件箱中来自 12306 或正文包含铁路购票信息的短信（兼容 MIUI / 澎湃 OS 通知类短信）
     */
    fun read12306SmsList(context: Context, limit: Int = 2000): List<SmsRecord> {
        val results = mutableListOf<SmsRecord>()
        val urisToTry = listOf(
            Telephony.Sms.Inbox.CONTENT_URI,
            Telephony.Sms.CONTENT_URI,
            android.net.Uri.parse("content://sms/inbox"),
            android.net.Uri.parse("content://sms")
        )

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        for (targetUri in urisToTry) {
            try {
                // 注意：在 ContentResolver 中不可在 sortOrder 内传 LIMIT，否则部分系统会抛出 IllegalArgumentException
                val sortOrder = "${Telephony.Sms.DATE} DESC"
                context.contentResolver.query(targetUri, projection, null, null, sortOrder)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                    val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                    while (cursor.moveToNext() && results.size < limit) {
                        val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                        val address = if (addrIdx != -1) cursor.getString(addrIdx) ?: "" else ""
                        val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L

                        // 过滤纯登录/验证码类短信
                        if (body.contains("验证码") || body.contains("动态码") || body.contains("校验码")) {
                            continue
                        }

                        // 匹配 12306 购票、改签、退票或铁路短信
                        val is12306Sms = address.contains("12306") ||
                                body.contains("12306") ||
                                body.contains("铁路") ||
                                (body.contains("次") && (body.contains("开") || body.contains("购票") || body.contains("改签")))

                        if (is12306Sms) {
                            // 避免不同 URI 重复添加同一条短信（以短信正文与发送时间联合判重）
                            if (results.none { it.timestamp == date && it.body == body }) {
                                results.add(SmsRecord(id, address, body, date))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SmsInboxReader", "Error querying SMS uri: $targetUri", e)
            }
        }
        return results
    }

    /**
     * 一键读取并解析导入全部 12306 短信行程（包含未出行与历史行程）
     */
    fun syncFromSmsInbox(context: Context, limit: Int = 2000): List<Trip> {
        val records = read12306SmsList(context, limit)
        val importedTrips = mutableListOf<Trip>()
        val db = TripDatabaseHelper.getInstance(context)

        for (record in records) {
            val trip = Parser12306.parseSms(record.body, record.timestamp)
            if (trip != null) {
                // 仅对当前或未来两日内车次尝试联网补全时刻表，历史车次直接秒级入库，杜绝网络阻塞
                val enriched = if (isUpcomingOrRecent(trip.departureDate)) {
                    try {
                        RailwayApiService.enrichTrip(trip)
                    } catch (_: Exception) {
                        trip
                    }
                } else {
                    trip
                }
                db.insertOrUpdateTrip(enriched)
                importedTrips.add(enriched)
            }
        }
        return importedTrips
    }

    private fun isUpcomingOrRecent(departureDate: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            val dep = sdf.parse(departureDate)?.time ?: return false
            dep >= System.currentTimeMillis() - 48 * 3600 * 1000L
        } catch (_: Exception) {
            false
        }
    }
}
