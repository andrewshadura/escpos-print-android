package me.shadura.escposprint.detect

data class PrinterRec(val nickname: String, val protocol: String, val host: String, val port: Int, val queue: String) : Comparable<PrinterRec> {

    override fun toString(): String {
        return "$nickname ($protocol on $host)"
    }

    override fun compareTo(another: PrinterRec): Int {
        return toString().compareTo(another.toString())
    }
}
