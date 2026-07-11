package snd.komf.util

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.math.BigInteger
import javax.imageio.ImageIO
import kotlin.math.ceil

private const val similarityThreshold = 0.1
private const val width = 32
private const val height = 32
private const val bitResolution = width * height

fun compareImages(image1: ByteArray, image2: ByteArray): Boolean {
    val decoded1 = ImageIO.read(image1.inputStream())
    val decoded2 = ImageIO.read(image2.inputStream())
    if (decoded1 == null || decoded2 == null) return false

    val hash1 = hash(decoded1)
    val hash2 = hash(decoded2)

    return normalizedHammingDistance(hash1, hash2) <= similarityThreshold
}

// ponytail: replaces twelvemonkeys ResampleOp with JDK Graphics2D scaling
fun hash(source: BufferedImage): BigInteger {
    val resampled = source.resample(width, height)

    val luma = getLuma(resampled)
    val avgPixelValue = luma.map { it.average() }.average()

    val bytes = ByteArray(ceil(bitResolution / 8.0).toInt())
    luma.asSequence().flatMap { it.asSequence() }
        .forEachIndexed { index, value ->
            if (value >= avgPixelValue) {
                bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (7 - index % 8))).toByte()
            }
        }

    return BigInteger(1, bytes)
}

private fun BufferedImage.resample(width: Int, height: Int): BufferedImage {
    val scaled = BufferedImage(width, height, type)
    val graphics = scaled.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.drawImage(this, 0, 0, width, height, null)
    graphics.dispose()
    return scaled
}

fun normalizedHammingDistance(hash1: BigInteger, hash2: BigInteger): Double {
    return hammingDistance(hash1, hash2) / bitResolution.toDouble()
}

private fun hammingDistance(hash1: BigInteger, hash2: BigInteger): Int {
    return hash1.xor(hash2).bitCount()
}

private fun getLuma(image: BufferedImage): Array<IntArray> {
    val luma = Array(image.width) { IntArray(image.height) }
    for (y in 0..<image.height) {
        for (x in 0..<image.width) {
            val color = Color(image.getRGB(x, y))
            val lum = color.red * 0.299 + color.green * 0.587 + color.blue * 0.114
            luma[x][y] = if (lum > 255) 255 else lum.toInt()
        }
    }
    return luma
}
