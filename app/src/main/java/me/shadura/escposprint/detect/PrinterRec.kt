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
            name.startsWith("MTP-") || name.startsWith("PT2") ->
                model = PrinterModel.Goojprt
            name.startsWith("PTP-II") -> {
                lineWidth = if (name == "PTP-III") 48 else 32
                model = PrinterModel.Cashino
            }
            name.startsWith("JP302") -> {
                model = PrinterModel.Cashino
                lineWidth = 48
            }
            address.startsWith("98:D3:3") || name.startsWith("Printer00") ->
                model = PrinterModel.Xprinter
            address.startsWith("00:01:90") || name.startsWith("TM-P20_") -> {
                model = PrinterModel.Epson
            }
            address.startsWith("00:19:0") || address.startsWith("74:F0:7") -> {
                L.i("Detected as unsupported Bixolon, fallin back to ZiJiang")
                model = PrinterModel.ZiJiang
            }
        }
    }
}
