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

class BluetoothService
/**
 * Constructor. Prepares a new Bluetooh session.
 * @param context  The UI Activity Context
 * @param handler  A Handler to send messages back to the UI Activity
 */
(context: Context, private val mHandler: Handler) {

    private val mAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

    private var mState: State = State.STATE_NONE

    /**
     * Return the current connection state.
     */
    /**
     * Set the current state of the connection
     * @param state  An integer defining the current connection state
     */
    // Give the new state to the Handler so the UI Activity can update
    var state: State
        @Synchronized get() = mState
        @Synchronized private set(state: State) {
            if (DEBUG) Log.d(TAG, "setState() $mState -> $state")
            mState = state
            mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state.ordinal, -1).sendToTarget()
        }

    // Constants that indicate the current connection state
    enum class State {
        STATE_NONE, // we're doing nothing
        STATE_CONNECTING, // now initiating an outgoing connection
        STATE_CONNECTED  // now connected to a remote device
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    @Synchronized
    fun connect(device: BluetoothDevice) {
        if (DEBUG) Log.d(TAG, "connect to: $device")

        // Cancel any thread attempting to make a connection
        if (mState == State.STATE_CONNECTING) {
            mConnectThread?.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        mConnectedThread?.cancel()
        mConnectedThread = null

        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device)
        mConnectThread!!.start()
        state = State.STATE_CONNECTING
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        if (DEBUG) Log.d(TAG, "connected")

        // Cancel the thread that completed the connection
        mConnectThread?.cancel()
        mConnectThread = null

        // Cancel any thread currently running a connection
        mConnectedThread?.cancel()
        mConnectedThread = null

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket)
        mConnectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString("DEVICE_NAME", device.name)
        msg.data = bundle
        mHandler.sendMessage(msg)

        state = State.STATE_CONNECTED
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        if (DEBUG) {
            Log.d(TAG, "stop")
        }
        state = State.STATE_NONE
        mConnectThread?.cancel()
        mConnectThread = null
        mConnectedThread?.cancel()
        mConnectedThread = null
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray) {
        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState == State.STATE_CONNECTED) {
                mConnectedThread!!.write(out)
            }
        }
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed() {
        state = State.STATE_NONE

        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString("MESSAGE_TOAST", "Unable to connect device")
        msg.data = bundle
        mHandler.sendMessage(msg)
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        state = State.STATE_NONE

        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString("MESSAGE_TOAST", "Device connection was lost")
        msg.data = bundle
        mHandler.sendMessage(msg)
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket?

        init {
            this.name = "ConnectThread"
            Log.d(TAG, "create $name")
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            mmSocket = try {
                mmDevice.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                Log.e(TAG, "create() failed", e)
                null
            }
        }

        override fun run() {
            Log.i(TAG, "BEGIN $name")

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            if (mmSocket != null) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmSocket.connect()
                } catch (e: IOException) {
                    connectionFailed()
                    // Close the socket
                    try {
                        mmSocket.close()
                    } catch (e2: IOException) {
                        Log.e(TAG, "unable to close() socket during connection failure", e2)
                    }

                    return
                }

                // Reset the ConnectThread because we're done
                synchronized(this@BluetoothService) {
                    mConnectThread = null
                }

                // Start the connected thread
                connected(mmSocket, mmDevice)
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }

        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            this.name = "ConnectThread"
            Log.d(TAG, "create $name")
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
            }

            mmInStream = tmpIn
            mmOutStream = tmpOut
            mState = BluetoothService.State.STATE_CONNECTED
        }

        override fun run() {
            Log.i(TAG, "BEGIN $name")
            var bytes: Int

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    val buffer = ByteArray(1024)
                    // Read from the InputStream
                    bytes = mmInStream!!.read(buffer)

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget()
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }

            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        fun write(buffer: ByteArray) {
            try {
                mmOutStream!!.write(buffer)
                mmOutStream.flush()

                Log.i(TAG, String(buffer))

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }

        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }

        }
    }

    companion object {
        private const val TAG = "BluetoothService"
        private const val DEBUG = true

        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5
        const val MESSAGE_CONNECTION_LOST = 6
        const val MESSAGE_CONNECTION_FAILURE = 7
    }


}
