package me.shadura.escpos

enum class PrinterModel {
    Generic,
    ZiJiang,
    Epson,
    Bixolon,
    Goojprt,
    Xprinter,
    Cashino,
    Sunmi,
    Gzqianji
}

enum class LineWidth(val characters: Int) {
    NarrowPaper(32),
    MediumPaper(42),
    WidePaper(48)
}

private val ESC: Byte = 0x1b
private val FS: Byte = 0x1c
private val GS: Byte = 0x1d
private val LF: Byte = 0x0a

open class Dialect {
    open var lineWidth: Int = 32

    fun setLineWidth(value: LineWidth) {
        lineWidth = value.characters
    }

    val paperWidth: Int
        get() = when (lineWidth) {
            32 ->
                58
            42 ->
                80
            48 ->
                80
            else ->
                58
        }
    open val pixelWidth: Int
        get() = when (lineWidth) {
            32 -> 384
            42 -> 512
            48 -> 576
            else -> 384
        }
    open val supportedCharsets = mapOf(
            Codepage("cp1252") to 16,
            Codepage("cp852") to 18,
            Codepage("cp437") to 0
    )

    fun initialise(): ByteArray = byteArrayOf(ESC, 0x40)

    fun disableKanji(): ByteArray = byteArrayOf(FS, 0x2e)

    open fun bitImageAdvance(): ByteArray = byteArrayOf(LF)

    fun boldFont(bytes: ByteArray, bold: Boolean): ByteArray {
        return if (bold) {
            byteArrayOf(ESC, 0x45, 1) + bytes + byteArrayOf(ESC, 0x45, 0)
        } else bytes
    }

    open fun largeFont(bytes: ByteArray, large: Boolean): ByteArray {
        return if (large) {
            byteArrayOf(GS, 0x21, 1) + bytes + byteArrayOf(GS, 0x21, 0)
        } else bytes
    }

    fun smallFont(bytes: ByteArray, small: Boolean): ByteArray {
        return if (small) {
            byteArrayOf(ESC, 0x4d, 1) + bytes + byteArrayOf(ESC, 0x4d, 0)
        } else bytes
    }

    fun centre(enable: Boolean): ByteArray {
        return byteArrayOf(ESC, 0x61, if (enable) 1 else 0)
    }

    open fun pageStart(): ByteArray =
        byteArrayOf(LF, LF)

    open fun pageFeed(): ByteArray =
        byteArrayOf(LF, LF, LF)

    fun pageFeed(lines: Int): ByteArray = if (lines > 1) {
        byteArrayOf(ESC, 0x64, lines.toByte())
    } else {
        byteArrayOf(LF)
    }

    open fun openDrawer(): ByteArray =
            byteArrayOf(ESC, 0x70, 0, 0x40, 0x50)

    fun setDefaultLineSpacing(): ByteArray =
            byteArrayOf(ESC, 0x32)

    fun setLineSpacing(px: Int): ByteArray =
            byteArrayOf(ESC, 0x33, px.toByte())

    fun selectBitImageMode(width: Int): ByteArray =
            byteArrayOf(ESC, 0x2a, 33,
                    (0xff and width).toByte(),
                    (0xff00 and width shr 8).toByte())

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
            48 ->
                when (num) {
                    1 ->
                        listOf(48)
                    2 ->
                        listOf(24, 24)
                    3 ->
                        listOf(16, 16, 16)
                    4 ->
                        listOf(12, 12, 12, 12)
                    5 ->
                        listOf(10, 9, 10, 9, 10)
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
            pageFeed(11)
}

class GzqianjiDialect: Dialect() {
    override val supportedCharsets = mapOf(
            Codepage("cp852")  to 13,
            Codepage("cp1252") to 11,
            Codepage("cp437")  to 0,
            Codepage("cp866")  to 12,
            IncompleteCodepage("cp1251", listOf('Ђ')) to 18
    )

    override fun pageFeed(): ByteArray =
            pageFeed(11)
}

class EpsonTMP20Dialect: Dialect() {
    override var lineWidth: Int = 32

    override val supportedCharsets = mapOf(
            Codepage("cp852")  to 18,
            Codepage("cp1252") to 16,
            Codepage("cp437")  to 0,
            Codepage("cp866")  to 17,
            Codepage("cp1251") to 46
    )

    override fun pageStart(): ByteArray {
        return super.pageStart() + byteArrayOf(ESC, 0x4d, 0)
    }

    override fun pageFeed(): ByteArray =
            pageFeed(2)
}

open class GoojprtDialect: Dialect() {
    override val supportedCharsets = mapOf(
            IncompleteCodepage("cp1250", listOf('ý')) to 30,
            Codepage("cp852")  to 18,
            Codepage("cp1252") to 16,
            Codepage("cp437")  to 0,
            Codepage("cp1251") to 6
    )
}

class CashinoDialect: GoojprtDialect() {
    override fun pageFeed(): ByteArray {
        return super.pageFeed() + pageFeed(2)
    }

    /*
    // This doesn’t work properly atm since the line remains 48 chars long
    // but the characters are double width
    override fun largeFont(bytes: ByteArray, large: Boolean): ByteArray {
        return if (large) {
            byteArrayOf(ESC, 0x21, 0x28) + bytes + byteArrayOf(ESC, 0x21, 0)
        } else bytes
    }
    */
}

class SunmiDialect: Dialect() {
    override var lineWidth: Int = 32

    override val supportedCharsets = mapOf(
            Codepage("cp852")  to 18,
            Codepage("cp1252") to 16,
            Codepage("cp437")  to 0,
            Codepage("cp866")  to 17
    )

    override fun pageStart(): ByteArray {
        return super.pageStart() + byteArrayOf(ESC, 0x4d, 0)
    }

    override fun pageFeed(): ByteArray =
            pageFeed(3)

    override fun bitImageAdvance(): ByteArray =
            byteArrayOf()
}

val dialects = mapOf(
        PrinterModel.ZiJiang to ZiJiangDialect::class,
        PrinterModel.Goojprt to GoojprtDialect::class,
        PrinterModel.Cashino to CashinoDialect::class,
        PrinterModel.Xprinter to XprinterDialect::class,
        PrinterModel.Gzqianji to GzqianjiDialect::class,
        PrinterModel.Epson to EpsonTMP20Dialect::class,
        PrinterModel.Sunmi to SunmiDialect::class,
        PrinterModel.Bixolon to Dialect::class,
        PrinterModel.Generic to Dialect::class
)