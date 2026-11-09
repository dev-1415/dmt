package dev.jyotiraditya.dmt.data.lyrics

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads and rewrites the OpusTags comment block in an Ogg Opus file. */
internal object OpusTags {

    private val OPUS_TAGS = "OpusTags".toByteArray()
    private val OGG_PAGE = "OggS".toByteArray()

    /** Replaces the lyrics comment in [file], keeping every other comment and the audio pages. */
    fun write(file: File, lyrics: String) {
        val bytes = file.readBytes()
        val pages = pages(bytes)
        val tags = tags(bytes, pages)
        val vendor = vendorOf(tags.packet)
        val comments =
            commentsOf(tags.packet).filterNot {
                it.startsWith("LYRICS=", true) || it.startsWith("UNSYNCEDLYRICS=", true)
            }
        val newPacket = buildPacket(vendor, comments + "LYRICS=$lyrics")
        val newPages = buildPages(newPacket, pages[tags.first])
        val shift = newPages.size - (tags.last - tags.first + 1)
        val output = ByteArrayOutputStream(bytes.size + newPacket.size)
        output.write(bytes, 0, pages[tags.first].start)
        newPages.forEach { output.write(it) }
        for (index in tags.last + 1 until pages.size) {
            output.write(rebuildPage(bytes, pages[index], pages[index].sequence + shift))
        }
        file.writeBytes(output.toByteArray())
    }

    private data class Page(
        val start: Int,
        val end: Int,
        val headerType: Int,
        val serial: Int,
        val sequence: Int,
        val segments: IntArray,
        val dataStart: Int,
        val dataEnd: Int,
    )

    private data class Tags(val first: Int, val last: Int, val packet: ByteArray)

    private fun pages(bytes: ByteArray): List<Page> {
        val pages = ArrayList<Page>()
        var offset = 0
        while (offset + 27 <= bytes.size) {
            require(bytes.copyOfRange(offset, offset + 4).contentEquals(OGG_PAGE)) {
                "Not an Ogg file"
            }
            val headerType = bytes[offset + 5].toInt() and 0xFF
            val serial = ByteBuffer.wrap(bytes, offset + 14, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val sequence = ByteBuffer.wrap(bytes, offset + 18, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val segmentCount = bytes[offset + 26].toInt() and 0xFF
            val segments = IntArray(segmentCount) { bytes[offset + 27 + it].toInt() and 0xFF }
            val dataStart = offset + 27 + segmentCount
            val dataEnd = dataStart + segments.sum()
            pages +=
                Page(
                    start = offset,
                    end = dataEnd,
                    headerType = headerType,
                    serial = serial,
                    sequence = sequence,
                    segments = segments,
                    dataStart = dataStart,
                    dataEnd = dataEnd,
                )
            offset = dataEnd
        }
        return pages
    }

    private fun tags(bytes: ByteArray, pages: List<Page>): Tags {
        for (i in pages.indices) {
            val page = pages[i]
            if (page.dataEnd - page.dataStart >= 8 &&
                bytes.copyOfRange(page.dataStart, page.dataStart + 8).contentEquals(OPUS_TAGS)
            ) {
                val output = ByteArrayOutputStream()
                var pageIndex = i
                var done = false
                while (pageIndex < pages.size && !done) {
                    val current = pages[pageIndex]
                    var dataOffset = current.dataStart
                    for (lacing in current.segments) {
                        output.write(bytes, dataOffset, lacing)
                        dataOffset += lacing
                        if (lacing < 255) {
                            done = true
                            break
                        }
                    }
                    pageIndex++
                }
                return Tags(i, pageIndex - 1, output.toByteArray())
            }
        }
        error("The Opus comment block is missing")
    }

    private fun vendorOf(packet: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8)
        val length = buffer.int
        require(length in 0..buffer.remaining()) { "The Opus vendor is malformed" }
        val vendor = ByteArray(length)
        buffer.get(vendor)
        return vendor
    }

    private fun commentsOf(packet: ByteArray): List<String> {
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8)
        val vendorLength = buffer.int
        buffer.position(buffer.position() + vendorLength)
        val count = buffer.int
        require(count in 0..1_000_000) { "The Opus comments are malformed" }
        return buildList {
            repeat(count) {
                val length = buffer.int
                require(length in 0..buffer.remaining()) { "The Opus comments are malformed" }
                val comment = ByteArray(length)
                buffer.get(comment)
                add(comment.toString(Charsets.UTF_8))
            }
        }
    }

