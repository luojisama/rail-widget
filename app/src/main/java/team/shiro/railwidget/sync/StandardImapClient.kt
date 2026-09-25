package team.shiro.railwidget.sync

import team.shiro.railwidget.data.api.RailwayApiService
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.parser.Parser12306
import java.util.Properties
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.MimeMultipart

object StandardImapClient {

    /**
     * Connect to generic IMAP mailbox, search for 12306 emails, parse and save them
     */
    fun sync(
        host: String,
        port: Int,
        username: String,
        password: String,
        useSsl: Boolean,
        dbHelper: TripDatabaseHelper
    ): Result<List<Trip>> {
        var store: Store? = null
        var inbox: Folder? = null
        val parsedTrips = mutableListOf<Trip>()

        return try {
            val props = Properties().apply {
                put("mail.imap.host", host)
                put("mail.imap.port", port.toString())
                if (useSsl) {
                    put("mail.imap.ssl.enable", "true")
                    put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.imap.socketFactory.fallback", "false")
                    put("mail.imap.socketFactory.port", port.toString())
                }
                put("mail.imap.auth", "true")
                put("mail.imap.timeout", "10000")
                put("mail.imap.connectiontimeout", "10000")
            }

            val session = Session.getInstance(props)
            store = session.getStore(if (useSsl) "imaps" else "imap")
            store.connect(host, port, username, password)

            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)

            val count = inbox.messageCount
            val start = (count - 150).coerceAtLeast(1)
            val messages = inbox.getMessages(start, count)

            for (msg in messages.reversed()) {
                val subject = msg.subject ?: ""
                if (subject.contains("12306") || subject.contains("购票") || subject.contains("铁路")) {
                    val content = getTextFromMessage(msg)
                    val trips = Parser12306.parseEmail(content)
                    for (t in trips) {
                        val enriched = RailwayApiService.enrichTrip(t)
                        dbHelper.insertOrUpdateTrip(enriched)
                        parsedTrips.add(enriched)
                    }
                }
            }

            Result.success(parsedTrips)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { inbox?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    private fun getTextFromMessage(message: javax.mail.Message): String {
        return try {
            if (message.isMimeType("text/plain")) {
                message.content.toString()
            } else if (message.isMimeType("text/html")) {
                message.content.toString()
            } else if (message.isMimeType("multipart/*")) {
                val mimeMultipart = message.content as MimeMultipart
                getTextFromMimeMultipart(mimeMultipart)
            } else {
                message.content?.toString() ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun getTextFromMimeMultipart(mimeMultipart: MimeMultipart): String {
        val result = StringBuilder()
        val count = mimeMultipart.count
        for (i in 0 until count) {
            val bodyPart = mimeMultipart.getBodyPart(i)
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.content)
            } else if (bodyPart.isMimeType("text/html")) {
                result.append(bodyPart.content)
            } else if (bodyPart.content is MimeMultipart) {
                result.append(getTextFromMimeMultipart(bodyPart.content as MimeMultipart))
            }
        }
        return result.toString()
    }
}
