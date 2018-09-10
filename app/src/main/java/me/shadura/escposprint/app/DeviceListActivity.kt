/**
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
 * program; if not, see <http://www.gnu.org/licenses/>.
 */
package me.shadura.escposprint.app

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView

import java.util.HashSet

import me.shadura.escposprint.R

class DeviceListActivity : AppCompatActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var discoveredDevicesArrayAdapter: BluetoothDevicesAdapter? = null
    private var snackbar: Snackbar? = null
    private var refreshLayout: SwipeRefreshLayout? = null
    private val discoveredDevices = HashSet<BluetoothDevice>()

    private val discoveredDevicesClickListener = OnItemClickListener { _, _, position, id ->
        bluetoothAdapter!!.cancelDiscovery()

        val intent = Intent()
        intent.putExtra(EXTRA_DEVICE_ADDRESS,
                discoveredDevicesArrayAdapter!!.getItem(position)!!.address)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        if (discoveredDevices.contains(device)) {
                            return
                        }
                        discoveredDevices.add(device)
                        discoveredDevicesArrayAdapter!!.add(device)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
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

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        discoveredDevicesArrayAdapter = BluetoothDevicesAdapter(this, R.layout.device_list_item)
        val discoveredListView = findViewById<View>(R.id.discovered_devices) as ListView
        discoveredListView.adapter = discoveredDevicesArrayAdapter
        discoveredListView.onItemClickListener = discoveredDevicesClickListener

        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        this.registerReceiver(receiver, filter)

        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        this.registerReceiver(receiver, filter)

        addPairedDevices()

        val refreshDevices = findViewById<FloatingActionButton>(R.id.refresh_devices)

        val mCancelListener = OnClickListener { bluetoothAdapter!!.cancelDiscovery() }

        snackbar = Snackbar.make(discoveredListView, "Searching for new Bluetooth devices", Snackbar.LENGTH_LONG)
                .setAction("Cancel", mCancelListener)

        refreshLayout = findViewById(R.id.discovered_refresh_layout)
        refreshLayout!!.setOnRefreshListener { discoverDevices() }

        refreshDevices.setOnClickListener {
            refreshLayout!!.isRefreshing = true
            discoverDevices()
        }
    }

    private fun discoverDevices() {
        if (!bluetoothAdapter!!.isDiscovering) {
            snackbar!!.show()
            addPairedDevices()
            discoveredDevices.clear()
            bluetoothAdapter!!.startDiscovery()
        }
    }

    private fun addPairedDevices() {
        discoveredDevicesArrayAdapter!!.clear()
        val pairedDevices = bluetoothAdapter!!.bondedDevices
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                discoveredDevicesArrayAdapter!!.add(device)
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
                refreshLayout!!.isRefreshing = true
                discoverDevices()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()

        bluetoothAdapter?.cancelDiscovery()
        unregisterReceiver(receiver)
    }

    private class BluetoothDeviceViews internal constructor(internal var name: TextView, internal var address: TextView)

    private class BluetoothDevicesAdapter(context: Context, @LayoutRes resource: Int) : ArrayAdapter<BluetoothDevice>(context, resource) {

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

            val device: BluetoothDevice = getItem(position)
            views.name.text = device.name ?: "(unnamed)"
            views.address.text = device.address

            return convertView
        }
    }

    companion object {

        var EXTRA_DEVICE_ADDRESS = "device_address"
    }
}