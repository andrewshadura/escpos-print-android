/**
 * Copyright (C) 2018 Andrej Shadura
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details. You should have
 * received a copy of the GNU General Public License along with this
 * program; if not, see <http://www.gnu.org/licenses/>.
 */
package me.shadura.escposprint.printservice

import android.graphics.Bitmap
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFontDescriptor
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import me.shadura.escposprint.L
import me.shadura.escposprint.printservice.utils.encodeForPrinter
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.floor

private fun PDFontDescriptor.isBold(): Boolean {
    return this.isForceBold || this.fontName.contains("Bold")
}

private const val LINEWIDTH = 32

fun String.padCentre(length: Int, padChar: Char = ' '): String {
    if (length < 0)
        throw IllegalArgumentException("Desired length $length is less than zero.")
    if (length <= this.length)
        return this.substring(0, this.length)

    val halfPad = (length - this.length + 1) / 2
    var s = String()
    s += padChar.toString().repeat(halfPad)
    s += this
    s += padChar.toString().repeat(length - s.length)
    return s
}

fun Iterable<ByteArray>.joinTo(buffer: ByteArrayOutputStream,
                               separator: CharSequence = ", ",
                               prefix: CharSequence = "",
                               postfix: CharSequence = "",
                               limit: Int = -1,
                               truncated: CharSequence = "..."): ByteArrayOutputStream {
    buffer.write(prefix.toString().toByteArray())
    var count = 0
    for (element in this) {
        if (++count > 1) buffer.write(separator.toString().toByteArray())
        if (limit < 0 || count <= limit) {
            buffer.write(element)
        } else break
    }
    if (limit in 0..(count - 1)) buffer.write(truncated.toString().toByteArray())
    buffer.write(postfix.toString().toByteArray())
    return buffer
}

fun String.removeAccents(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}

sealed class LineElement
data class TextElement(var text: String,
                       val bold: Boolean,
                       val size: Int,
                       val height: Int,
                       val start: Float,
                       var end: Float) : LineElement() {
    fun appendTextPosition(position: TextPosition) {
        this.text += position.unicode
        this.end = position.xDirAdj + position.widthDirAdj
    }

    constructor(position: TextPosition) :
            this(text = position.unicode,
                 bold = position.font.fontDescriptor.isBold(),
                 size = floor(position.fontSize).toInt(),
                 height = floor(position.heightDir).toInt(),
                 start = position.xDirAdj,
                 end = position.xDirAdj + position.widthDirAdj)
}
data class RuleElement(val start: Float, val end: Float) : LineElement()
data class ImageElement(val name: String, val image: Bitmap) : LineElement()

sealed class PageElement {
    open var top: Float = Float.NaN
}

object NewLine : PageElement()
data class TextLine(override var top: Float, val width: Float, val elements: MutableList<LineElement>) : PageElement() {
    fun appendTextPosition(position: TextPosition) {
        if (this.elements.isEmpty() || (this.elements.last() !is TextElement)) {
            this.elements.add(TextElement(position))
        } else {
            (this.elements.last() as TextElement).let {
                when {
                    position.font.fontDescriptor.isBold() != it.bold ||
                    floor(position.fontSize).toInt() != it.size ||
                    position.xDirAdj > (it.end + position.widthOfSpace * 3) -> {
                        this.elements.add(TextElement(position))
                    }

                    else -> {
                        it.appendTextPosition(position)
                    }
                }
            }
        }
    }
}

class PDFStyledTextStripper : PDFTextStripper() {
    private var currentLine: PageElement = NewLine
    val textLines : MutableList<PageElement> = mutableListOf()
    private var currentWidth = 0.0f
    private val noPos = PointF(Float.NaN, Float.NaN)
    private var fromPos = noPos
    private var toPos = noPos
    private var col = 0
    private var sizes = emptyList<Int>()
    private var columns = emptyList<Int>()

    private fun lineOffset(): Float {
        return currentPageNo * currentPage.mediaBox.height
    }

