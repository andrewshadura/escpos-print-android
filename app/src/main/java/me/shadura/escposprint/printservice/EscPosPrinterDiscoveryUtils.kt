/**
 * Copyright (C) 2015—2017 Benoit Duffez
 * Copyright (C) 2018      Andrej Shadura
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

import android.print.PrintAttributes
import android.print.PrintAttributes.MediaSize.*

import java.util.Locale
import java.util.regex.Pattern

import ch.ethz.vppserver.schema.ippclient.AttributeValue

/**
 * Misc util methods
 */
internal object EscPosPrinterDiscoveryUtils {
    /**
     * Compute the resolution from the [AttributeValue]
     *
     * @param id             resolution ID (nullable)
     * @param attributeValue attribute (resolution) value
     * @return resolution parsed into a [android.print.PrintAttributes.Resolution]
     */
    fun getResolutionFromAttributeValue(id: String, attributeValue: AttributeValue): PrintAttributes.Resolution {
        val resolution = attributeValue.value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val horizontal: Int
        val vertical: Int
        horizontal = Integer.parseInt(resolution[0])
        vertical = Integer.parseInt(resolution[1])
        return PrintAttributes.Resolution(id, String.format(Locale.ENGLISH, "%d×%d dpi", horizontal, vertical), horizontal, vertical)
    }