    private fun buildPacket(vendor: ByteArray, comments: List<String>): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(OPUS_TAGS)
        output.writeLeInt(vendor.size)
        output.write(vendor)
        output.writeLeInt(comments.size)
        comments.forEach { comment ->
            val bytes = comment.toByteArray(Charsets.UTF_8)
            output.writeLeInt(bytes.size)
            output.write(bytes)
        }
        return output.toByteArray()
    }

    private fun buildPages(packet: ByteArray, original: Page): List<ByteArray> {
        val pages = ArrayList<ByteArray>()
        var offset = 0
        var sequence = original.sequence
        var lastLacing = -1
        while (offset < packet.size) {
            val lacing = ArrayList<Int>()
            var left = packet.size - offset
            while (left >= 255 && lacing.size < 255) {
                lacing += 255
                left -= 255
            }
            if (lacing.size < 255) lacing += left
            val dataLength = lacing.sum()
            pages += buildPage(packet, offset, dataLength, lacing, original, sequence, pages.isEmpty())
            lastLacing = lacing.last()
            offset += dataLength
            sequence++
        }
        if (lastLacing == 255) {
            pages +=
                buildPage(packet, offset, 0, listOf(0), original, sequence, pages.isEmpty())
        }
        return pages
    }

    private fun buildPage(
        packet: ByteArray,
        offset: Int,
        dataLength: Int,
        lacing: List<Int>,
        original: Page,
        sequence: Int,
        first: Boolean,
    ): ByteArray {
        val header = ByteArray(27 + lacing.size)
        OGG_PAGE.copyInto(header, 0)
        header[4] = 0
        header[5] = if (first) original.headerType.toByte() else 0
        header[14] = (original.serial and 0xFF).toByte()
        header[15] = ((original.serial ushr 8) and 0xFF).toByte()
        header[16] = ((original.serial ushr 16) and 0xFF).toByte()
        header[17] = ((original.serial ushr 24) and 0xFF).toByte()
        header[18] = (sequence and 0xFF).toByte()
        header[19] = ((sequence ushr 8) and 0xFF).toByte()
        header[20] = ((sequence ushr 16) and 0xFF).toByte()
        header[21] = ((sequence ushr 24) and 0xFF).toByte()
        header[26] = lacing.size.toByte()
        lacing.forEachIndexed { index, value -> header[27 + index] = value.toByte() }
        val page = ByteArray(header.size + dataLength)
        header.copyInto(page, 0)
        packet.copyInto(page, header.size, offset, offset + dataLength)
        writeCrc(page)
        return page
    }

    private fun rebuildPage(bytes: ByteArray, page: Page, sequence: Int): ByteArray {
        val headerSize = 27 + page.segments.size
        val dataLength = page.dataEnd - page.dataStart
        val output = ByteArray(headerSize + dataLength)
        bytes.copyInto(output, 0, page.start, page.start + headerSize)
        output[18] = (sequence and 0xFF).toByte()
        output[19] = ((sequence ushr 8) and 0xFF).toByte()
        output[20] = ((sequence ushr 16) and 0xFF).toByte()
        output[21] = ((sequence ushr 24) and 0xFF).toByte()
        bytes.copyInto(output, headerSize, page.dataStart, page.dataEnd)
        writeCrc(output)
        return output
    }

    private fun writeCrc(page: ByteArray) {
        page[22] = 0
        page[23] = 0
        page[24] = 0
        page[25] = 0
        val crc = crc(page)
        page[22] = crc.toByte()
        page[23] = (crc ushr 8).toByte()
        page[24] = (crc ushr 16).toByte()
        page[25] = (crc ushr 24).toByte()
    }

    private fun ByteArrayOutputStream.writeLeInt(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private val CRC_TABLE =
        IntArray(256) { index ->
            var value = index shl 24
            repeat(8) {
                value =
                    if (value and 0x80000000.toInt() != 0) {
                        (value shl 1) xor 0x04C11DB7
                    } else {
                        value shl 1
                    }
            }
            value
        }

    private fun crc(bytes: ByteArray): Int {
        var value = 0
        for (byte in bytes) {
            value =
                (value shl 8) xor
                    CRC_TABLE[((value ushr 24) xor (byte.toInt() and 0xFF)) and 0xFF]
        }
        return value
    }
}
