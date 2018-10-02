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

import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import me.shadura.escposprint.L

class PDFStyledTextStripper : PDFTextStripper() {
    private var currentBold = false
    private var currentWeight = -1.0f

    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
        textPositions?.forEach {
            val bold = it.font.fontDescriptor.fontWeight >= 700
            if (bold != currentBold) {
                if (bold) {
                    output.write(String(byteArrayOf(0x1b, 0x45, 1)))
                    output.write(String(byteArrayOf(0x1b, 0x0e)))
                } else {
                    output.write(String(byteArrayOf(0x1b, 0x45, 0)))
                    output.write(String(byteArrayOf(0x1b, 0x14)))
                }
                currentBold = bold
            }
            if (currentWeight != it.font.fontDescriptor.fontWeight) {
                currentWeight = it.font.fontDescriptor.fontWeight
            }
            output.write(it.unicode)
        }
    }
}
