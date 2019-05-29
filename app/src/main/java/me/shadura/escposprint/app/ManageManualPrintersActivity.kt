/**
 * Copyright (C) 2017      Benoit Duffez
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
 * program; if not, see </http://www.gnu.org/licenses/>.
 */
package me.shadura.escposprint.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.design.widget.BottomSheetDialog
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.*

import me.shadura.escposprint.R

import kotlinx.coroutines.*
import me.shadura.escposprint.L
import me.shadura.escpos.PrinterModel
import me.shadura.escposprint.detect.PrinterRec
import me.shadura.escposprint.printservice.*
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.design.snackbar
import java.util.*

class ManageManualPrintersActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private var mBluetoothAdapter: BluetoothAdapter? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: ManualPrintersRecyclerAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var printers: MutableList<PrinterRec>
    private lateinit var prefs: SharedPreferences
    private lateinit var config: Config
    private var bottomDialog: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_manual_printers)

        // Build adapter
        prefs = getSharedPreferences(Config.SHARED_PREFS_PRINTERS, Context.MODE_PRIVATE)
        config = Config.read(prefs)
        printers = config.configuredPrinters.values.toMutableList()

        viewManager = LinearLayoutManager(this)
        viewAdapter = ManualPrintersRecyclerAdapter(printers)

        recyclerView = findViewById<RecyclerView>(R.id.manage_printers_recycler).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        viewAdapter.removeCallback = { _, printer: PrinterRec ->
            if (printer.address in config.configuredPrinters) {
                config.configuredPrinters.remove(printer.address)
            }
            savePrinters()
        }
        viewAdapter.changeCallback = { _, printer: PrinterRec ->
            if (printer.address !in config.configuredPrinters) {
                config.configuredPrinters[printer.address] = printer
            }
            savePrinters()
        }

        viewAdapter.clickCallback = { _, printer: PrinterRec ->
            bottomDialog = BottomSheetDialog(this)
            val sheetView = this.layoutInflater.inflate(R.layout.printer_details, null)

            sheetView.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
                viewAdapter.remove(printer)
                bottomDialog?.cancel()
            }
            sheetView.findViewById<Button>(R.id.closeButton).setOnClickListener {
                bottomDialog?.cancel()
            }
            with (sheetView.findViewById<Spinner>(R.id.printerModel)) {
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                    }

                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        L.i("${parent.getItemAtPosition(position)}")
                        printer.model = PrinterModel.valueOf(parent.getItemAtPosition(position).toString())
                        viewAdapter.notifyDataSetChanged()
                        savePrinters()
                    }
                }
                setSelection((adapter as ArrayAdapter<String>).getPosition(printer.model.name))
            }
            bottomDialog?.run {
                setContentView(sheetView)
                show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        bottomDialog?.dismiss()
    }

    fun findPrinters(button: View) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()?.apply {
            if (!isEnabled) {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH)
            } else {
                val serverIntent = Intent(this@ManageManualPrintersActivity, DeviceListActivity::class.java)
                startActivityForResult(serverIntent, REQUEST_FIND_DEVICE)
            }
        } ?: {
            longSnackbar(recyclerView, "Bluetooth is not available")

            val debug = false
            if (debug) {
                val bytes = byteArrayOf(0, 0, 0, 0, 0, 0)
                Random().nextBytes(bytes)
                val mac = bytes.joinToString(separator = ":") { String.format("%02x", it.toInt() and 0xff) }
                val printerInfo = PrinterRec("Random device",
                        "$mac", true, PrinterModel.ZiJiang)
                printers.add(printerInfo)
                addPrinter(printerInfo)
                viewAdapter.notifyDataSetChanged()
            }
            null
        }()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (ContextCompat.checkSelfPermission(this,
                                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            /* TODO: Add an explainer */
                        } else {
                            ActivityCompat.requestPermissions(this,
                                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_COARSE_LOCATION)
                        }
                    } else {
                        val serverIntent = Intent(this@ManageManualPrintersActivity, DeviceListActivity::class.java)
                        startActivityForResult(serverIntent, REQUEST_FIND_DEVICE)
                    }
                } else {
                    longSnackbar(recyclerView, "This app needs Bluetooth to add new printers")
                }
            }
            REQUEST_FIND_DEVICE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val address = data!!.extras!!.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS)!!

                    if (BluetoothAdapter.checkBluetoothAddress(address)) {
                        val device = mBluetoothAdapter!!.getRemoteDevice(address)
                        val printerInfo = PrinterRec(device.name, address, true, PrinterModel.ZiJiang)
                        printerInfo.detectModel()
                        printerInfo.connecting = true
                        printers.add(printerInfo)
                        viewAdapter.notifyDataSetChanged()

                        launch {
                            val bluetoothService = bluetoothServiceActor(device)
                            val response = CompletableDeferred<Result>()
                            bluetoothService.send(Connect(response))
                            val result = response.await()
                            when (result.state) {
                                State.STATE_CONNECTED -> {
                                    snackbar(recyclerView, R.string.printer_connected)
                                    printerInfo.connecting = false
                                    viewAdapter.notifyDataSetChanged()
                                    addPrinter(printerInfo)
                                }
                                State.STATE_FAILED,
                                State.STATE_NONE -> {
                                    longSnackbar(recyclerView, "Failed to connect to the printer: ${result.error}")
                                    printerInfo.connecting = false
                                    printerInfo.enabled = false
                                    viewAdapter.notifyDataSetChanged()
                                }
                            }
                            bluetoothService.close()
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_COARSE_LOCATION -> {
                val serverIntent = Intent(this@ManageManualPrintersActivity, DeviceListActivity::class.java)
                startActivityForResult(serverIntent, REQUEST_FIND_DEVICE)
            }
        }
    }

    private fun savePrinters() {
        config.write(prefs)
    }

    private fun addPrinter(printer: PrinterRec) {
        config.configuredPrinters[printer.address] = printer
        savePrinters()
    }

    private data class ManualPrinterInfoViews(val name: TextView,
                                              val address: TextView,
                                              val model: TextView,
                                              val enabled: Switch,
                                              val connecting: ProgressBar,
                                              val innerLayout: ConstraintLayout)

    private class ManualPrintersRecyclerAdapter(val objects: MutableList<PrinterRec>) :
            UndoableDeleteAdapter<ManualPrintersRecyclerAdapter.ViewHolder>() {

        private val pendingRemovals = mutableMapOf<PrinterRec, Job>()
        var removeCallback: ((position: Int, o: PrinterRec) -> Unit)? = null
        var changeCallback: ((position: Int, o: PrinterRec) -> Unit)? = null
        var clickCallback: ((position: Int, o: PrinterRec) -> Unit)? = null

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val views: ManualPrinterInfoViews = ManualPrinterInfoViews(
                    v.findViewById(R.id.manual_printer_name) as TextView,
                    v.findViewById(R.id.manual_printer_address) as TextView,
                    v.findViewById(R.id.manual_printer_model) as TextView,
                    v.findViewById(R.id.manual_printer_enabled) as Switch,
                    v.findViewById(R.id.manual_printer_progressbar) as ProgressBar,
                    v.findViewById(R.id.inner_layout) as ConstraintLayout)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val listItem = LayoutInflater.from(parent.context).inflate(R.layout.manage_printers_list_item, parent, false)

            return ViewHolder(listItem)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = objects[position]
            item.let { printer ->
                holder.views.name.text = printer.name
                holder.views.address.text = printer.address
                holder.views.model.text = printer.model.name
                holder.views.enabled.isChecked = printer.enabled
                holder.views.enabled.isEnabled = !printer.connecting

                holder.views.enabled.setOnCheckedChangeListener { _, isChecked ->
                    printer.enabled = isChecked
                    L.i("$printer -> $isChecked")
                    changeCallback?.invoke(position, printer)
                }
                holder.views.connecting.visibility = if (printer.connecting) VISIBLE else INVISIBLE
                holder.itemView.setOnClickListener {
                    clickCallback?.invoke(position, printer)
                    false
                }
            }
        }

        override fun getItemCount() = objects.size

        override fun isPendingRemoval(position: Int): Boolean {
            return objects[position] in pendingRemovals
        }

        fun isPendingRemoval(item: PrinterRec): Boolean {
            return item in pendingRemovals
        }

        override fun postRemoveAt(position: Int) {
            postRemove(objects[position])
        }

        override fun postRemove(item: Any) {
            if (!isPendingRemoval(item as PrinterRec)) {
                val undo = GlobalScope.launch {
                    delay(PENDING_REMOVAL_TIMEOUT)
                    remove(item)
                }
                pendingRemovals[item] = undo
                notifyItemChanged(objects.indexOf(item))
                L.i("posted removal task for $item: $undo")
            }
        }

        override fun undoRemove(item: Any) {
            if (isPendingRemoval(item as PrinterRec)) {
                L.i("cancelling removal task for $item")
                pendingRemovals[item]?.cancel()
                val position = objects.indexOf(item)
                pendingRemovals.remove(item)
                notifyItemChanged(position)
            }
        }

        override fun removeAt(position: Int) {
            remove(objects[position])
        }

        override fun remove(item: Any) {
            if (item in objects) {
                if (item in pendingRemovals) {
                    L.i("dropping removal task for $item")
                    pendingRemovals.remove(item)
                }
                L.i("removing $item")
                val position = objects.indexOf(item)
                objects.remove(item)
                if (item is PrinterRec) {
                    removeCallback?.invoke(position, item)
                }
                notifyItemRemoved(position)
            }
        }

        companion object {
            private const val PENDING_REMOVAL_TIMEOUT = 3000L
        }
    }

    companion object {

        /* Intent request codes */
        const val REQUEST_FIND_DEVICE = 1
        const val REQUEST_ENABLE_BLUETOOTH = 2

        const val PERMISSION_REQUEST_COARSE_LOCATION = 2
    }
}
