package com.technocreatives.beckon.redux

import arrow.core.Option
import arrow.core.or
import arrow.core.toOption
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.internal.BluetoothState

internal data class BeckonState(
    val saved: List<BeckonDevice>,
    val discovered: List<BeckonDevice>,
    val bluetoothState: BluetoothState
) {
    fun findSavedDevice(macAddress: MacAddress): Option<BeckonDevice> {
        return saved.find { it.deviceInfo().macAddress == macAddress }.toOption()
    }

    fun findDiscoveredDevice(macAddress: MacAddress): Option<BeckonDevice> {
        return discovered.find { it.deviceInfo().macAddress == macAddress }.toOption()
    }

    fun findDevice(macAddress: MacAddress): Option<BeckonDevice> {
        return findSavedDevice(macAddress) or findDiscoveredDevice(macAddress)
    }
}
