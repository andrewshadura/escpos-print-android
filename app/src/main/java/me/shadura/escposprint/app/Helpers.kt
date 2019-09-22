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
package me.shadura.escposprint.app

import android.view.View
import android.widget.*
import java.lang.reflect.InvocationTargetException
import android.bluetooth.BluetoothDevice
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar

fun Spinner.setOnItemSelectedListener(l: (parent: AdapterView<*>, view: View?, position: Int, id: Long) -> Unit) {
    this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {

        }

        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
            l(parent, view, position, id)
        }
    }
}

fun SeekBar.setOnChangeListener(l: (seekBar: SeekBar?, progress: Int, fromUser: Boolean) -> Unit) {
    this.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser) {
                l(seekBar, progress, false)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {

        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            seekBar?.run {
                l(this, this.progress, true)
            }
        }
    })
}

fun BluetoothDevice.getNameOrAlias(default: String = "(unnamed)"): String {
    return try {
        (this.javaClass.getMethod("getAlias").invoke(this) as String?)
    } catch (e: NoSuchMethodException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: InvocationTargetException) {
        null
    } ?: this.name ?: default
}

fun View.longSnackbar(@StringRes messageRes: Int, f: (Snackbar.() -> Unit)? = null) {
    snackbar(resources.getString(messageRes), Snackbar.LENGTH_LONG, f)
}

fun View.longSnackbar(message: String, f: (Snackbar.() -> Unit)? = null) {
    snackbar(message, Snackbar.LENGTH_LONG, f)
}

fun View.snackbar(@StringRes messageRes: Int, length: Int = Snackbar.LENGTH_LONG, f: (Snackbar.() -> Unit)? = null) {
    snackbar(resources.getString(messageRes), length, f)
}

fun View.snackbar(message: String, length: Int = Snackbar.LENGTH_LONG, f: (Snackbar.() -> Unit)? = null) {
    with(Snackbar.make(this, message, length)) {
        f?.invoke(this)
        show()
    }
}

fun Snackbar.action(@StringRes actionRes: Int, color: Int? = null, listener: (View) -> Unit) {
    action(view.resources.getString(actionRes), color, listener)
}

fun Snackbar.action(action: String, color: Int? = null, listener: (View) -> Unit) {
    setAction(action, listener)
    color?.let { setActionTextColor(color) }
}
