package me.shadura.escpos

import java.nio.charset.Charset

open class Codepage(val name: String) {
    val mapping by lazy {
        ByteArray(128) {
            (it + 128).toByte()
        }.toString(Charset.forName(name)).asSequence().mapIndexed {
            i, v -> v to (i+128).toByte()
        }.toMap()
    }

    open fun canEncode(c: Char): Boolean =
            when {
                c < 128.toChar() ->
                    true
                c in mapping ->
                    true
                else ->
                    false
            }

    fun encode(c: Char): Byte =
            when {
                c < 128.toChar() ->
                    c.toByte()
                c in mapping ->
                    mapping.getValue(c)
                else -> 0
            }
}

class IncompleteCodepage(name: String, val missingCharacters: List<Char>) : Codepage(name) {
    override fun canEncode(c: Char): Boolean {
        if (c in missingCharacters) {
            return false
        }
        return super.canEncode(c)
    }
}