/**
 * Copyright (C) 2015—2017 Benoit Duffez
 * Copyright (C) 2018      Andrej Shadura
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details. You should have
 * received a copy of the GNU General Public License along with this
 * program; if not, see <http://www.gnu.org/licenses/>.
 */
package me.shadura.escposprint.printservice

import android.content.Context
import android.os.AsyncTask
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.widget.Toast

import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.ArrayList

import javax.net.ssl.SSLPeerUnverifiedException

import me.shadura.escposprint.EscPosPrintApp
import me.shadura.escposprint.L
import me.shadura.escposprint.R
import me.shadura.escposprint.detect.PrinterRec

/**
 * CUPS printer discovery class
 */
internal class EscPosPrinterDiscoverySession(private val mPrintService: PrintService) : PrinterDiscoverySession() {

    var mResponseCode: Int = 0

    /**
     * Called when the framework wants to find/discover printers
     * Will prompt the user to trust any (the last) host that raises an [SSLPeerUnverifiedException]
     *
     * @param priorityList The list of printers that the user selected sometime in the past, that need to be checked first
     */
    override fun onStartPrinterDiscovery(priorityList: List<PrinterId>) {
        object : AsyncTask<Void, Void, Map<String, PrinterRec>>() {
            override fun doInBackground(vararg params: Void): Map<String, PrinterRec> {
                return scanPrinters()
            }

            override fun onPostExecute(printers: Map<String, PrinterRec>) {
                onPrintersDiscovered(printers)
            }
        }.execute()
    }

    /**
     * Called when mDNS/manual printers are found
     * Called on the UI thread
     *
     * @param printers The list of printers found, as a map of URL=>name
     */
    fun onPrintersDiscovered(printers: Map<String, PrinterRec>) {
        val res = EscPosPrintApp.getInstance().resources
        val toast = res.getQuantityString(R.plurals.printer_discovery_result, printers.size, printers.size)
        Toast.makeText(mPrintService, toast, Toast.LENGTH_SHORT).show()
        L.d("onPrintersDiscovered($printers)")
        val printersInfo = ArrayList<PrinterInfo>(printers.size)
        for ((address, printer) in printers) {
            val printerId = mPrintService.generatePrinterId(address)
            printersInfo.add(PrinterInfo.Builder(printerId, printer.name, PrinterInfo.STATUS_IDLE).build())
        }

        addPrinters(printersInfo)
    }

    /**
     * Ran in the background thread, will check whether a printer is valid
     *
     * @return The printer capabilities if the printer is available, null otherwise
     */
    @Throws(Exception::class)
    fun checkPrinter(address: String?, printerId: PrinterId): PrinterCapabilitiesInfo? {
        address?.let {
            val builder = PrinterCapabilitiesInfo.Builder(printerId)
            builder.addResolution(PrintAttributes.Resolution("default", "203×203 dpi", 203, 203), true)
            builder.addMediaSize(PrintAttributes.MediaSize("58x105mm", "58x105mm",
                    Math.round(58.0f / 25.4f * 1000f), Math.round(105.0f / 25.4f * 1000f)), true)
            builder.addMediaSize(PrintAttributes.MediaSize("58x210mm", "58x210mm",
                    Math.round(58.0f / 25.4f * 1000f), Math.round(210.0f / 25.4f * 1000f)), false)
            builder.addMediaSize(PrintAttributes.MediaSize.ISO_A6, false)
            builder.addMediaSize(PrintAttributes.MediaSize.ISO_A5, false)
            builder.addMediaSize(PrintAttributes.MediaSize.ISO_A4, false)
            builder.setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
            builder.setMinMargins(PrintAttributes.Margins(0, 0, 0, 0))
            return builder.build()
        }

        return null
    }

