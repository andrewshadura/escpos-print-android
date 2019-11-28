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
import android.print.PrintJobInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.widget.Toast

import java.net.MalformedURLException
import java.net.URISyntaxException

import me.shadura.escposprint.L
import me.shadura.escposprint.R
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import kotlinx.coroutines.*
import me.shadura.escpos.dialects
import me.shadura.escposprint.detect.OpenDrawerSetting
import org.jetbrains.anko.toast
import java.io.*
import java.util.concurrent.ConcurrentHashMap

/**
 * CUPS print service
 */
class EscPosService : PrintService(), CoroutineScope by MainScope() {

    data class PrintJobTask(var state: JobStateEnum = JobStateEnum.QUEUED, var task: Job?)

    private val jobs = ConcurrentHashMap<PrintJobId, PrintJobTask>()

    private lateinit var prefs: SharedPreferences

    override fun onConnected() {
        PDFBoxResourceLoader.init(applicationContext)
        prefs = getSharedPreferences(Config.SHARED_PREFS_PRINTERS, Context.MODE_PRIVATE)
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession? {
        return EscPosPrinterDiscoverySession(this)
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        jobs[printJob.id]?.apply {
            state = JobStateEnum.CANCELED
            task?.cancel()
        }

        onPrintJobCancelled(printJob)
    }

    /**
     * Called on the main thread, when the print job was cancelled
     *
     * @param printJob The print job
     */
    private fun onPrintJobCancelled(printJob: PrintJob) {
        jobs.remove(printJob.id)
        printJob.cancel()
        startNextJob()
    }

    private fun startNextJob() {
        synchronized(jobs) {
            if (jobs.filterValues { it.state == JobStateEnum.STARTED }.isEmpty()) {
                jobs.values.firstOrNull {
                    it.state == JobStateEnum.QUEUED
                }?.also { job ->
                    job.state = JobStateEnum.STARTED
                    job.task?.start()
                }
            }
        }
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        val jobId = printJob.id

        if (jobs.containsKey(jobId)) {
            L.e("Job $jobId already queued")
            return
        }
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

            // Send print job
            jobs.getOrPut(jobId) {
                PrintJobTask(task = launch(start = CoroutineStart.LAZY) {
                    try {
                        parseDocument(jobId, address, fd, jobInfo)
                        onPrintJobSent(printJob)
                    } catch (e: Exception) {
                        toast(getString(R.string.err_job_exception, jobId.toString(), e.localizedMessage))
                        L.e("Couldn't query job $jobId", e)
                    }
                })
            }
            startNextJob()

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
                startNextJob()
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
        if (!jobs.containsKey(printJob.id)) {
            L.w("Tried to request a job status, but the job couldn't be found in the jobs list")
            return false
        }

        // Prepare job
        jobs[printJob.id]?.also { job ->
            onJobStateUpdate(printJob, job.state)
        }

        // We don’t want to be called again if the job has been removed from the map.
        return jobs.containsKey(printJob.id)
    }

    /**
     * Called on the main thread, when a job status has been checked
     *
     * @param printJob The print job
     * @param state    Print job state
     */
    private fun onJobStateUpdate(printJob: PrintJob, state: JobStateEnum) {
        // Couldn't check state -- don't do anything
        when (state) {
            is JobStateEnum.CANCELED -> {
                jobs.remove(printJob.id)
                printJob.cancel()
            }
            is JobStateEnum.COMPLETED -> {
                jobs.remove(printJob.id)
                printJob.complete()
            }
            is JobStateEnum.FAILED -> {
                jobs.remove(printJob.id)
                printJob.fail(state.errorMessage)
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
    internal fun parseDocument(jobId: PrintJobId, address: String, fd: FileDescriptor, info: PrintJobInfo) {
        val config = Config.read(prefs)
        if (address !in config.configuredPrinters) {
            L.e("received a job for unconfigured printer $address, bailing out")
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
        val copies = if (info.copies > 1) {
            info.copies
        } else 1

        launch {
            when {
                address.isBluetoothAddress -> {
                    BluetoothAdapter.getDefaultAdapter()?.apply {
                        val retries = 1..5
                        for (retry in retries) {
                            if (!isEnabled) {
                                L.i("[$retry/${retries.last}] Bluetooth is not up yet, trying to enable...")
                                enable()
                                delay(1000)
                            }
                        }
                    }
                }
                address.isUsbAddress -> {
                    while (!hasUsbPermission(address)) {
                        delay(1000)
                    }
                }
            }

            try {
                val bluetoothService = commServiceActor(this@EscPosService, address)
                val response = CompletableDeferred<State>()
                bluetoothService.send(Connect(response))
                when (val result = response.await()) {
                    is State.Connected -> {
                        L.i("sending text")
                        for (copy in 0 until copies) {
                            if (copy > 0) {
                                delay(1000)
                            }
                            bluetoothService.send(Write(dialect.initialise()))
                            bluetoothService.send(Write(dialect.disableKanji()))

                            if (printerConfig.drawerSetting == OpenDrawerSetting.OpenBefore) {
                                bluetoothService.send(Write(dialect.openDrawer()))
                            }

                            bytes.forEach {
                                bluetoothService.send(Write(it))
                                bluetoothService.send(Write("\n".toByteArray()))
                            }

                            if (printerConfig.extraLines > 0) {
                                bluetoothService.send(Write(ByteArray(printerConfig.extraLines) {
                                    0xa
                                }))
                            }

                            /* perform partial cut */
                            bluetoothService.send(Write(byteArrayOf(0x1d, 0x56, 1)))

                            if (printerConfig.drawerSetting == OpenDrawerSetting.OpenAfter) {
                                bluetoothService.send(Write(dialect.openDrawer()))
                            }
                        }

                        val finalResponse = CompletableDeferred<State>()
                        bluetoothService.send(Disconnect(finalResponse))
                        when (val disconnectResult = finalResponse.await()) {
                            is State.Failed -> {
                                jobs[jobId]?.state = JobStateEnum.FAILED(disconnectResult.error)
                                L.e(disconnectResult.error)
                            }
                            is State.Disconnected -> {
                                jobs[jobId]?.state = JobStateEnum.COMPLETED
                                L.i("marked job as complete")
                            }
                        }
                        bluetoothService.close()
                    }
                    is State.Failed -> {
                        jobs[jobId]?.state = JobStateEnum.FAILED(result.error)
                        L.e(result.error)
                    }
                }
            } catch (e: IOException) {
                val error = e.localizedMessage ?: e.message ?: "Unknown error"
                jobs[jobId]?.state = JobStateEnum.FAILED(error)
                L.e(error)
            }
        }
    }

    /**
     * Called on the main thread, when the job was sent to the printer
     *
     * @param printJob The print job
     */
    private fun onPrintJobSent(printJob: PrintJob) {
        printJob.start()
    }

    companion object {
        /**
         * When a print job is active, the app will poll the printer to retrieve the job status. This is the polling interval.
         */
        const val JOB_CHECK_POLLING_INTERVAL = 1000
    }
}

