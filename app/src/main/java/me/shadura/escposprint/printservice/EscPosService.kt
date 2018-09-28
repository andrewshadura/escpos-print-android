/**
 * Copyright (C) 2015—2016 Benoit Duffez
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

import android.os.AsyncTask
import android.os.Handler
import android.print.PrintJobId
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.widget.Toast

import java.net.MalformedURLException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URISyntaxException
import java.util.HashMap

import me.shadura.escposprint.L
import me.shadura.escposprint.R
import java.io.*

/**
 * CUPS print service
 */
class EscPosService : PrintService() {

    private val mJobs = HashMap<PrintJobId, Int>()

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

        val url = printerId.localId

        val id = printJob.id
        if (id == null) {
            L.e("Tried to cancel a job, but the print job ID is null")
            return
        }
        val jobId = mJobs[id]
        if (jobId == null) {
            L.e("Tried to cancel a job, but the print job ID is null")
            return
        }

        try {
            object : AsyncTask<Void, Void, Void>() {
                override fun doInBackground(vararg params: Void): Void? {
                    cancelPrintJob(jobId)
                    return null
                }

                override fun onPostExecute(v: Void) {
                    onPrintJobCancelled(printJob)
                }
            }.execute()
        } catch (e: URISyntaxException) {
            L.e("Couldn't parse URI: $url", e)
        }

    }

    /**
     * Called from a background thread, ask the printer to cancel a job by its printer job ID
     *
     * @param clientURL The printer client URL
     * @param jobId     The printer job ID
     */
    internal fun cancelPrintJob(jobId: Int) {
        try {
            /*
            TODO: Real cancellation
            val client = CupsClient(clientURL)
            client.cancelJob(jobId)
            */
        } catch (e: Exception) {
            L.e("Couldn't cancel job: $jobId", e)
        }

    }

    /**
     * Called on the main thread, when the print job was cancelled
     *
     * @param printJob The print job
     */
    internal fun onPrintJobCancelled(printJob: PrintJob) {
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
            val info = printJob.info

            // Send print job
            object : AsyncTask<Void, Void, Void>() {
                var mException: Exception? = null

                override fun doInBackground(vararg params: Void): Void? {
                    try {
                        printDocument(jobId, address, fd)
                    } catch (e: Exception) {
                        mException = e
                    }

                    return null
                }

                override fun onPostExecute(result: Void?) {
                    mException?.let { exception: Exception ->
                        handleJobException(jobId, exception)
                        return
                    }
                    onPrintJobSent(printJob)
                }
            }.execute()
        } catch (e: MalformedURLException) {
            L.e("Couldn't queue print job: $printJob", e)
        } catch (e: URISyntaxException) {
            L.e("Couldn't parse URI: $address", e)
        }

    }

    /**
     * Called from the UI thread.
     * Handle the exception (e.g. log or report it as a bug?), and inform the user of what happened
     *
     * @param jobId The print job
     * @param e     The exception that occurred
     */
    internal fun handleJobException(jobId: PrintJobId, e: Exception) {
        when (e) {
            is SocketTimeoutException ->
                Toast.makeText(this, R.string.err_job_socket_timeout, Toast.LENGTH_LONG).show()                      
            else -> {
                Toast.makeText(this, getString(R.string.err_job_exception, jobId.toString(), e.localizedMessage), Toast.LENGTH_LONG).show()
                L.e("Couldn't query job $jobId", e)
            }
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
        if (!mJobs.containsKey(printJob.id)) {
            L.e("Tried to request a job status, but the job couldn't be found in the jobs list")
            return false
        }

        val printerId = printJob.info.printerId
        if (printerId == null) {
            L.e("Tried to request a job status, but the printer ID is null")
            return false
        }
        val address = printerId.localId

        // Prepare job
        val jobId: Int = mJobs[printJob.id]!!

        // Send print job
        object : AsyncTask<Void, Void, JobStateEnum>() {
            internal var mException: Exception? = null

            override fun doInBackground(vararg params: Void): JobStateEnum? {
                try {
                    return getJobState(jobId)
                } catch (e: Exception) {
                    mException = e
                }

                return null
            }

            override fun onPostExecute(state: JobStateEnum?) {
                mException?.let { exception: Exception ->
                    L.e("Couldn't get job: $jobId state because: $exception")

                    if (exception is SocketException && exception.message!!.contains("ECONNRESET")) {
                        Toast.makeText(this@EscPosService, getString(R.string.err_job_econnreset, jobId), Toast.LENGTH_LONG).show()
                    } else if (exception is FileNotFoundException) {
                        Toast.makeText(this@EscPosService, getString(R.string.err_job_not_found, jobId), Toast.LENGTH_LONG).show()
                    } else {
                        L.e("Couldn't get job: $jobId state", exception)
                    }
                    return
                }
                if (state != null) {
                    onJobStateUpdate(printJob, state)
                }
            }
        }.execute()

        // We want to be called again if the job is still in this map
        // Indeed, when the job is complete, the job is removed from this map.
        return mJobs.containsKey(printJob.id)
    }

    /**
     * Called in a background thread, in order to check the job status
     *
     * @param jobId     The printer job ID
     * @return true if the job is complete/aborted/cancelled, false if it's still processing (printing, paused, etc)
     */
    @Throws(Exception::class)
    internal fun getJobState(jobId: Int): JobStateEnum {
        return JobStateEnum.COMPLETED
    }

    /**
     * Called on the main thread, when a job status has been checked
     *
     * @param printJob The print job
     * @param state    Print job state
     */
    internal fun onJobStateUpdate(printJob: PrintJob, state: JobStateEnum?) {
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
    internal fun printDocument(jobId: PrintJobId, address: String, fd: FileDescriptor) {

        val inputStream = FileInputStream(fd)
        mJobs[jobId] = jobId.hashCode()
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

