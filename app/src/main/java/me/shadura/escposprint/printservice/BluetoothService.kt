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
package me.shadura.escposprint.printservice

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import me.shadura.escposprint.L
import java.lang.Exception
import kotlin.collections.chunked
import kotlin.concurrent.thread

private val PRINTER_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

// Constants that indicate the current connection state
enum class State {
    STATE_NONE, // we're doing nothing
    STATE_CONNECTING, // now initiating an outgoing connection
    STATE_CONNECTED,  // now connected to a remote device
    STATE_FAILED
}

sealed class BluetoothServiceMsg
class Connect(val response: CompletableDeferred<State>) : BluetoothServiceMsg()
object Disconnect : BluetoothServiceMsg()
class Write(val data: ByteArray) : BluetoothServiceMsg()

fun CoroutineScope.bluetoothServiceActor(device: BluetoothDevice) = actor<BluetoothServiceMsg>(Dispatchers.IO) {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    var state: State
    val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)

    process@ for (msg in channel) {
        when (msg) {
            is Connect -> {
                adapter.cancelDiscovery()
                L.i("connecting to $device")
                socket.run {
                    state = try {
                        connect()
                        State.STATE_CONNECTED
                    } catch (e: IOException) {
                        L.e("unable to connect", e)
                        State.STATE_FAILED
                    }
                }
                msg.response.complete(state)
            }
            is Disconnect -> break@process
            is Write -> {
                msg.data.asIterable().chunked(256).forEach {
                    socket.outputStream.write(it.toByteArray())
                    socket.outputStream.flush()
                    delay(10)
                }
            }
        }
    }
    socket.outputStream.flush()
    socket.close()
}

fun CoroutineScope.bluetoothServiceActor(address: String): SendChannel<BluetoothServiceMsg> {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    if (adapter == null) {
        throw Exception("Bluetooth is not available")
    } else {
        if (BluetoothAdapter.checkBluetoothAddress(address)) {
            val device = adapter.getRemoteDevice(address)
            return bluetoothServiceActor(device)
        } else {
            throw Exception("Invalid Bluetooth address")
        }
    }
}
