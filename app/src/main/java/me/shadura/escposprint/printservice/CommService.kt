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
 * program; if not, see </http://www.gnu.org/licenses/>.
 */
package me.shadura.escposprint.printservice

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel

// Constants that indicate the current connection state
sealed class State {
    object Disconnected : State() // we're doing nothing
    object Connecting : State() // now initiating an outgoing connection
    object NeedsPermission : State()
    object Connected : State()  // now connected to a remote device
    data class Failed(val error: String) : State()
}

sealed class CommServiceMsg
class Connect(val response: CompletableDeferred<State>) : CommServiceMsg()
class Disconnect(val response: CompletableDeferred<State>) : CommServiceMsg()
class Write(val data: ByteArray) : CommServiceMsg()

val String.isBluetoothAddress: Boolean
    get() {
        return (this.count {
            it == ':'
        }) == 5
    }

val String.isUsbAddress: Boolean
    get() {
        return (this.count {
            it == ':'
        }) == 1
    }

fun CoroutineScope.commServiceActor(context: Context, address: String): SendChannel<CommServiceMsg> {
    return when {
        address.isBluetoothAddress ->
            bluetoothServiceActor(address)
        address.isUsbAddress ->
            usbServiceActor(context, address)
        else ->
            throw IllegalStateException("wrong address format")
    }
}
