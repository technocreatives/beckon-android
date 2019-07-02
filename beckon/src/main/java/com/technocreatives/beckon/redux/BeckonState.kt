package com.technocreatives.beckon.redux

import arrow.core.Option
import arrow.core.or
import arrow.core.toOption
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.internal.BluetoothState

internal data class BeckonState(
    val saved: List<BeckonDevice>,
    val connected: List<BeckonDevice>,
    val bluetoothState: BluetoothState
) {
    fun allDevices(): List<BeckonDevice> = saved + connected
    fun findSavedDevice(macAddress: MacAddress): Option<BeckonDevice> {
        return saved.find { it.metadata().macAddress == macAddress }.toOption()
    }

    fun findConnectedDevice(macAddress: MacAddress): Option<BeckonDevice> {
        return connected.find { it.metadata().macAddress == macAddress }.toOption()
    }

    fun findDevice(macAddress: MacAddress): Option<BeckonDevice> {
        return findSavedDevice(macAddress) or findConnectedDevice(macAddress)
    }
}
