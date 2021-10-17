package me.shadura.escpos

import java.io.ByteArrayOutputStream
import java.util.*

val replacements = mapOf(
    'ș' to "ş",
    'ț' to "ţ",
    'Ș' to "Ş",
    'Ț' to "Ţ",
    'ﬁ' to "fi",
    'ﬂ' to "fl",
    'ﬀ' to "ff",
    'ﬃ' to "ffi",
    'ﬄ' to "ffl",
    'ĳ' to "ij",
    'Ĳ' to "IJ",
    'Ǳ' to "DZ",
    'ǲ' to "Dz",
    'ǳ' to "dz",
    'Ǆ' to "DŽ",
    'ǅ' to "Dž",
    'ǆ' to "dž",
    'Ǉ' to "LJ",
    'ǈ' to "Lj",
    'ǉ' to "lj",
    'Ǌ' to "NJ",
    'ǋ' to "Nj",
    'ǌ' to "nj"
)

class Encoder(val dialect: Dialect) {
    val codepages = LinkedList(dialect.supportedCharsets.keys)

    fun encodeToPairs(s: String): List<Pair<Codepage, ByteArray>> {
        val out = mutableListOf<Pair<Codepage, ByteArray>>()
        var curcp: Codepage? = null
        val acc = mutableListOf<Byte>()
        val mapped = s.fold("") { r, c ->
            r + (replacements[c] ?: c)
        }
        for (c in mapped) {
            for (cp in codepages) {
                if (cp.canEncode(c)) {
                    curcp = cp
                    acc.add(cp.encode(c))
                    if (cp != codepages.first()) {
                        codepages.remove(cp)
                        codepages.push(cp)
                    }
                    break
                } else {
                    if (acc.isNotEmpty() && curcp is Codepage) {
                        out.add(Pair(curcp, acc.toByteArray()))
                    }
                    acc.clear()
                }
            }
        }
        if (acc.isNotEmpty() && curcp is Codepage) {
            out.add(Pair(curcp, acc.toByteArray()))
        }
        return out
    }

    fun encode(s: String): ByteArray {
        val pairs = encodeToPairs(s)

        val bytes = ByteArrayOutputStream()
        for (pair in pairs) {
            bytes.write(byteArrayOf(0x1b, 0x74, dialect.supportedCharsets.getValue(pair.first).toByte()))
            bytes.write(pair.second)
        }
        return bytes.toByteArray()
    }
}