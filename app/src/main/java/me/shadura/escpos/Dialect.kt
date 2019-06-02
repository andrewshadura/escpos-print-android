package me.shadura.escpos

enum class PrinterModel {
    Generic,
    ZiJiang,
    Epson,
    Bixolon,
    Goojprt,
    Xprinter
}

open class Dialect {
    var paperWidth: Int = 58
    val lineWidth: Int
        get() = when (paperWidth) {
            58 ->
                32
            80 ->
                42
            else ->
                32
        }
    val pixelWidth: Int
        get() = when (paperWidth) {
            58 -> 384
            80 -> 512
            else -> 384
        }
    open val supportedCharsets = mapOf(
            Codepage("cp1252") to 16,
            Codepage("cp852") to 18,
            Codepage("cp437") to 0
    )
    fun boldFont(bytes: ByteArray, bold: Boolean): ByteArray {
        return if (bold) {
            byteArrayOf(0x1b, 0x45, 1) + bytes + byteArrayOf(0x1b, 0x45, 0)
        } else bytes
    }

    fun largeFont(bytes: ByteArray, large: Boolean): ByteArray {
        return if (large) {
            byteArrayOf(0x1d, 0x21, 1) + bytes + byteArrayOf(0x1d, 0x21, 0)
        } else bytes
    }

    fun smallFont(bytes: ByteArray, small: Boolean): ByteArray {
        return if (small) {
            byteArrayOf(0x1b, 0x4d, 1) + bytes + byteArrayOf(0x1b, 0x4d, 0)
        } else bytes
    }

    fun centre(enable: Boolean): ByteArray {
        return byteArrayOf(0x1b, 0x61, if (enable) 1 else 0)
    }

    open fun pageStart(): ByteArray =
        byteArrayOf(0xa, 0xa)

    open fun pageFeed(): ByteArray =
        byteArrayOf(0xa, 0xa, 0xa)

    fun getColumns(num: Int): List<Int> {
        return when (lineWidth) {
            32 ->
                when (num) {
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
            42 ->
                when (num) {
                    1 ->
                        listOf(42)
                    2 ->
                        listOf(21, 21)
                    3 ->
                        listOf(14, 14, 14)
                    4 ->
                        listOf(12, 10, 10, 12)
                    5 ->
                        listOf(9, 8, 8, 8, 9)
                    else ->
                        listOf(1)
                }
            else ->
                listOf(1)
        }
    }
}

class ZiJiangDialect : Dialect() {
    override val supportedCharsets = mapOf(
            Codepage("cp1250") to 72,
            Codepage("cp1252") to 16,
            Codepage("cp852")  to 18,
            Codepage("cp437")  to 0,
            Codepage("cp1251") to 73
    )
}

class XprinterDialect: Dialect() {
    override val supportedCharsets = mapOf(
            Codepage("cp852")  to 18,
            Codepage("cp1252") to 16,
            Codepage("cp437")  to 0,
            Codepage("cp866")  to 17,
            IncompleteCodepage("cp1251", listOf('Ђ')) to 23
    )

    override fun pageFeed(): ByteArray =
            byteArrayOf(0x1b, 0x64, 11)
}

class EpsonTMP20Dialect: Dialect() {
    override val supportedCharsets = mapOf(
            Codepage("cp852")  to 18,
            Codepage("cp1252") to 16,
            Codepage("cp437")  to 0,
            Codepage("cp866")  to 17,
            Codepage("cp1251") to 46
    )

    override fun pageFeed(): ByteArray =
            byteArrayOf(0x1b, 0x64, 11)
}

class GoojprtDialect: Dialect() {
    override val supportedCharsets = mapOf(
            IncompleteCodepage("cp1250", listOf('ý')) to 30,
            Codepage("cp852")  to 18,
            Codepage("cp1252") to 16,
            Codepage("cp437")  to 0,
            Codepage("cp1251") to 6
    )
}

val dialects = mapOf(
        PrinterModel.ZiJiang to ZiJiangDialect::class,
        PrinterModel.Goojprt to GoojprtDialect::class,
        PrinterModel.Xprinter to XprinterDialect::class,
        PrinterModel.Epson to EpsonTMP20Dialect::class,
        PrinterModel.Bixolon to Dialect::class,
        PrinterModel.Generic to Dialect::class
)