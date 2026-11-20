package com.tarixustoz.app

import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class DrawableBitmapAuditTest {

    private data class ImageInfo(val format: String, val width: Int, val height: Int)

    private fun readImageDimensions(file: File): ImageInfo? {
        val bytes = file.readBytes()
        if (bytes.size < 24) return null

        // PNG signature: 89 50 4E 47 0D 0A 1A 0A, IHDR width@16, height@20
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            val w = ((bytes[16].toInt() and 0xFF) shl 24) or
                ((bytes[17].toInt() and 0xFF) shl 16) or
                ((bytes[18].toInt() and 0xFF) shl 8) or
                (bytes[19].toInt() and 0xFF)
            val h = ((bytes[20].toInt() and 0xFF) shl 24) or
                ((bytes[21].toInt() and 0xFF) shl 16) or
                ((bytes[22].toInt() and 0xFF) shl 8) or
                (bytes[23].toInt() and 0xFF)
            return ImageInfo("PNG", w, h)
        }

        // RIFF .... WEBP
        if (bytes.size >= 30 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            val chunk = String(bytes, 12, 4)
            when (chunk) {
                "VP8 " -> {
                    val w = ((bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0xFF) shl 8)) and 0x3FFF
                    val h = ((bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0xFF) shl 8)) and 0x3FFF
                    return ImageInfo("WEBP-VP8", w, h)
                }
                "VP8L" -> {
                    val b0 = bytes[21].toInt() and 0xFF
                    val b1 = bytes[22].toInt() and 0xFF
                    val b2 = bytes[23].toInt() and 0xFF
                    val b3 = bytes[24].toInt() and 0xFF
                    val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                    val w = (bits and 0x3FFF) + 1
                    val h = ((bits shr 14) and 0x3FFF) + 1
                    return ImageInfo("WEBP-VP8L", w, h)
                }
                "VP8X" -> {
                    val w = 1 + ((bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or ((bytes[26].toInt() and 0xFF) shl 16))
                    val h = 1 + ((bytes[27].toInt() and 0xFF) or ((bytes[28].toInt() and 0xFF) shl 8) or ((bytes[29].toInt() and 0xFF) shl 16))
                    return ImageInfo("WEBP-VP8X", w, h)
                }
            }
        }

        // JPEG FF D8
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var i = 2
            while (i + 9 < bytes.size) {
                if (bytes[i] != 0xFF.toByte()) {
                    i++
                    continue
                }
                val marker = bytes[i + 1].toInt() and 0xFF
                if (marker == 0xFF) {
                    i++
                    continue
                }
                if (marker in 0xC0..0xC3 || marker in 0xC5..0xC7 || marker in 0xC9..0xCB || marker in 0xCD..0xCF) {
                    val h = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                    val w = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                    return ImageInfo("JPEG", w, h)
                }
                if (i + 3 >= bytes.size) break
                val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                if (len < 2) break
                i += 2 + len
            }
        }
        return null
    }

    @Test
    fun checkAllDrawableImageDimensionsAndSizes() {
        val drawableDir = File("src/main/res/drawable")
        val files = drawableDir.listFiles()?.filter {
            it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp")
        }?.sortedBy { it.name } ?: emptyList()

        val oversized = mutableListOf<String>()
        val report = StringBuilder()

        for (file in files) {
            val info = readImageDimensions(file)
            val bytes = file.length()
            if (info == null) {
                val line = "${file.name}: UNDECODABLE (${bytes}B)"
                report.appendLine(line)
                oversized.add(line)
            } else {
                val rgbaBytes = info.width.toLong() * info.height.toLong() * 4L
                val line = "${file.name} [${info.format}]: ${info.width}x${info.height}px, file=${bytes / 1024}KB, decodedRGBA=${rgbaBytes / (1024 * 1024)}MB"
                report.appendLine(line)
                if (info.width > 2048 || info.height > 2048 || rgbaBytes > 25L * 1024L * 1024L) {
                    oversized.add(line)
                }
            }
        }
        println(report.toString())
        if (oversized.isNotEmpty()) {
            fail("Found oversized/corrupt drawables:\n" + oversized.joinToString("\n"))
        }
    }
}
