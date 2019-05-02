package com.technocreatives.beckon.redux

import com.technocreatives.beckon.DeviceInfo
import com.technocreatives.beckon.internal.BluetoothState

internal data class BeckonState(
    val devices: List<DeviceInfo>,
    val bluetoothState: BluetoothState
)
