package me.shadura.escposprint.detect

import kotlinx.serialization.*

enum class PrinterModel {
    Generic,
    ZiJiang,
    Epson,
    Bixolon,
    Goojprt
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
}
