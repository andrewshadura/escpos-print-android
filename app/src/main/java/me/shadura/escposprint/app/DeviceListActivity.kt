/**
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
package me.shadura.escposprint.app

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.*
import android.os.Build
import android.os.Bundle
import androidx.annotation.LayoutRes
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView
import me.shadura.escposprint.L

import java.util.HashSet

import me.shadura.escposprint.R

import me.shadura.escposprint.printservice.*


class DeviceListActivity : AppCompatActivity() {
    private var bluetoothAdapter: BluetoothAdapter = getDefaultAdapter()
    private lateinit var discoveredDevicesArrayAdapter: BluetoothDevicesAdapter
    private var refreshLayout: SwipeRefreshLayout? = null
    private val discoveredDevices = HashSet<Any>()
    private  val discoveredListView: ListView by lazy {
        findViewById<View>(R.id.discovered_devices) as ListView
    }

    private val discoveredDevicesClickListener = OnItemClickListener { _, _, position, _ ->
        bluetoothAdapter.cancelDiscovery()

        val intent = Intent()
        when (val device = discoveredDevicesArrayAdapter.getItem(position)) {
            is BluetoothDevice -> {
                intent.putExtra(EXTRA_DEVICE_ADDRESS, device.address)
                intent.putExtra(EXTRA_DEVICE_NAME, device.name)
            }
            is UsbDevice -> {
                intent.putExtra(EXTRA_DEVICE_ADDRESS, "${device.address}")
                intent.putExtra(EXTRA_DEVICE_USB, true)
                intent.putExtra(EXTRA_DEVICE_NAME, device.name)
            }
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                    if (device.bondState != BOND_BONDED) {
                        if (discoveredDevices.contains(device)) {
                            return
                        }
                        discoveredDevices.add(device)
                        discoveredDevicesArrayAdapter.add(device)
                    }
                }
                ACTION_NAME_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                    if (discoveredDevices.contains(device)) {
                        discoveredDevicesArrayAdapter.notifyDataSetChanged()
                    }
                }
                ACTION_DISCOVERY_FINISHED -> {
                    refreshLayout!!.isRefreshing = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        setResult(Activity.RESULT_CANCELED)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        discoveredDevicesArrayAdapter = BluetoothDevicesAdapter(this, R.layout.device_list_item)
        discoveredListView.adapter = discoveredDevicesArrayAdapter
        discoveredListView.onItemClickListener = discoveredDevicesClickListener

        this.registerReceiver(receiver, IntentFilter(ACTION_FOUND))
        this.registerReceiver(receiver, IntentFilter(ACTION_NAME_CHANGED))
        this.registerReceiver(receiver, IntentFilter(ACTION_DISCOVERY_FINISHED))

        discoveredDevicesArrayAdapter.clear()
        addUsbDevices()
        addPairedDevices()

        val refreshDevices = findViewById<FloatingActionButton>(R.id.refresh_devices)

        refreshLayout = findViewById(R.id.discovered_refresh_layout)
        refreshLayout?.setOnRefreshListener { discoverDevices() }

        refreshDevices.setOnClickListener {
            refreshLayout?.isRefreshing = true
            discoverDevices()
        }
    }

    private fun discoverDevices() {
        if (!bluetoothAdapter.isDiscovering) {
            discoveredListView.longSnackbar(R.string.searching) {
                action(R.string.cancel) {
                    bluetoothAdapter.cancelDiscovery()
                }
            }
            discoveredDevicesArrayAdapter.clear()
            addUsbDevices()
            addPairedDevices()
            discoveredDevices.clear()
            bluetoothAdapter.startDiscovery()
        }
    }

    private fun addUsbDevices() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            val manager = getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = manager.deviceList
            deviceList.forEach { (name, device) ->

                L.i("found device $name: $device")
                if (device.isUsbPrinter) {
                    discoveredDevicesArrayAdapter.add(device)
                }
            }
        }
    }

    private fun addPairedDevices() {
        val pairedDevices = bluetoothAdapter.bondedDevices
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                discoveredDevicesArrayAdapter.add(device)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_refresh -> {
                refreshLayout?.isRefreshing = true
                discoverDevices()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()

        bluetoothAdapter.cancelDiscovery()
        unregisterReceiver(receiver)
    }

    private class BluetoothDeviceViews internal constructor(internal var name: TextView, internal var address: TextView)

    private class BluetoothDevicesAdapter(context: Context, @LayoutRes resource: Int) : ArrayAdapter<Any>(context, resource) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            val views: BluetoothDeviceViews
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.context).inflate(R.layout.device_list_item, parent, false)!!
                views = BluetoothDeviceViews(
                        convertView.findViewById<View>(R.id.bluetooth_device_name) as TextView,
                        convertView.findViewById<View>(R.id.bluetooth_device_address) as TextView
                )
                convertView.tag = views
            } else {
                views = convertView.tag as BluetoothDeviceViews
            }

            when (val device = getItem(position)) {
                is BluetoothDevice -> {
                    views.name.text = device.getNameOrAlias()
                    views.address.text = device.address
                }
                is UsbDevice -> {
                    views.name.text = device.name
                    views.address.text = device.address
                }
            }

            return convertView
        }
    }

    companion object {

        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_USB = "device_usb"
        const val EXTRA_DEVICE_NAME = "device_name"
    }
}