    /**
     * Compute the media size from the [AttributeValue]
     *
     * @param attributeValue attribute (media size) value
     * @return media size parsed into a [PrintAttributes.MediaSize]
     */
    fun getMediaSizeFromAttributeValue(attributeValue: AttributeValue): PrintAttributes.MediaSize? {
        val value = attributeValue.value.toUpperCase(Locale.ENGLISH)
        return when {
            value.startsWith("ISO_A0") -> ISO_A0
            value.startsWith("ISO_A10") -> ISO_A10
            value.startsWith("ISO_A1") -> ISO_A1
            value.startsWith("ISO_A2") -> ISO_A2
            value.startsWith("ISO_A3") -> ISO_A3
            value.startsWith("ISO_A4") -> ISO_A4
            value.startsWith("ISO_A5") -> ISO_A5
            value.startsWith("ISO_A6") -> ISO_A6
            value.startsWith("ISO_A7") -> ISO_A7
            value.startsWith("ISO_A8") -> ISO_A8
            value.startsWith("ISO_A9") -> ISO_A9
            value.startsWith("ISO_B0") -> ISO_B0
            value.startsWith("ISO_B10") -> ISO_B10
            value.startsWith("ISO_B1") -> ISO_B1
            value.startsWith("ISO_B2") -> ISO_B2
            value.startsWith("ISO_B3") -> ISO_B3
            value.startsWith("ISO_B4") -> ISO_B4
            value.startsWith("ISO_B5") -> ISO_B5
            value.startsWith("ISO_B6") -> ISO_B6
            value.startsWith("ISO_B7") -> ISO_B7
            value.startsWith("ISO_B8") -> ISO_B8
            value.startsWith("ISO_B9") -> ISO_B9
            value.startsWith("ISO_C0") -> ISO_C0
            value.startsWith("ISO_C10") -> ISO_C10
            value.startsWith("ISO_C1") -> ISO_C1
            value.startsWith("ISO_C2") -> ISO_C2
            value.startsWith("ISO_C3") -> ISO_C3
            value.startsWith("ISO_C4") -> ISO_C4
            value.startsWith("ISO_C5") -> ISO_C5
            value.startsWith("ISO_C6") -> ISO_C6
            value.startsWith("ISO_C7") -> ISO_C7
            value.startsWith("ISO_C8") -> ISO_C8
            value.startsWith("ISO_C9") -> ISO_C9
            value.startsWith("JIS_B0") -> JIS_B0
            value.startsWith("JIS_B10") -> JIS_B10
            value.startsWith("JIS_B1") -> JIS_B1
            value.startsWith("JIS_B2") -> JIS_B2
            value.startsWith("JIS_B3") -> JIS_B3
            value.startsWith("JIS_B4") -> JIS_B4
            value.startsWith("JIS_B5") -> JIS_B5
            value.startsWith("JIS_B6") -> JIS_B6
            value.startsWith("JIS_B7") -> JIS_B7
            value.startsWith("JIS_B8") -> JIS_B8
            value.startsWith("JIS_B9") -> JIS_B9
            value.startsWith("JIS_EXEC") -> JIS_EXEC
            value.startsWith("JPN_CHOU2") -> JPN_CHOU2
            value.startsWith("JPN_CHOU3") -> JPN_CHOU3
            value.startsWith("JPN_CHOU4") -> JPN_CHOU4
            value.startsWith("JPN_HAGAKI") -> JPN_HAGAKI
            value.startsWith("JPN_KAHU") -> JPN_KAHU
            value.startsWith("JPN_KAKU2") -> JPN_KAKU2
            value.startsWith("JPN_OUFUKU") -> JPN_OUFUKU
            value.startsWith("JPN_YOU4") -> JPN_YOU4
            value.startsWith("NA_FOOLSCAP") -> NA_FOOLSCAP
            value.startsWith("NA_GOVT_LETTER") -> NA_GOVT_LETTER
            value.startsWith("NA_INDEX_3X5") -> NA_INDEX_3X5
            value.startsWith("NA_INDEX_4X6") -> NA_INDEX_4X6
            value.startsWith("NA_INDEX_5X8") -> NA_INDEX_5X8
            value.startsWith("NA_JUNIOR_LEGAL") -> NA_JUNIOR_LEGAL
            value.startsWith("NA_LEDGER") -> NA_LEDGER
            value.startsWith("NA_LEGAL") -> NA_LEGAL
            value.startsWith("NA_LETTER") -> NA_LETTER
            value.startsWith("NA_MONARCH") -> NA_MONARCH
            value.startsWith("NA_QUARTO") -> NA_QUARTO
            value.startsWith("NA_TABLOID") -> NA_TABLOID
            value.startsWith("OM_DAI_PA_KAI") -> OM_DAI_PA_KAI
            value.startsWith("OM_JUURO_KU_KAI") -> OM_JUURO_KU_KAI
            value.startsWith("OM_PA_KAI") -> OM_PA_KAI
            value.startsWith("PRC_10") -> PRC_10
            value.startsWith("PRC_16K") -> PRC_16K
            value.startsWith("PRC_1") -> PRC_1
            value.startsWith("PRC_2") -> PRC_2
            value.startsWith("PRC_3") -> PRC_3
            value.startsWith("PRC_4") -> PRC_4
            value.startsWith("PRC_5") -> PRC_5
            value.startsWith("PRC_6") -> PRC_6
            value.startsWith("PRC_7") -> PRC_7
            value.startsWith("PRC_8") -> PRC_8
            value.startsWith("PRC_9") -> PRC_9
            value.startsWith("ROC_16K") -> ROC_16K
            value.startsWith("ROC_8K") -> ROC_8K
            value.startsWith("UNKNOWN_LANDSCAPE") -> UNKNOWN_LANDSCAPE
            value.startsWith("UNKNOWN_PORTRAIT") -> UNKNOWN_PORTRAIT
            else -> {
                val m = Pattern.compile("_((\\d*\\.?\\d+)x(\\d*\\.?\\d+)([a-z]+))$").matcher(value)
                if (m.find()) {
                    try {
                        var x = java.lang.Float.parseFloat(m.group(2))
                        var y = java.lang.Float.parseFloat(m.group(3))
                        when (m.group(4)) {
                            "mm" -> {
                                x /= 25.4f
                                y /= 25.4f
                                x *= 1000f
                                y *= 1000f
                            }
                            // fall through
                            "in" -> {
                                x *= 1000f
                                y *= 1000f
                            }
                            else -> return null
                        }
                        return PrintAttributes.MediaSize(value, m.group(1), Math.round(x), Math.round(y))
                    } catch (ignored: NumberFormatException) {
                    }

                }
                return null
            }
        }
    }
}
