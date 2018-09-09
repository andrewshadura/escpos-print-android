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
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView

import java.util.HashSet

import me.shadura.escposprint.R
import me.shadura.escposprint.printservice.BluetoothService

class DeviceListActivity : AppCompatActivity() {
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mDiscoveredDevicesArrayAdapter: BluetoothDevicesAdapter? = null
    private var mSnackbar: Snackbar? = null
    private var mRefreshLayout: SwipeRefreshLayout? = null
    private val discoveredDevices = HashSet<BluetoothDevice>()

    private val mDiscoveredDevicesClickListener = OnItemClickListener { av, v, position, id ->
        mBluetoothAdapter!!.cancelDiscovery()

        val intent = Intent()
        intent.putExtra(EXTRA_DEVICE_ADDRESS,
                mDiscoveredDevicesArrayAdapter!!.getItem(position)!!.address)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    if (discoveredDevices.contains(device)) {
                        return
                    }
                    discoveredDevices.add(device)
                    mDiscoveredDevicesArrayAdapter!!.add(device)
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                mRefreshLayout!!.isRefreshing = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        setResult(Activity.RESULT_CANCELED)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        mDiscoveredDevicesArrayAdapter = BluetoothDevicesAdapter(this, R.layout.device_list_item)
        val discoveredListView = findViewById<View>(R.id.discovered_devices) as ListView
        discoveredListView.adapter = mDiscoveredDevicesArrayAdapter
        discoveredListView.onItemClickListener = mDiscoveredDevicesClickListener

        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        this.registerReceiver(mReceiver, filter)

        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        this.registerReceiver(mReceiver, filter)

        addPairedDevices()

        val refresh_devices = findViewById<FloatingActionButton>(R.id.refresh_devices)

        val mCancelListener = OnClickListener { mBluetoothAdapter!!.cancelDiscovery() }

        mSnackbar = Snackbar.make(discoveredListView, "Searching for new Bluetooth devices", Snackbar.LENGTH_LONG)
                .setAction("Cancel", mCancelListener)

        mRefreshLayout = findViewById(R.id.discovered_refresh_layout)
        mRefreshLayout!!.setOnRefreshListener { discoverDevices() }

        refresh_devices.setOnClickListener {
            mRefreshLayout!!.isRefreshing = true
            discoverDevices()
        }
    }

    private fun discoverDevices() {
        if (!mBluetoothAdapter!!.isDiscovering) {
            mSnackbar!!.show()
            addPairedDevices()
            discoveredDevices.clear()
            mBluetoothAdapter!!.startDiscovery()
        }
    }

    private fun addPairedDevices() {
        mDiscoveredDevicesArrayAdapter!!.clear()
        val pairedDevices = mBluetoothAdapter!!.bondedDevices
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                mDiscoveredDevicesArrayAdapter!!.add(device)
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
                mRefreshLayout!!.isRefreshing = true
                discoverDevices()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mBluetoothAdapter != null) {
            mBluetoothAdapter!!.cancelDiscovery()
        }
        unregisterReceiver(mReceiver)
    }

    private class BluetoothDeviceViews internal constructor(internal var name: TextView, internal var address: TextView)

    private class BluetoothDevicesAdapter(context: Context, @LayoutRes resource: Int) : ArrayAdapter<BluetoothDevice>(context, resource) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            val views: BluetoothDeviceViews
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.context).inflate(R.layout.device_list_item, parent, false)
                views = BluetoothDeviceViews(
                        convertView!!.findViewById<View>(R.id.bluetooth_device_name) as TextView,
                        convertView.findViewById<View>(R.id.bluetooth_device_address) as TextView
                )
                convertView.tag = views
            } else {
                views = convertView.tag as BluetoothDeviceViews
            }

            val device = getItem(position)
            if (device != null) {
                val name = if (device.name != null) device.name else "(unnamed)"
                views.name.text = name
                views.address.text = device.address
            } else {
                throw IllegalStateException("Bluetooth device list can't have invalid items")
            }

            return convertView
        }

        fun removeItem(position: Int) {
            remove(getItem(position))
        }
    }

    companion object {

        var EXTRA_DEVICE_ADDRESS = "device_address"
    }
}
