package me.shadura.escpos

import java.io.ByteArrayOutputStream
import java.util.*

class Encoder(val dialect: Dialect) {
    val codepages = LinkedList(dialect.supportedCharsets.keys)

    fun encodeToPairs(s: String): List<Pair<Codepage, ByteArray>> {
        var out = mutableListOf<Pair<Codepage, ByteArray>>()
        var curcp: Codepage? = null
        var acc = mutableListOf<Byte>()
        for (c in s) {
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