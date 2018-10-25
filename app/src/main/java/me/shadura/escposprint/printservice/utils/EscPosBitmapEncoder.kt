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
package me.shadura.escposprint.printservice.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Color.colorToHSV

import java.io.ByteArrayOutputStream
import kotlin.experimental.or

fun Bitmap.encodeForPrinter(): ByteArray {
    val rasteriser = EscPosBitmapEncoder()
    return rasteriser.printImage(this)
}

fun colourToV(colour: Int): Float {
    var hsv = FloatArray(3)
    colorToHSV(colour, hsv)
    return hsv[2]
}

class EscPosBitmapEncoder {
    private val printerBuffer = ByteArrayOutputStream()

    private fun configure() {
        printerBuffer.write(PRINTER_INITIALIZE)
        printerBuffer.write(PRINTER_DARKER_PRINTING)
    }

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

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val BAND_HEIGHT = 24

        // Bands of pixels are sent that are 8 pixels high.  Iterate through bitmap
        // 24 rows of pixels at a time, capturing bytes representing vertical slices 1 pixel wide.
        // Each bit indicates if the pixel at that position in the slice should be dark or not.
        var row = 0
        while (row < height) {
            printerBuffer.write(PRINTER_SET_LINE_SPACE_24)

            // Need to send these two sets of bytes at the beginning of each row.
            printerBuffer.write(PRINTER_SELECT_BIT_IMAGE_MODE)
            printerBuffer.write(controlByte)

            // Columns, unlike rows, are one at a time.
            for (col in 0 until width) {
                val bandBytes = byteArrayOf(0x0, 0x0, 0x0)

                // Ugh, the nesting of forloops.  For each starting row/col position, evaluate
                // each pixel in a column, or "band", 24 pixels high.  Convert into 3 bytes.
                for (rowOffset in 0..7) {
                    // Because the printer only maintains correct height/width ratio
                    // at the highest density, where it takes 24 bit-deep slices, process
                    // a 24-bit-deep slice as 3 bytes.
                    val pixelSlice = FloatArray(3)
                    val pixel2Row = row + rowOffset + 8
                    val pixel3Row = row + rowOffset + 16

                    // If we go past the bottom of the image, just send white pixels so the printer
                    // doesn't do anything.  Everything still needs to be sent in sets of 3 rows.
                    pixelSlice[0] = colourToV(bitmap.getPixel(col, row + rowOffset))
                    pixelSlice[1] = colourToV(if (pixel2Row >= bitmap.height) {
                        Color.WHITE
                    } else {
                        bitmap.getPixel(col, pixel2Row)
                    })
                    pixelSlice[2] = colourToV(if (pixel3Row >= bitmap.height) {
                        Color.WHITE
                    } else {
                        bitmap.getPixel(col, pixel3Row)
                    })

                    val isDark = booleanArrayOf(pixelSlice[0] < 0.5,
                                                pixelSlice[1] < 0.5,
                                                pixelSlice[2] < 0.5)

                    // Towing that fine line between "should I forloop or not".  This will only
                    // ever be 3 elements deep.
                    if (isDark[0]) bandBytes[0] = bandBytes[0] or (1 shl 7 - rowOffset).toByte()
                    if (isDark[1]) bandBytes[1] = bandBytes[1] or (1 shl 7 - rowOffset).toByte()
                    if (isDark[2]) bandBytes[2] = bandBytes[2] or (1 shl 7 - rowOffset).toByte()
                }
                printerBuffer.write(bandBytes)
            }
            addLineFeed(1)
            row += BAND_HEIGHT
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