    /**
     * Called when the printer has been checked over IPP(S)
     * Called from the UI thread
     *
     * @param printerId               The printer
     * @param printerCapabilitiesInfo null if the printer isn't available anymore, otherwise contains the printer capabilities
     */
    fun onPrinterChecked(printerId: PrinterId, printerCapabilitiesInfo: PrinterCapabilitiesInfo?) {
        L.d("onPrinterChecked: $printerId (printers: $printers), cap: $printerCapabilitiesInfo")
        if (printerCapabilitiesInfo == null) {
            val printerIds = ArrayList<PrinterId>()
            printerIds.add(printerId)
            removePrinters(printerIds)
            Toast.makeText(mPrintService, mPrintService.getString(R.string.printer_not_responding, printerId.localId), Toast.LENGTH_LONG).show()
            L.d("onPrinterChecked: Printer has no cap, removing it from the list")
        } else {
            val printers = ArrayList<PrinterInfo>()
            for (printer in getPrinters()) {
                if (printer.id == printerId) {
                    val printerWithCaps = PrinterInfo.Builder(printerId, printer.name, PrinterInfo.STATUS_IDLE)
                            .setCapabilities(printerCapabilitiesInfo)
                            .build()
                    L.d("onPrinterChecked: adding printer: $printerWithCaps")
                    printers.add(printerWithCaps)
                } else {
                    printers.add(printer)
                }
            }
            L.d("onPrinterChecked: we had " + getPrinters().size + " printers, we now have " + printers.size)
            addPrinters(printers)
        }
    }

    /**
     * Ran in background thread.
     *
     * @return The list of printers
     */
    fun scanPrinters(): Map<String, PrinterRec> {


        /* TODO: Here, we can detect more printers */

        // Add the printers manually added
        val prefs = mPrintService.getSharedPreferences(Config.SHARED_PREFS_PRINTERS, Context.MODE_PRIVATE)
        return Config.read(prefs).configuredPrinters.filterValues(PrinterRec::enabled)
    }

    override fun onStopPrinterDiscovery() {
        //TODO
    }

    override fun onValidatePrinters(printerIds: List<PrinterId>) {
        //TODO?
    }

    /**
     * Called when the framework wants additional information about a printer: is it available? what are its capabilities? etc
     *
     * @param printerId The printer to check
     */
    override fun onStartPrinterStateTracking(printerId: PrinterId) {
        L.d("onStartPrinterStateTracking: $printerId")
        object : AsyncTask<Void, Void, PrinterCapabilitiesInfo>() {
            internal var mException: Exception? = null

            override fun doInBackground(vararg voids: Void): PrinterCapabilitiesInfo? {
                try {
                    L.i("Checking printer status: $printerId")
                    return checkPrinter(printerId.localId, printerId)
                } catch (e: Exception) {
                    mException = e
                }

                return null
            }

            override fun onPostExecute(printerCapabilitiesInfo: PrinterCapabilitiesInfo?) {
                L.v("HTTP response code: $mResponseCode")
                mException?.let { exception: Exception ->
                    if (handlePrinterException(exception, printerId)) {
                        L.e("Couldn't start printer state tracking", exception)
                    }
                    return
                }
                printerCapabilitiesInfo?.let {
                    onPrinterChecked(printerId, it)
                }
            }
        }.execute()
    }

    /**
     * Run on the UI thread. Present the user some information about the error that happened during the printer check
     *
     * @param exception The exception that occurred
     * @param printerId The printer on which the exception occurred
     * @return true if the exception should be reported for a potential bug, false otherwise
     */
    fun handlePrinterException(exception: Exception, printerId: PrinterId): Boolean {
        // Happens when the HTTP response code is in the 4xx range
        when {
            exception is FileNotFoundException ->
                return handleHttpError(exception, printerId)
            exception is SocketTimeoutException -> {
                Toast.makeText(mPrintService, R.string.err_printer_socket_timeout, Toast.LENGTH_LONG).show()
            }
            exception is UnknownHostException -> {
                Toast.makeText(mPrintService, R.string.err_printer_unknown_host, Toast.LENGTH_LONG).show()
            }
            exception is ConnectException && exception.getLocalizedMessage().contains("ENETUNREACH") -> {
                Toast.makeText(mPrintService, R.string.err_printer_network_unreachable, Toast.LENGTH_LONG).show()
            }
            else -> {
                return handleHttpError(exception, printerId)
            }
        }
        return false
    }

    /**
     * Run on the UI thread. Handle all errors related to HTTP errors (usually in the 4xx range)
     *
     * @param exception The exception that occurred
     * @param printerId The printer on which the exception occurred
     * @return true if the exception should be reported for a potential bug, false otherwise
     */
    private fun handleHttpError(exception: Exception, printerId: PrinterId): Boolean {
        Toast.makeText(mPrintService, exception.localizedMessage, Toast.LENGTH_LONG).show()
        return true
    }

    override fun onStopPrinterStateTracking(printerId: PrinterId) {
        // TODO?
    }

    override fun onDestroy() {}

    companion object {
        const val MM_IN_MILS = 39.3700787
    }
}
