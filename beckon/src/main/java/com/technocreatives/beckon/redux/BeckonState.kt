package com.technocreatives.beckon.redux

import arrow.core.Option
import arrow.core.toOption
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.MacAddress

internal data class BeckonState(
    val devices: List<BeckonDevice>,
    val bluetoothState: BluetoothState
) {

    fun findDevice(macAddress: MacAddress): Option<BeckonDevice> {
        return devices.find { it.metadata().macAddress == macAddress }.toOption()
    }
}
