package me.shadura.escposprint.detect

import java.util.ArrayList

import me.shadura.escposprint.L

internal class Merger {
    fun merge(httpRecs: List<PrinterRec>, httpsRecs: MutableList<PrinterRec>) {
        val tmpRecs = ArrayList<PrinterRec>()
        for (httpRec in httpRecs) {
            var match = false
            for ((_, _, host, port, queue) in httpsRecs) {
                try {
                    if (httpRec.queue == queue &&
                            httpRec.host == host &&
                            httpRec.port == port) {
                        match = true
                        break
                    }
                } catch (e: Exception) {
                    L.e("Invalid record in merge", e)
                }

            }
            if (!match) {
                tmpRecs.add(httpRec)
            }
        }
        for (rec in tmpRecs) {
            httpsRecs.add(rec)
        }
        httpsRecs.sort()
    }
}
