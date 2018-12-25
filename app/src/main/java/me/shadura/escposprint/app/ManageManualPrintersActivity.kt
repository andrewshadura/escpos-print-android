/**
 * Copyright (C) 2017 Benoit Duffez
 * Copyright (C) 2018 Andrej Shadura
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
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView

import me.shadura.escposprint.R

import kotlinx.coroutines.*
import me.shadura.escposprint.L
import me.shadura.escposprint.printservice.*
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.design.snackbar
import java.util.*

class ManageManualPrintersActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private var mBluetoothAdapter: BluetoothAdapter? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: ManualPrintersRecyclerAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var printers: MutableList<ManualPrinterInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_manual_printers)

        // Build adapter
        val prefs = getSharedPreferences(AddPrintersActivity.SHARED_PREFS_MANUAL_PRINTERS, Context.MODE_PRIVATE)
        var numPrinters = prefs.getInt(AddPrintersActivity.PREF_NUM_PRINTERS, 0)
        if (numPrinters < 0) {
            numPrinters = 0
        }
        printers = getPrinters(prefs, numPrinters)

        viewManager = LinearLayoutManager(this)
        viewAdapter = ManualPrintersRecyclerAdapter(printers)

        recyclerView = findViewById<RecyclerView>(R.id.manage_printers_recycler).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        viewAdapter.removeCallback = {
            savePrinters()
        }
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
                val printerInfo = ManualPrinterInfo("Random device",
                        "$mac", true, true)
                printers.add(printerInfo)
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
                    val address = data!!.extras!!.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS)

                    if (BluetoothAdapter.checkBluetoothAddress(address)) {
                        val device = mBluetoothAdapter!!.getRemoteDevice(address)
                        val printerInfo = ManualPrinterInfo(device.name, address, true, true)
                        printers.add(printerInfo)
                        launch {
                            val bluetoothService = bluetoothServiceActor(device)
                            val response = CompletableDeferred<State>()
                            bluetoothService.send(Connect(response))
                            when (response.await()) {
                                State.STATE_CONNECTED -> {
                                    snackbar(recyclerView, "Printer connected and enabled")
                                    printerInfo.connecting = false
                                    viewAdapter.notifyDataSetChanged()
                                    addPrinter(printerInfo.name, printerInfo.address, printerInfo.enabled)
                                }
                                State.STATE_NONE -> {
                                    snackbar(recyclerView, "Failed to connect to the printer")
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

    private fun getPrinters(prefs: SharedPreferences, numPrinters: Int): MutableList<ManualPrinterInfo> {
        val printers = ArrayList<ManualPrinterInfo>(numPrinters)
        var address: String?
        var name: String?
        var enabled: Boolean
        for (i in 0 until numPrinters) {
            name = prefs.getString(AddPrintersActivity.PREF_NAME + i, null)
            address = prefs.getString(AddPrintersActivity.PREF_ADDRESS + i, null)
            enabled = prefs.getBoolean(AddPrintersActivity.PREF_ENABLED + i, false)
            printers.add(ManualPrinterInfo(name, address, enabled, false))
        }
        return printers
    }

    private fun savePrinters() {
        val prefs = getSharedPreferences(AddPrintersActivity.SHARED_PREFS_MANUAL_PRINTERS, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            printers.forEachIndexed { id, printer ->
                putString(AddPrintersActivity.PREF_ADDRESS + id, printer.address)
                putString(AddPrintersActivity.PREF_NAME + id, printer.name)
                putBoolean(AddPrintersActivity.PREF_ENABLED + id, printer.enabled)
            }
            putInt(AddPrintersActivity.PREF_NUM_PRINTERS, printers.size)
            apply()
        }
    }

    private fun addPrinter(name: String, address: String, enabled: Boolean) {
        val prefs = getSharedPreferences(AddPrintersActivity.SHARED_PREFS_MANUAL_PRINTERS, Context.MODE_PRIVATE)
        val id = prefs.getInt(AddPrintersActivity.PREF_NUM_PRINTERS, 0)
        with (prefs.edit()) {
            putString(AddPrintersActivity.PREF_ADDRESS + id, address)
            putString(AddPrintersActivity.PREF_NAME + id, name)
            putBoolean(AddPrintersActivity.PREF_ENABLED + id, enabled)
            putInt(AddPrintersActivity.PREF_NUM_PRINTERS, id + 1)
            apply()
        }
    }

    private data class ManualPrinterInfo(var name: String, val address: String, var enabled: Boolean, var connecting: Boolean)

    private data class ManualPrinterInfoViews(val name: TextView,
                                              val address: TextView,
                                              val enabled: Switch,
                                              val connecting: ProgressBar,
                                              val innerLayout: ConstraintLayout)

    private class ManualPrintersRecyclerAdapter(val objects: MutableList<ManualPrinterInfo>) :
            UndoableDeleteAdapter<ManualPrintersRecyclerAdapter.ViewHolder>() {

        private val pendingRemovals = mutableMapOf<ManualPrinterInfo, Job>()
        var removeCallback: ((position: Int) -> Unit)? = null

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val views: ManualPrinterInfoViews = ManualPrinterInfoViews(
                    v.findViewById(R.id.manual_printer_name) as TextView,
                    v.findViewById(R.id.manual_printer_address) as TextView,
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
            item.let { (name, address, enabled, connecting) ->
                holder.views.name.text = name
                holder.views.address.text = address
                holder.views.enabled.isChecked = enabled
                holder.views.enabled.isEnabled = !connecting

                holder.views.enabled.setOnCheckedChangeListener { _, isChecked ->
                    item.enabled = isChecked
                    L.i("$item -> $isChecked")
                }
                holder.views.connecting.visibility = if (connecting) VISIBLE else GONE
            }
        }

        override fun getItemCount() = objects.size

        override fun isPendingRemoval(position: Int): Boolean {
            return objects[position] in pendingRemovals
        }

        fun isPendingRemoval(item: ManualPrinterInfo): Boolean {
            return item in pendingRemovals
        }

        override fun postRemoveAt(position: Int) {
            postRemove(objects[position])
        }

        override fun postRemove(item: Any) {
            if (!isPendingRemoval(item as ManualPrinterInfo)) {
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
            if (isPendingRemoval(item as ManualPrinterInfo)) {
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
                removeCallback?.invoke(position)
                objects.remove(item)
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
