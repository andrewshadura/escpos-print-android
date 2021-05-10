package me.shadura.escposprint.detect

import kotlinx.serialization.*
import me.shadura.escposprint.L

import me.shadura.escpos.PrinterModel

enum class OpenDrawerSetting {
    DontOpen,
    OpenBefore,
    OpenAfter
}

@Serializable
data class PrinterRec(var name: String, val address: String, var enabled: Boolean, var model: PrinterModel) : Comparable<PrinterRec> {
    @Transient
    var connecting: Boolean = false

    @Optional
    var lineWidth: Int = 32

    @Optional
    var drawerSetting: OpenDrawerSetting = OpenDrawerSetting.DontOpen

    @Optional
    var extraLines: Int = 0

    @Optional
    var alias: String = ""
        get() = if (field.isBlank()) {
            if (name.isBlank()) {
                "(unnamed)"
            } else name
        } else {
            field
        }

    override fun compareTo(other: PrinterRec): Int {
        return address.compareTo(other.address)
    }

    override fun equals(other: Any?): Boolean {
        return if (other is PrinterRec) {
            address == other.address
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int = address.hashCode()

    fun detectModel() {
        when {
            name.startsWith("Bluetooth Printer") ->
                model = PrinterModel.ZiJiang
            address == "1CBE:0003" ||
                    address == "1CB0:0003" ||
                    address == "0483:0483" ||
                    address == "0416:5011" ||
                    address == "0416:AABB" ||
                    address == "1659:8965" ||
                    address == "0483:5741" ->
                model = PrinterModel.ZiJiang
            name.startsWith("MTP-") || name.startsWith("PT2") ->
                model = PrinterModel.Goojprt
            address == "067B:2305" || address == "0483:5720" ->
                model = PrinterModel.Goojprt
            name.startsWith("PTP-II") -> {
                lineWidth = if (name == "PTP-III") 48 else 32
                model = PrinterModel.Cashino
            }
            name.startsWith("JP302") -> {
                model = PrinterModel.Cashino
                lineWidth = 48
            }
            address.startsWith("DC:0D:3") && name.startsWith("Printer00") ->
                model = PrinterModel.Gzqianji
            address.startsWith("98:D3:3") || name.startsWith("Printer00") ->
                model = PrinterModel.Xprinter
            address == "0483:070B" ->
                model = PrinterModel.Xprinter
            address.startsWith("00:01:90") || name.startsWith("TM-P20_") -> {
                model = PrinterModel.Epson
            }
            address == "04B8:0E1C" ->
                model = PrinterModel.Epson
            address.startsWith("00:19:0") || address.startsWith("74:F0:7") -> {
                L.i("Detected as unsupported Bixolon, falling back to Epson")
                model = PrinterModel.Epson
            }
            name == "InnerPrinter" && address == "00:11:22:33:44:55" ->
                model = PrinterModel.Sunmi
        }
    }
}
