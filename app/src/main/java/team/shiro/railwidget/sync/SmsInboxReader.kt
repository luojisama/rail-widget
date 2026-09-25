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
     * 读取系统短信收件箱中来自 12306 或正文包含 12306 的购票短信
     */
    fun read12306SmsList(context: Context, limit: Int = 30): List<SmsRecord> {
        val results = mutableListOf<SmsRecord>()
        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val selection = "${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.BODY} LIKE ?"
            val selectionArgs = arrayOf("%12306%", "%12306%")
            val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val address = if (addrIdx != -1) cursor.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    results.add(SmsRecord(id, address, body, date))
                }
            }
        } catch (_: Exception) {
            // Permission denied or provider unavailable
        }
        return results
    }

    /**
     * 一键读取并解析导入全部 12306 短信行程
     */
    fun syncFromSmsInbox(context: Context, limit: Int = 30): List<Trip> {
        val records = read12306SmsList(context, limit)
        val importedTrips = mutableListOf<Trip>()
        val db = TripDatabaseHelper.getInstance(context)

        for (record in records) {
            val trip = Parser12306.parseSms(record.body)
            if (trip != null) {
                // 联动 12306 官方时刻表接口补全到站时间与途经站
                val enriched = try {
                    RailwayApiService.enrichTrip(trip)
                } catch (_: Exception) {
                    trip
                }
                db.insertOrUpdateTrip(enriched)
                importedTrips.add(enriched)
            }
        }
        return importedTrips
    }
}
