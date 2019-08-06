/*
 * Copyright 2017 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.shadura.escpos

import android.graphics.Bitmap
import android.graphics.Color
import me.shadura.escposprint.BuildConfig

import java.io.ByteArrayOutputStream
import kotlin.experimental.or

fun Bitmap.encodeForPrinter(dialect: Dialect? = null): ByteArray {
    val rasteriser = BitmapEncoder(dialect)
    return rasteriser.printImage(this)
}

fun greyToV(colour: Int): Float {
    /* TODO: this is a hack! */
    return Color.red(colour) / 255.0f
}

class BitmapEncoder(val dialect: Dialect? = null) {
    private val printerBuffer = ByteArrayOutputStream()

    private fun addLineFeed(numLines: Int) {
        if (numLines <= 1) {
            printerBuffer.write(LF)
        } else {
            printerBuffer.write(PRINTER_PRINT_AND_FEED)
            printerBuffer.write(numLines)
        }
    }

    internal fun printImage(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        val controlByte = byteArrayOf((0x00ff and width).toByte(), (0xff00 and width shr 8).toByte())
        val pixels = IntArray(width * height)
        val errors = Array(height) { IntArray(width) }

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val threshold = 128

        for (row in 0 until height - 1) {
            for (col in 1 until width - 1) {
                val index = row * width + col
                val pixel = pixels[index]
                val grey = (0.21 * Color.red(pixel) + 0.72 * Color.green(pixel) + 0.07 * Color.blue(pixel)).toInt() + errors[row][col]
                val mono = if (grey < threshold) {
                    0
                } else {
                    255
                }
                if (BuildConfig.FLAVOR == "plus") {
                    val error = grey - mono
                    errors[row][col + 1] += (7 * error) / 16
                    errors[row + 1][col - 1] += (3 * error) / 16
                    errors[row + 1][col] += (5 * error) / 16
                    errors[row + 1][col + 1] += (1 * error) / 16
                }
                pixels[index] = Color.rgb(mono, mono, mono)
            }
        }

        val bandHeight = 24

        // Bands of pixels are sent that are 8 pixels high.  Iterate through bitmap
        // 24 rows of pixels at a time, capturing bytes representing vertical slices 1 pixel wide.
        // Each bit indicates if the pixel at that position in the slice should be dark or not.
        for (row in 0 until height step bandHeight) {
            printerBuffer.write(PRINTER_SET_LINE_SPACE_24)

            // Need to send these two sets of bytes at the beginning of each row.
            printerBuffer.write(PRINTER_SELECT_BIT_IMAGE_MODE)
            printerBuffer.write(controlByte)

            // Columns, unlike rows, are one at a time.
            for (col in 0 until width) {
                val bandBytes = byteArrayOf(0x0, 0x0, 0x0)

                // Ugh, the nesting of for loops.  For each starting row/col position, evaluate
                // each pixel in a column, or "band", 24 pixels high.  Convert into 3 bytes.
                for (rowOffset in 0..7) {
                    // Because the printer only maintains correct height/width ratio
                    // at the highest density, where it takes 24 bit-deep slices, process
                    // a 24-bit-deep slice as 3 bytes.
                    val pixelSlice = FloatArray(3)
                    val pixel1Row = row + rowOffset
                    val pixel2Row = row + rowOffset + 8
                    val pixel3Row = row + rowOffset + 16

                    // If we go past the bottom of the image, just send white pixels so the printer
                    // doesn't do anything.  Everything still needs to be sent in sets of 3 rows.
                    pixelSlice[0] = greyToV(pixels.elementAtOrNull(pixel1Row * width + col)
                            ?: Color.WHITE)
                    pixelSlice[1] = greyToV(if (pixel2Row >= bitmap.height) {
                        Color.WHITE
                    } else {
                        pixels.elementAtOrNull(pixel2Row * width + col) ?: Color.WHITE
                    })
                    pixelSlice[2] = greyToV(if (pixel3Row >= bitmap.height) {
                        Color.WHITE
                    } else {
                        pixels.elementAtOrNull(pixel3Row * width + col) ?: Color.WHITE
                    })

                    val isDark = booleanArrayOf(pixelSlice[0] < 0.5,
                                                pixelSlice[1] < 0.5,
                                                pixelSlice[2] < 0.5)

                    isDark.forEachIndexed { i, b ->
                        if (b) {
                            bandBytes[i] = bandBytes[i] or (1 shl 7 - rowOffset).toByte()
                        }
                    }
                }
                printerBuffer.write(bandBytes)
            }
            printerBuffer.write(dialect?.bitImageAdvance() ?: byteArrayOf())
        }
        return printerBuffer.toByteArray()
    }

    companion object {
        private val ESC: Byte = 0x1B
        private val PRINTER_SET_LINE_SPACE_24 = byteArrayOf(ESC, 0x33, 24)
        // Slowing down the printer a little and increasing dot density, in order to make the QR
        // codes darker (they're a little faded at default settings).
        // Bytes represent the following: (first two): Print settings.
        // Max heating dots: Units of 8 dots.  11 means 88 dots.
        // Heating time: Units of 10 uS.  120 means 1.2 milliseconds.
        // Heating interval: Units of 10 uS. 50 means 0.5 milliseconds.
        val PRINTER_INITIALIZE = byteArrayOf(ESC, 0x40)
        val PRINTER_DARKER_PRINTING = byteArrayOf(ESC, 0x37, 11, 0x7F, 50)
        val PRINTER_PRINT_AND_FEED = byteArrayOf(ESC, 0x64)
        val LF = byteArrayOf(0xA);
        private val PRINTER_SELECT_BIT_IMAGE_MODE = byteArrayOf(ESC, 0x2A, 33)
    }

}
