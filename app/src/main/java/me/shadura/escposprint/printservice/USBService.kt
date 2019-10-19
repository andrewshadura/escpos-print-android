/**
 * Copyright (C) 2019 Andrej Shadura
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

import android.content.Context
import android.hardware.usb.*
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import java.io.IOException

val UsbDevice.interfaces: Sequence<UsbInterface>
    get() {
        return sequence {
            (0 until this@interfaces.interfaceCount).map {
                yield(this@interfaces.getInterface(it))
            }
        }
    }

val UsbInterface.endpoints: Sequence<UsbEndpoint>
    get() {
        return sequence {
            (0 until this@endpoints.endpointCount).map {
                yield(this@endpoints.getEndpoint(it))
            }
        }
    }

data class EndpointPair<Twrite,Tread>(val write: Twrite, val read: Tread)

val UsbInterface.printerEndpoints: EndpointPair<UsbEndpoint, UsbEndpoint?>
    get() {
        val candidates = this.endpoints.filter {
            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        }.associateBy {
            it.direction
        }
        return EndpointPair(
                write = candidates[0] ?: throw IOException("no write endpoint?!"),
                read = candidates[0x80]
        )
    }

val UsbDevice.name: String
    get() = if (Build.VERSION.SDK_INT >= 21) {
        this.productName
    } else {
        null
    } ?: "USB printer ${this.address}"

val UsbDevice.address: String
    get() = "%04X:%04X".format(this.vendorId, this.productId)

val UsbDevice.isUsbPrinter: Boolean
    get() = this.printerInterface != null

val UsbDevice.printerInterface: UsbInterface?
    get() {
        this.interfaces.forEach { intf ->
            if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER && intf.interfaceSubclass == 1) {
                return intf
            }
        }
        return null
    }

fun CoroutineScope.usbServiceActor(context: Context, device: UsbDevice?) = actor<CommServiceMsg>(Dispatchers.IO) {
    val manager = context.usbManager
    var state: State
    var error = ""
    var conn: UsbDeviceConnection? = null
    lateinit var ep: EndpointPair<UsbEndpoint, UsbEndpoint?>

    process@ for (msg in channel) {
        when (msg) {
            is Connect -> {
                state = device?.let { device ->
                    if (!manager.hasPermission(device)) {
                        State.NeedsPermission
                    } else {
                        conn = manager.openDevice(device)
                        conn?.run {
                            device.printerInterface?.let {
                                claimInterface(it, true)
                                ep = it.printerEndpoints
                            }
                            State.Connected
                        }
                    }
                } ?: State.Failed("Cannot connect to the USB device")
                msg.response.complete(state)
            }
            is Disconnect -> {
                conn?.close()
                break@process
            }
            is Write -> {
                msg.data.asIterable().chunked(64).forEach {
                    val bytes = it.toByteArray()
                    conn?.bulkTransfer(ep.write, bytes, bytes.size, 0)
                    delay(15)
                }
            }
        }
    }
}

val Context.usbManager: UsbManager
    get() =
        getSystemService(Context.USB_SERVICE) as UsbManager

fun Context.getUsbDevice(address: String): UsbDevice? {
    val (vendorId, productId) = address.split(":", limit = 2).map {
        it.toInt(16)
    }
    val deviceList = usbManager.deviceList
    deviceList.forEach { (name, device) ->
        if (device.isUsbPrinter && device.vendorId == vendorId && device.productId == productId) {
            return device
        }
    }
    return null
}

fun Context.hasUsbPermission(address: String): Boolean {
    return getUsbDevice(address)?.let { device ->
        usbManager.hasPermission(device)
    } ?: false
}

fun CoroutineScope.usbServiceActor(context: Context, address: String): SendChannel<CommServiceMsg> =
    context.getUsbDevice(address)?.let { device ->
        usbServiceActor(context, device)
    } ?: throw IOException("device $address not found")

