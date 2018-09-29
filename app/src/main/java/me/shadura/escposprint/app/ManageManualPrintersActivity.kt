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
import android.support.annotation.LayoutRes
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView

import java.util.ArrayList

import me.shadura.escposprint.R

import android.view.View.GONE
import android.view.View.VISIBLE
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import me.shadura.escposprint.L
import me.shadura.escposprint.printservice.*
import org.jetbrains.anko.design.snackbar

class ManageManualPrintersActivity : AppCompatActivity() {
    private var mBluetoothAdapter: BluetoothAdapter? = null

    private var mService: BluetoothService? = null
    private var adapter: ManualPrintersAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_manual_printers)

        val printersList = findViewById<ListView>(R.id.manage_printers_list)

        // Build adapter
        val prefs = getSharedPreferences(AddPrintersActivity.SHARED_PREFS_MANUAL_PRINTERS, Context.MODE_PRIVATE)
        var numPrinters = prefs.getInt(AddPrintersActivity.PREF_NUM_PRINTERS, 0)
        if (numPrinters < 0) {
            numPrinters = 0
        }
        val printers = getPrinters(prefs, numPrinters)
        adapter = ManualPrintersAdapter(this, R.layout.manage_printers_list_item, printers)

        // Setup adapter with click to remove
        printersList.adapter = adapter
        printersList.onItemLongClickListener = AdapterView.OnItemLongClickListener { parent, view, position, id ->
            val editor = prefs.edit()
            val numPrinters = prefs.getInt(AddPrintersActivity.PREF_NUM_PRINTERS, 0)
            editor.putInt(AddPrintersActivity.PREF_NUM_PRINTERS, numPrinters - 1)
            editor.remove(AddPrintersActivity.PREF_NAME + position)
            editor.remove(AddPrintersActivity.PREF_ADDRESS + position)
            editor.apply()
            adapter!!.removeItem(position)
            true
        }
    }

    fun findPrinters(button: View) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (mBluetoothAdapter == null) {
            val printersList = findViewById<ListView>(R.id.manage_printers_list)

            Snackbar.make(printersList, "Bluetooth is not available", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            return
        }

        if (!mBluetoothAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH)
        } else {
            val serverIntent = Intent(this@ManageManualPrintersActivity, DeviceListActivity::class.java)
            startActivityForResult(serverIntent, REQUEST_FIND_DEVICE)
        }
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
                    val printersList = findViewById<ListView>(R.id.manage_printers_list)

                    Snackbar.make(printersList, "This app needs Bluetooth to add new printers", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show()
                }
            }
            REQUEST_FIND_DEVICE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val address = data!!.extras!!.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS)

                    if (BluetoothAdapter.checkBluetoothAddress(address)) {
                        val device = mBluetoothAdapter!!.getRemoteDevice(address)
                        var printerInfo = ManualPrinterInfo(device.name, address, true, true)
                        adapter!!.add(printerInfo)
                        launch {
                            val bluetoothService = bluetoothServiceActor(device)
                            val response = CompletableDeferred<State>()
                            bluetoothService.send(Connect(response))
                            when (response.await()) {
                                State.STATE_CONNECTED -> {
                                    withContext(UI) {
                                        snackbar(findViewById<ListView>(R.id.manage_printers_list), "Printer connected and enabled")
                                        printerInfo.connecting = false
                                        adapter!!.notifyDataSetChanged()
                                        addPrinter(printerInfo.name, printerInfo.address, printerInfo.enabled)
                                    }
                                }
                                State.STATE_NONE -> {
                                    withContext(UI) {
                                        snackbar(findViewById<ListView>(R.id.manage_printers_list), "Failed to connect to the printer")
                                        printerInfo.connecting = false
                                        printerInfo.enabled = false
                                        adapter!!.notifyDataSetChanged()
                                    }
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

    private fun getPrinters(prefs: SharedPreferences, numPrinters: Int): List<ManualPrinterInfo> {
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

    private fun addPrinter(name: String, address: String, enabled: Boolean) {
        val prefs = getSharedPreferences(AddPrintersActivity.SHARED_PREFS_MANUAL_PRINTERS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val id = prefs.getInt(AddPrintersActivity.PREF_NUM_PRINTERS, 0)
        editor.putString(AddPrintersActivity.PREF_ADDRESS + id, address)
        editor.putString(AddPrintersActivity.PREF_NAME + id, name)
        editor.putBoolean(AddPrintersActivity.PREF_ENABLED + id, enabled)
        editor.putInt(AddPrintersActivity.PREF_NUM_PRINTERS, id + 1)
        editor.apply()
    }

    private data class ManualPrinterInfo(var name: String, val address: String, var enabled: Boolean, var connecting: Boolean)

    private data class ManualPrinterInfoViews(val name: TextView, val address: TextView, val enabled: Switch, val connecting: ProgressBar)

    private class ManualPrintersAdapter internal constructor(context: Context, @LayoutRes resource: Int, objects: List<ManualPrinterInfo>) : ArrayAdapter<ManualPrinterInfo>(context, resource, objects) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            val views: ManualPrinterInfoViews
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.context).inflate(R.layout.manage_printers_list_item, parent, false)!!
                views = ManualPrinterInfoViews(
                        convertView.findViewById<View>(R.id.manual_printer_name) as TextView,
                        convertView.findViewById<View>(R.id.manual_printer_address) as TextView,
                        convertView.findViewById<View>(R.id.manual_printer_enabled) as Switch,
                        convertView.findViewById<View>(R.id.manual_printer_progressbar) as ProgressBar
                )
                convertView.tag = views
            } else {
                views = convertView.tag as ManualPrinterInfoViews
            }

            val info = getItem(position)!!
            views.name.text = info.name
            views.address.text = info.address
            views.enabled.isChecked = info.enabled
            views.enabled.isEnabled = !info.connecting
            views.enabled.setOnCheckedChangeListener { _, isChecked ->
                info.enabled = isChecked
                L.i("item $info -> $isChecked")
            }
            views.connecting.visibility = if (info.connecting) VISIBLE else GONE

            return convertView
        }

        fun removeItem(position: Int) {
            remove(getItem(position))
        }
    }

    companion object {

        /* Intent request codes */
        const val REQUEST_FIND_DEVICE = 1
        const val REQUEST_ENABLE_BLUETOOTH = 2

        const val PERMISSION_REQUEST_COARSE_LOCATION = 2
    }
}
