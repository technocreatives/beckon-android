package com.technocreatives.beckon.util

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile

fun BluetoothManager.findDevice(address: String): BluetoothDevice? {

    return adapter.bondedDevices.firstOrNull { it.address == address }
        ?: getConnectedDevices(BluetoothProfile.GATT)
            .firstOrNull { it.address == address } ?: safeGetRemoveDevice(address)
}

private fun BluetoothManager.safeGetRemoveDevice(address: String): BluetoothDevice? {
    return try {
        adapter.getRemoteDevice(address)
    } catch (ex: IllegalArgumentException) {
        null
    }
}
