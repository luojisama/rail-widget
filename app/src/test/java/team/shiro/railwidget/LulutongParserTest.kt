package team.shiro.railwidget

import org.junit.Assert.*
import org.junit.Test
import team.shiro.railwidget.data.api.LulutongData
import java.io.ByteArrayOutputStream
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
}
