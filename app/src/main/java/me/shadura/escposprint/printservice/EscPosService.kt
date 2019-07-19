/**
 * Copyright (C) 2015—2016 Benoit Duffez
 * Copyright (C) 2018—2019 Andrej Shadura
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

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.print.PrintJobId
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.widget.Toast

import java.net.MalformedURLException
import java.net.URISyntaxException
import java.util.HashMap

import me.shadura.escposprint.L
import me.shadura.escposprint.R
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import kotlinx.coroutines.*
import me.shadura.escpos.Dialect
import me.shadura.escpos.PrinterModel
import me.shadura.escpos.dialects
import me.shadura.escposprint.detect.OpenDrawerSetting
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.io.*
import java.util.concurrent.Future

/**
 * CUPS print service
 */
class EscPosService : PrintService(), CoroutineScope by MainScope() {

    data class PrintJobTask(var state: JobStateEnum = JobStateEnum.PROCESSING, var task: Future<Unit>?)

    private val mJobs = HashMap<PrintJobId, PrintJobTask>()

    private lateinit var prefs: SharedPreferences

    override fun onConnected() {
        PDFBoxResourceLoader.init(applicationContext)
        prefs = getSharedPreferences(Config.SHARED_PREFS_PRINTERS, Context.MODE_PRIVATE)
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession? {
        return EscPosPrinterDiscoverySession(this)
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        val jobInfo = printJob.info
        val printerId = jobInfo.printerId
        if (printerId == null) {
            L.e("Tried to cancel a job, but the printer ID is null")
            return
        }

        val id = printJob.id
        if (id == null) {
            L.e("Tried to cancel a job, but the print job ID is null")
            return
        }
        val job = mJobs[id]!!
        if (job.task == null) {
            L.e("Tried to cancel a job, but the print job is null")
            return
        }
        job.state = JobStateEnum.CANCELED
        job.task?.cancel(true)

        onPrintJobCancelled(printJob)

    }

    /**
     * Called on the main thread, when the print job was cancelled
     *
     * @param printJob The print job
     */
    private fun onPrintJobCancelled(printJob: PrintJob) {
        mJobs.remove(printJob.id)
        printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        startPolling(printJob)
        val jobInfo = printJob.info
        val printerId = jobInfo.printerId
        if (printerId == null) {
            L.e("Tried to queue a job, but the printer ID is null")
            return
        }

        val address = printerId.localId
        try {
            val data = printJob.document.data
            if (data == null) {
                L.e("Tried to queue a job, but the document data (file descriptor) is null")
                Toast.makeText(this, R.string.err_document_fd_null, Toast.LENGTH_LONG).show()
                return
            }
            val fd = data.fileDescriptor
            val jobId = printJob.id

            // Send print job
            val task = doAsync {
                try {
                    parseDocument(jobId, address, fd)
                    uiThread {
                        onPrintJobSent(printJob)
                    }
                } catch (e: Exception) {
                    uiThread {
                        toast(getString(R.string.err_job_exception, jobId.toString(), e.localizedMessage))
                        L.e("Couldn't query job $jobId", e)
                    }
                }
            }
            mJobs[jobId] = PrintJobTask(task = task)
        } catch (e: MalformedURLException) {
            L.e("Couldn't queue print job: $printJob", e)
        } catch (e: URISyntaxException) {
            L.e("Couldn't parse URI: $address", e)
        }

    }

    private fun startPolling(printJob: PrintJob) {
        Handler().postDelayed(object : Runnable {
            override fun run() {
                if (updateJobStatus(printJob)) {
                    Handler().postDelayed(this, JOB_CHECK_POLLING_INTERVAL.toLong())
                }
            }
        }, JOB_CHECK_POLLING_INTERVAL.toLong())
    }

    /**
     * Called in the main thread, will ask the job status and update it in the Android framework
     *
     * @param printJob The print job
     * @return true if this method should be called again, false otherwise (in case the job is still pending or it is complete)
     */
    internal fun updateJobStatus(printJob: PrintJob): Boolean {
        // Check if the job is already gone
        if (printJob.id !in mJobs) {
            L.e("Tried to request a job status, but the job couldn't be found in the jobs list")
            return false
        }

        val printerId = printJob.info.printerId
        if (printerId == null) {
            L.e("Tried to request a job status, but the printer ID is null")
            return false
        }

        // Prepare job
        if (printJob.id in mJobs) {
            val job = mJobs[printJob.id]

            val state = job!!.state
            onJobStateUpdate(printJob, state)
        }


        // We don’t want to be called again if the job has been removed from the map.
        return printJob.id in mJobs
    }

    /**
     * Called in a background thread, in order to check the job status
     *
     * @param jobId     The printer job ID
     * @return true if the job is complete/aborted/cancelled, false if it's still processing (printing, paused, etc)
     */
    internal fun getJobState(job: PrintJobTask): JobStateEnum {
        return job.state
    }

    /**
     * Called on the main thread, when a job status has been checked
     *
     * @param printJob The print job
     * @param state    Print job state
     */
    private fun onJobStateUpdate(printJob: PrintJob, state: JobStateEnum?) {
        // Couldn't check state -- don't do anything
        if (state == null) {
            mJobs.remove(printJob.id)
            printJob.cancel()
        } else {
            if (state == JobStateEnum.CANCELED) {
                mJobs.remove(printJob.id)
                printJob.cancel()
            } else if (state == JobStateEnum.COMPLETED || state == JobStateEnum.ABORTED) {
                mJobs.remove(printJob.id)
                printJob.complete()
            }
        }
    }

    /**
     * Called from a background thread, when the print job has to be sent to the printer.
     *
     * @param jobId      The job id
     * @param address    The printer address
     * @param fd         The document to print, as a [FileDescriptor]
     */
    @Throws(Exception::class)
    internal fun parseDocument(jobId: PrintJobId, address: String, fd: FileDescriptor) {
        val config = Config.read(prefs)
        if (address !in config.configuredPrinters) {
            L.e("received a job for unconfigured printer ${address}, bailing out")
            return
        }
        val printerConfig = config.configuredPrinters[address]!!

        val inputStream = FileInputStream(fd)
        val document = PDDocument.load(inputStream)
        val pdfStripper = PDFStyledTextStripper()
        pdfStripper.addMoreFormatting = true
        val dialect = dialects.getValue(printerConfig.model).java.newInstance()
        dialect.lineWidth = printerConfig.lineWidth
        pdfStripper.dialect = dialect
        val bytes = try {
            pdfStripper.getByteArrays(document)
        } catch (e: Exception) {
            val writer = StringWriter()
            e.printStackTrace(PrintWriter(writer))
            L.e(writer.toString())
            listOf(byteArrayOf())
        }
        document.close()

        launch {
            BluetoothAdapter.getDefaultAdapter()?.apply {
                if (!isEnabled) {
                    enable()
                }
            }

            val bluetoothService = bluetoothServiceActor(address)
            val response = CompletableDeferred<Result>()
            bluetoothService.send(Connect(response))
            var result = response.await()
            when (result.state) {
                State.STATE_CONNECTED -> {
                    L.i("sending text")
                    bluetoothService.send(Write(byteArrayOf(0x1b, 0x40)))
                    bluetoothService.send(Write(byteArrayOf(0x1c, 0x2e)))

                    if (printerConfig.drawerSetting == OpenDrawerSetting.OpenBefore) {
                        bluetoothService.send(Write(dialect.openDrawer()))
                    }

                    bytes.forEach {
                        bluetoothService.send(Write(it))
                        bluetoothService.send(Write("\n".toByteArray()))
                    }
                    /* perform partial cut */
                    bluetoothService.send(Write(byteArrayOf(0x1d, 0x56, 1)))

                    if (printerConfig.drawerSetting == OpenDrawerSetting.OpenAfter) {
                        bluetoothService.send(Write(dialect.openDrawer()))
                    }

                    bluetoothService.close()
                    L.i("sent text")

                    mJobs[jobId]?.state = JobStateEnum.COMPLETED
                    L.i("marked job as complete")
                }
                State.STATE_FAILED -> {
                    L.e(result.error)
                }
            }
        }
    }

    /**
     * Called on the main thread, when the job was sent to the printer
     *
     * @param printJob The print job
     */
    internal fun onPrintJobSent(printJob: PrintJob) {
        printJob.start()
    }

    companion object {
        /**
         * When a print job is active, the app will poll the printer to retrieve the job status. This is the polling interval.
         */
        const val JOB_CHECK_POLLING_INTERVAL = 5000
    }
}

