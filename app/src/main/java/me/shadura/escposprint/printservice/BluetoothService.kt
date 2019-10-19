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
package me.shadura.escposprint.printservice

import java.io.IOException
import java.util.UUID

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import me.shadura.escposprint.L
import java.lang.Exception
import kotlin.collections.chunked

private val PRINTER_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

data class Result(var state: State, var error: String)

fun CoroutineScope.bluetoothServiceActor(device: BluetoothDevice) = actor<CommServiceMsg>(Dispatchers.IO) {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    var state: State
    var error: String = ""
    val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)

    process@ for (msg in channel) {
        when (msg) {
            is Connect -> {
                adapter.cancelDiscovery()
                L.i("connecting to $device")
                socket.run {
                    state = try {
                        connect()
                        State.Connected
                    } catch (e: IOException) {
                        error = e.message ?: ""
                        L.e("unable to connect", e)
                        State.Failed
                    }
                }
                msg.response.complete(Result(state, error))
            }
            is Disconnect -> break@process
            is Write -> {
                msg.data.asIterable().chunked(64).forEach {
                    try {
                        socket.outputStream.write(it.toByteArray())
                        socket.outputStream.flush()
                    } catch (e: IOException) {
                        L.e("I/O error occurred:", e)
                    }
                    delay(15)
                }
            }
        }
    }
    socket.outputStream.flush()
    socket.close()
}

fun CoroutineScope.bluetoothServiceActor(address: String): SendChannel<CommServiceMsg> {
    val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    if (adapter == null || !adapter.isEnabled) {
        throw IOException("Bluetooth is not available")
    } else {
        if (BluetoothAdapter.checkBluetoothAddress(address)) {
            val device = adapter.getRemoteDevice(address)
            return bluetoothServiceActor(device)
        } else {
            throw Exception("Invalid Bluetooth address")
        }
    }
}
