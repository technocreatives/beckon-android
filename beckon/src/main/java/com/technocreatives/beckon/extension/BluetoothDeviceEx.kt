package com.technocreatives.beckon.extension

import android.bluetooth.BluetoothDevice

fun BluetoothDevice.removeBond() {
    this::class.java.getMethod("removeBond").invoke(this)
}