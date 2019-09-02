package com.technocreatives.beckon.util

import android.bluetooth.BluetoothDevice

fun BluetoothDevice.debugInfo(): String {
    return "Name: ${this.name} + MacAddress: ${this.address} bondState: ${this.bondState} type: ${this.type} uuids: $uuids"
}
