package com.technocreatives.beckon.util

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import arrow.core.Option
import arrow.core.toOption

fun BluetoothManager.findDevice(address: String): Option<BluetoothDevice> {
    val device = adapter.bondedDevices.firstOrNull { it.address == address }
        ?: getConnectedDevices(BluetoothProfile.GATT)
            .firstOrNull { it.address == address } ?: safeGetRemoteDevice(address)
    return device.toOption()
}

fun BluetoothManager.findBondedDevice(address: String): Option<BluetoothDevice> {
    return adapter.bondedDevices.firstOrNull { it.address == address }.toOption()
}

private fun BluetoothManager.safeGetRemoteDevice(address: String): BluetoothDevice? {
    return try {
        adapter.getRemoteDevice(address)
    } catch (ex: IllegalArgumentException) {
        null
    }
}

fun Context.bluetoothManager(): BluetoothManager {
    return getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
}
