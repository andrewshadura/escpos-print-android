package me.shadura.escposprint.detect

import kotlinx.serialization.*
import me.shadura.escposprint.L

enum class PrinterModel {
    Generic,
    ZiJiang,
    Epson,
    Bixolon,
    Goojprt,
    Xprinter
}

@Serializable
data class PrinterRec(var name: String, val address: String, var enabled: Boolean, var model: PrinterModel) : Comparable<PrinterRec> {
    @Transient
    var connecting: Boolean = false

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
            address.startsWith("98:D3:3") || name.startsWith("Printer00") ->
                model = PrinterModel.Xprinter
            address.startsWith("00:19:0") || address.startsWith("74:F0:7") -> {
                L.i("Detected as unsupported Bixolon, fallin back to ZiJiang")
                model = PrinterModel.ZiJiang
            }
        }
    }
}
