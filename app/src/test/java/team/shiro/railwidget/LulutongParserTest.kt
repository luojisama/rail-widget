package team.shiro.railwidget

import org.junit.Assert.*
import org.junit.Test
import team.shiro.railwidget.data.api.LulutongData
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LulutongParserTest {
    @Test fun matchesWholeTrainCodesOnly() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 0, 4))
        for (value in listOf("复兴号 CR400AF", "G101#G102")) {
            val bytes = value.toByteArray()
            out.write(bytes.size); out.write(bytes)
        }
        assertEquals("复兴号 CR400AF", LulutongData.findModel(out.toByteArray(), "G101"))
        assertEquals("", LulutongData.findModel(out.toByteArray(), "G10"))
    }

    @Test fun findsModelWithSlashExpandedCodes() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 0, 83))
        for (value in listOf("CR400BF-S型重联", "G1955/6/7/8#D6364/1")) {
            val bytes = value.toByteArray()
            out.write(bytes.size); out.write(bytes)
        }
        assertEquals("CR400BF-S型重联", LulutongData.findModel(out.toByteArray(), "G1956"))
        assertEquals("CR400BF-S型重联", LulutongData.findModel(out.toByteArray(), "D6361"))
        assertEquals("", LulutongData.findModel(out.toByteArray(), "G1959"))
    }

    @Test fun ignoresUnrecognizedZipPaths() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("../../outside")); zip.write(byteArrayOf(1)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("jlb.dat")); zip.write(byteArrayOf(2)); zip.closeEntry()
        }
        val result = LulutongData.unpack(out.toByteArray())
        assertEquals(setOf("jlb.dat"), result.keys)
    }

    @Test fun malformedRouteTableProducesNoGuessedMileage() {
        assertTrue(LulutongData.stationMileages(byteArrayOf(1, 2, 3)).isEmpty())
    }

    @Test fun expandsComplexSlashTrainCodes() {
        assertEquals(
            setOf("G1955", "G1956", "G1957", "G1958"),
            LulutongData.expandTrainCodes("G1955/6/7/8")
        )
        assertEquals(
            setOf("D6364", "D6361"),
            LulutongData.expandTrainCodes("D6364/1")
        )
        assertEquals(
            setOf("G1240", "G1237"),
            LulutongData.expandTrainCodes("G1240/37")
        )
        assertEquals(
            setOf("D236", "D237"),
            LulutongData.expandTrainCodes("D236/D237")
        )
        assertEquals(
            setOf("K598", "K599"),
            LulutongData.expandTrainCodes("K598/9")
        )
        assertEquals(
            setOf("D6942"),
            LulutongData.expandTrainCodes("0D6942#D6942")
        )
    }

    @Test fun cleansModelAndExtractsFormation() {
        assertEquals("CR400BF-S", LulutongData.cleanModel("CR400BF-S型重联，G1955/6/7/8石家庄调向"))
        assertEquals("25G型", LulutongData.cleanModel("25G型,DC600V,双管供风,集便,北京丰台调向"))
        assertEquals("CRH1A", LulutongData.cleanModel("CRH1A,4、12号车1-4排为餐座票额禁售"))
        assertEquals("25T型", LulutongData.cleanModel("25T型DC600V,集便"))

        assertEquals("重联", LulutongData.extractFormation("CR400BF-S型重联，G1955/6/7/8"))
        assertEquals("DC600V 双管供风 集便", LulutongData.extractFormation("25G型,DC600V,双管供风,集便"))
        assertEquals("AC380V 单管供风 直排", LulutongData.extractFormation("25G,AC380V,单管供风,直排"))
    }

    @Test fun extractsDiningCarAndNote() {
        val (car1, note1) = LulutongData.extractDiningCar("CRH1A型,4号车1-4排为餐座票额禁售")
        assertEquals("4车", car1)
        assertEquals("CRH1A型,4号车1-4排为餐座票额禁售", note1)

        val (car2, note2) = LulutongData.extractDiningCar("CRH1A,4、12号车1-4排为餐座票额禁售")
        assertEquals("4、12车", car2)
        assertEquals("CRH1A,4、12号车1-4排为餐座票额禁售", note2)

        val (car3, note3) = LulutongData.extractDiningCar("25G型,双管供风,AC380V,集便，餐车在3车厢")
        assertEquals("3车", car3)
        assertEquals("25G型,双管供风,AC380V,集便，餐车在3车厢", note3)

        val (car4, note4) = LulutongData.extractDiningCar("25K，集便，餐车代硬座")
        assertEquals("", car4)
        assertEquals("25K，集便，餐车代硬座", note4)

        val (carNone, noteNone) = LulutongData.extractDiningCar("CR400AF-Z型重联")
        assertEquals("", carNone)
        assertEquals("", noteNone)
    }

    @Test fun parsesBureauAndDepotCorrectly() {
        assertEquals(Pair("上海局", "上海段"), LulutongData.parseBureauAndDepot("上局上海段"))
        assertEquals(Pair("广州局", "广九段"), LulutongData.parseBureauAndDepot("广铁广九段"))
        assertEquals(Pair("广州局", "长沙段"), LulutongData.parseBureauAndDepot("广铁长沙段"))
        assertEquals(Pair("广州局", "长沙客运段"), LulutongData.parseBureauAndDepot("长沙客运段"))
        assertEquals(Pair("北京局", "北京客运段"), LulutongData.parseBureauAndDepot("京局北京客运段"))
        assertEquals(Pair("哈尔滨局", "牡丹江段"), LulutongData.parseBureauAndDepot("哈局牡丹江段"))
        assertEquals(Pair("青藏铁路公司", "西宁段"), LulutongData.parseBureauAndDepot("青藏公司西宁段"))
        assertEquals(Pair("南昌局", ""), LulutongData.parseBureauAndDepot("南昌局"))
        assertEquals(Pair("", ""), LulutongData.parseBureauAndDepot("暂无"))
    }

    @Test fun parsesCompactDiningCorrectly() {
        assertEquals(Pair("09车", "无餐座"), LulutongData.parseCompactDining("9车无餐座"))
        assertEquals(Pair("05车", "餐吧"), LulutongData.parseCompactDining("5车餐吧"))
        assertEquals(Pair("", "餐车代硬座"), LulutongData.parseCompactDining("餐车代硬座"))
    }

    @Test fun endToEndEnrichFromCache() {
        // Construct synthetic package
        // 1. extra.dat
        val extraOut = ByteArrayOutputStream()
        val extraDos = DataOutputStream(extraOut)
        val strings = listOf("暂无", "上局上海段", "广铁广九段")
        extraDos.writeShort(strings.size)
        for (s in strings) {
            val b = s.toByteArray(Charsets.UTF_8)
            extraDos.writeByte(b.size)
            extraDos.write(b)
        }
        // train to extra: train 0 -> extra 1 ("上局上海段")
        extraDos.writeShort(1)
        extraDos.writeShort(0) // train 0
        extraDos.writeShort(1) // extra 1
        extraDos.writeByte(0)  // flag

        // 2. t.i (trains)
        val tiOut = ByteArrayOutputStream()
        val tiDos = DataOutputStream(tiOut)
        tiDos.writeShort(1) // count
        val trainCodeBytes = "G1955/6".toByteArray(Charsets.UTF_8)
        tiDos.writeShort(trainCodeBytes.size)
        tiDos.write(trainCodeBytes)

        // 3. s.i (stations)
        val siOut = ByteArrayOutputStream()
        val siDos = DataOutputStream(siOut)
        siDos.writeShort(2) // count
        for (sname in listOf("北京南", "上海虹桥")) {
            val b = sname.toByteArray(Charsets.UTF_8)
            siDos.writeShort(b.size)
            siDos.write(b)
        }

        // 4. plat.dat
        val platOut = ByteArrayOutputStream()
        val platDos = DataOutputStream(platOut)
        platDos.writeInt(2) // 2 records
        // record 1: train 0, station 0 (北京南) -> "7"
        platDos.writeInt(0)
        platDos.writeInt(0)
        val p1 = "7".toByteArray(Charsets.UTF_8)
        platDos.writeByte(p1.size)
        platDos.write(p1)
        // record 2: train 0, station 1 (上海虹桥) -> "待定" (should be ignored)
        platDos.writeInt(0)
        platDos.writeInt(1)
        val p2 = "待定".toByteArray(Charsets.UTF_8)
        platDos.writeByte(p2.size)
        platDos.write(p2)

        // 5. jlb.dat
        val jlbOut = ByteArrayOutputStream()
        val jlbDos = DataOutputStream(jlbOut)
        jlbDos.write(byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1))
        jlbDos.writeShort(1) // depot index 1 ("上局上海段")
        val descBytes = "CR400BF-S型重联,4号车1-4排为餐座票额禁售".toByteArray(Charsets.UTF_8)
        jlbDos.writeByte(descBytes.size)
        jlbDos.write(descBytes)
        val trainsBytes = "G1955/6".toByteArray(Charsets.UTF_8)
        jlbDos.writeByte(trainsBytes.size)
        jlbDos.write(trainsBytes)

        // 6. T0.dat (17-byte train header + 7-byte stop entries)
        val t0Out = ByteArrayOutputStream()
        t0Out.write(byteArrayOf(0, 0)) // trainIdx = 0
        t0Out.write(ByteArray(14))     // 14-byte header
        t0Out.write(2)                 // stopCnt = 2
        // stop 0: stationIdx=0 (0,0), h=8, m=0, dwell=0, km=0 (0,0)
        t0Out.write(byteArrayOf(0, 0, 8, 0, 0, 0, 0))
        // stop 1: stationIdx=1 (0,1), h=12, m=30, dwell=0, km=1318 (5*255+43 -> 5,43)
        t0Out.write(byteArrayOf(0, 1, 12, 30, 0, 5, 43))

        val entries = mapOf(
            "extra.dat" to extraOut.toByteArray(),
            "t.i" to tiOut.toByteArray(),
            "s.i" to siOut.toByteArray(),
            "plat.dat" to platOut.toByteArray(),
            "jlb.dat" to jlbOut.toByteArray(),
            "T0.dat" to t0Out.toByteArray()
        )

        val cache = LulutongData.parsePackage(entries, "20261108")
        val trip = Trip(
            orderNo = "order1",
            passengerName = "张三",
            trainCode = "G1956",
            departureStation = "北京南",
            arrivalStation = "上海虹桥",
            departureDate = "2026-11-08",
            departureTime = "08:00",
            stops = listOf(
                StopInfo("01", "北京南", "----", "08:00", "始发"),
                StopInfo("02", "上海虹桥", "12:30", "----", "终到")
            )
        )

        val enriched = LulutongData.enrichFromCache(trip, cache)

        assertEquals("CR400BF-S", enriched.trainModel)
        assertEquals("重联", enriched.formation)
        assertEquals("4车", enriched.diningCar)
        assertEquals("CR400BF-S型重联,4号车1-4排为餐座票额禁售", enriched.diningCarNote)
        assertEquals("上海局", enriched.railwayBureau)
        assertEquals("上海段", enriched.crewDepot)
        assertEquals("7", enriched.platform)
        assertTrue(enriched.platformReference)
        assertEquals("7", enriched.stops[0].platform)
        assertTrue(enriched.stops[0].platformReference)
        assertEquals(0.0, enriched.stops[0].mileageKm!!, 0.01)
        assertEquals(1318.0, enriched.stops[1].mileageKm!!, 0.01)
        assertEquals(1318.0, enriched.rideMileageKm!!, 0.01)
        assertEquals(1318.0, enriched.totalMileageKm!!, 0.01)
        assertEquals("", enriched.stops[1].platform)
        assertFalse(enriched.stops[1].platformReference)
        assertEquals("20261108", enriched.lulutongVersion)
        assertTrue(enriched.dataSource.contains("路路通参考20261108"))
    }
}
