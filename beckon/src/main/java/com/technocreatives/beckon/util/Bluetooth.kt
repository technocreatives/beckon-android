package com.technocreatives.beckon.util

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import arrow.core.Option
import arrow.core.toOption
import com.technocreatives.beckon.BondState
import timber.log.Timber

fun BluetoothManager.findDevice(address: String): Option<BluetoothDevice> {
    getConnectedDevices(BluetoothProfile.GATT).onEach {
        Timber.d("Gatt Connected ${it.address}")
    }
    val device =
        getConnectedDevices(BluetoothProfile.GATT)
            .firstOrNull { it.address == address }
            ?: adapter.bondedDevices.firstOrNull { it.address == address }
    return device.toOption()
}

fun BluetoothManager.findBondedDevice(address: String): Option<BluetoothDevice> {
    return adapter.bondedDevices.firstOrNull { it.address == address }.toOption()
}

fun BluetoothManager.connectedDevices(): List<BluetoothDevice> {
    return getConnectedDevices(BluetoothProfile.GATT)
}

fun BluetoothManager.safeGetRemoteDevice(address: String): BluetoothDevice? {
    return try {
        adapter.getRemoteDevice(address)
    } catch (ex: IllegalArgumentException) {
        null
    }
}

fun Context.bluetoothManager(): BluetoothManager {
    return getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
}

internal fun Int.toBondState(): BondState {
    return when (this) {
        BluetoothDevice.BOND_BONDED -> BondState.Bonded
        BluetoothDevice.BOND_BONDING -> BondState.CreatingBond
        BluetoothDevice.BOND_NONE -> BondState.NotBonded
        else -> BondState.NotBonded
    }
}
