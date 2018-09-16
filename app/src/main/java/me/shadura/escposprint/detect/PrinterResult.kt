package me.shadura.escposprint.detect

import java.util.ArrayList
import java.util.Collections

class PrinterResult internal constructor() {
    var printers: List<PrinterRec> = Collections.synchronizedList(ArrayList())

    val errors: List<String> = Collections.synchronizedList(ArrayList())

    fun setPrinterRecs(printerRecs: ArrayList<PrinterRec>) {
        this.printers = printerRecs
    }
}