    override fun processOperator(operator: Operator, operands: List<COSBase>) {
        when (operator.name) {
            "Do" -> { /* draw object */
                val objectName = operands[0] as COSName
                val xObject = resources.getXObject(objectName)
                when (xObject) {
                    is PDImageXObject -> {
                        val ctm = graphicsState.currentTransformationMatrix
                        this.drawImage(xObject, ctm.translateX,
                                currentPage.mediaBox.height - ctm.translateY - ctm.scalingFactorY)
                    }
                }
            }
            "w" -> { /* set line width */
                if (operands.size == 1) {
                    val width = operands[0] as COSNumber
                    currentWidth = width.floatValue()
                }
            }
            "m" -> { /* move to */
                if (operands.size == 2) {
                    val x = operands[0] as COSNumber
                    val y = operands[1] as COSNumber
                    fromPos = transformedPoint(x.floatValue(), y.floatValue())
                }
            }
            "l" -> { /* line to*/
                if (operands.size == 2) {
                    val x = operands[0] as COSNumber
                    val y = operands[1] as COSNumber
                    toPos = transformedPoint(x.floatValue(), y.floatValue())
                    if ((fromPos != noPos) && (abs(fromPos.y - toPos.y) < 1.0f) && (currentWidth > 0.1f)) {
                        this.drawLine(fromPos.x, toPos.x, currentPage.mediaBox.height - toPos.y)
                    }
                }
            }
        }
        super.processOperator(operator, operands)
    }

    private fun drawImage(xObject: PDImageXObject, x: Float, y: Float) {
        val text = "image: ${xObject.width}x${xObject.height}"
        textLines.add(TextLine(y + lineOffset(), currentPage.mediaBox.width, mutableListOf(ImageElement(text, xObject.image))))
    }

    private fun drawLine(start: Float, end: Float, y: Float) {
        if (textLines.isEmpty() || ((textLines.last() as TextLine).elements.last() !is RuleElement)) {
            textLines.add(TextLine(y + lineOffset(), currentPage.mediaBox.width, mutableListOf(RuleElement(start, end))))
        }
    }

    override fun endDocument(document: PDDocument) {
        if (currentLine is TextLine) {
            textLines.add(currentLine)
            currentLine = NewLine
        }
        textLines.sortBy { it.top }

        /*
         * Find all elements which overlap lines with other elements
         * Append those element to the overlapping lines
         * Remove the original lines
         *
         * Effect: "snap to grid"
         * */
        do {
            val toRemove = textLines.filter {
                it is TextLine && it.elements[0] is TextElement
            }.zipWithNext().mapNotNull {
                val (first, second) = it as Pair<TextLine, TextLine>
                if (first.elements.isEmpty() || second.elements.isEmpty()) {
                    return@mapNotNull null
                }
                val firstElement = first.elements[0] as TextElement
                val secondElement = second.elements[0] as TextElement
                val firstTop     = floor(first.top - firstElement.height / 2).toInt()
                val firstBottom  = floor(first.top + firstElement.height / 2 + firstElement.height).toInt()
                val secondTop    = floor(second.top - secondElement.height / 2).toInt()
                val secondBottom = floor(second.top + secondElement.height / 2 + firstElement.height).toInt()
                when {
                    firstTop == secondTop -> {
                        if (secondElement.start < firstElement.start) {
                            second.elements.addAll(first.elements)
                            first.elements.clear()
                            second.elements.sortBy { (it as TextElement).start }
                            first
                        } else {
                            first.elements.addAll(second.elements)
                            second.elements.clear()
                            first.elements.sortBy { (it as TextElement).start }
                            second
                        }
                    }
                    firstBottom > secondTop -> {
                        if (secondElement.start < firstElement.start) {
                            second.elements.addAll(first.elements)
                            first.elements.clear()
                            second.elements.sortBy { (it as TextElement).start }
                            first
                        } else {
                            null
                        }
                    }
                    secondBottom < firstBottom -> {
                        if (secondElement.start > firstElement.start) {
                            first.elements.addAll(second.elements)
                            second.elements.clear()
                            first.elements.sortBy { (it as TextElement).start }
                            second
                        } else {
                            null
                        }
                    }
                    else -> {
                        null
                    }
                }
            }
            textLines.removeAll(toRemove)
        } while (toRemove.isNotEmpty())
        super.endDocument(document)
    }

    override fun writeString(text: String?, textPositions: List<TextPosition>?) {
        textPositions?.forEach { position: TextPosition ->
            if (currentLine is TextLine && (currentLine as TextLine).top != (position.yDirAdj + lineOffset())) {
                textLines.add(currentLine)
                currentLine = NewLine
            }
            if (currentLine is NewLine) {
                currentLine = TextLine(position.yDirAdj + lineOffset(), currentPage.mediaBox.width, mutableListOf())
            }
            (currentLine as TextLine).appendTextPosition(position)
        }
    }

