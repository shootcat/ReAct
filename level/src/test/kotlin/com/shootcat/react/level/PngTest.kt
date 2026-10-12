package com.shootcat.react.level

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class PngTest {

    private fun encode(image: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()

    /** A picture with flat areas, edges and noise, so every filter of the encoder comes up. */
    private fun painted(type: Int): BufferedImage {
        val img = BufferedImage(53, 37, type)
        var r = 12345
        for (y in 0 until img.height) for (x in 0 until img.width) {
            r = r * 1103515245 + 12345
            val c = when {
                y < 10 -> 0x7a5234
                x < 20 -> 0x2f6fc4
                (x + y) % 7 == 0 -> 0xdcc58a
                else -> (r ushr 8) and 0xFFFFFF
            }
            img.setRGB(x, y, (0xFF shl 24) or c)
        }
        return img
    }

    @Test
    fun `reads what the JVM writes in every common colour type`() {
        for (type in listOf(BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_BYTE_INDEXED, BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_BYTE_BINARY)) {
            val img = painted(type)
            val pic = Png.read(encode(img))
            assertEquals(img.width, pic.width)
            assertEquals(img.height, pic.height)
            for (y in 0 until img.height) for (x in 0 until img.width) {
                // grey is stored as it is; the JVM turns it into a colour through a gamma curve
                val want = if (type == BufferedImage.TYPE_BYTE_GRAY) img.raster.getSample(x, y, 0).let { (0xFF shl 24) or (it shl 16) or (it shl 8) or it } else img.getRGB(x, y)
                assertEquals(want, pic[x, y], "type $type at ($x, $y)")
            }
        }
    }

    @Test
    fun `see-through pixels keep their alpha`() {
        val img = BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(1, 1, 0x80112233.toInt())
        img.setRGB(2, 2, 0xFF445566.toInt())
        val pic = Png.read(encode(img))
        assertEquals(0, pic[0, 0] ushr 24)
        assertEquals(0x80, pic[1, 1] ushr 24)
        assertEquals(0x445566, pic.rgb(2, 2))
    }
}
