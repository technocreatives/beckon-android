package com.technocreatives.beckon.util

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import arrow.core.Option
import arrow.core.toOption

fun BluetoothManager.findDevice(address: String): Option<BluetoothDevice> {

    val device = adapter.bondedDevices.firstOrNull { it.address == address }
            ?: getConnectedDevices(BluetoothProfile.GATT)
                    .firstOrNull { it.address == address } ?: safeGetRemoveDevice(address)
    return device.toOption()
}

private fun BluetoothManager.safeGetRemoveDevice(address: String): BluetoothDevice? {
    return try {
        adapter.getRemoteDevice(address)
    } catch (ex: IllegalArgumentException) {
        null
    }
}