    private fun boldFont(text: String, bold: Boolean): ByteArray {
        return boldFont(text.toByteArray(Charset.forName("windows-1250")),
                bold)
    }

    private fun boldFont(bytes: ByteArray, bold: Boolean): ByteArray {
        return if (bold) {
            byteArrayOf(0x1b, 0x45, 1) + bytes + byteArrayOf(0x1b, 0x45, 0)
        } else bytes
    }

    private fun largeFont(bytes: ByteArray, large: Boolean): ByteArray {
        return if (large) {
            byteArrayOf(0x1d, 0x21, 1) + bytes + byteArrayOf(0x1d, 0x21, 0)
        } else bytes
    }

    private fun smallFont(text: String, small: Boolean): ByteArray {
        return smallFont((if (small) {
            text.removeAccents()
        } else text).toByteArray(Charset.forName("windows-1250")), small)
    }

    private fun smallFont(bytes: ByteArray, small: Boolean): ByteArray {
        return if (small) {
            byteArrayOf(0x1b, 0x4d, 1) + bytes + byteArrayOf(0x1b, 0x4d, 0)
        } else bytes
    }

    private fun centre(enable: Boolean): ByteArray {
        return byteArrayOf(0x1b, 0x61, if (enable) 1 else 0)
    }

    private fun isCentred(line: TextLine, element: LineElement): Boolean {
        return when (element) {
            is RuleElement -> {
                val right = line.width - element.end
                val left = element.start
                (abs(left - right) < 1.0) && (left > 0.10 * line.width)
            }
            is TextElement -> {
                val right = line.width - element.end
                val left = element.start
                val leftMax = 0.20 * line.width
                L.i("left = $left, leftMax = $leftMax, right = $right")
                (abs(left - right) < 1.0) && (left > 0.20 * line.width)
            }
            else -> false
        }
    }

    private fun printText(line: TextLine, element: TextElement): ByteArray {
        val centred = isCentred(line, element)
        val paddedText = when {
            centred ->
                element.text.padCentre(columns[col])
            columns.size > 1 && col >= columns.lastIndex ->
                element.text.padStart(columns[col])
            col > columns.lastIndex ->
                element.text.padEnd(columns.last())
            else ->
                element.text.padEnd(columns[col])
        }
        col++ /* only count text columns */
        val large = element.size == sizes.last()
        val small = (sizes.size > 2) && (element.size == sizes.first())
        return largeFont(boldFont(smallFont(paddedText, small), element.bold),
                large)
    }

    fun getBytes(document: PDDocument): ByteArray {
        this.getText(document)
        sizes = textLines.flatMap { line ->
            if (line is TextLine) {
                line.elements.mapNotNull { element ->
                    if (element is TextElement) {
                        element.size
                    } else null
                }
            } else emptyList()
        }.distinct().sorted()
        L.i("font sizes: $sizes")
        val bytes = ByteArrayOutputStream()
        for (line in textLines) (line as TextLine).let {
            col = 0
            
            if (line.elements.size == 2) {
                val textLeft = (line.elements[0] as TextElement).text
                val textRight = (line.elements[1] as TextElement).text
                val padLength = LINEWIDTH - textLeft.length - textRight.length
                val text = textLeft +
                           " ".repeat(if (padLength > 0) padLength else 1) +
                           textRight
                (line.elements[0] as TextElement).text = text
                line.elements.removeAt(1)
            }

            L.i("${line.top} (${line.width}): ${line.elements}")
            columns = when (line.elements.count()) {
                1 ->
                    listOf(32)
                2 ->
                    listOf(16, 16)
                3 ->
                    listOf(10, 12, 10)
                4 ->
                    listOf(8, 8, 8, 8)
                5 ->
                    listOf(7, 5, 8, 5, 7)
                else ->
                    listOf(1)
            }

            line.elements.forEach { element ->
                bytes.write(when (element) {
                    is TextElement -> {
                        printText(line, element)
                    }
                    is ImageElement -> {
                        val scaledBitmap = Bitmap.createScaledBitmap(
                                element.image,
                                element.image.width * 2,
                                element.image.height * 2,
                                false)
                        centre(true) + scaledBitmap.encodeForPrinter() + centre(false)
                    }
                    is RuleElement -> {
                        val startChar = floor(element.start / 12.0).toInt()
                        val endChar = floor(element.end / 12.0).toInt()
                        "=".repeat(32).toByteArray()
                    }
                })
            }
            bytes.write("\n".toByteArray())
        }
        return bytes.toByteArray()
    }
}
