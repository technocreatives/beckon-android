package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState

internal fun createBeckonStore(): Store {
    val reducer: Reducer = { state, action ->
        when (action) {
            is Action.AddConnectedDevice -> state.copy(devices = addDevice(state.devices, action.device))
            is Action.RemoveConnectedDevice -> state.copy(devices = removeDevice(state.devices, action.device))
            is Action.ChangeBluetoothState -> state.copy(bluetoothState = action.state)
        }
    }
    return BeckonStore(reducer, BeckonState(emptyList(), BluetoothState.UNKNOWN))
}

private fun addDevice(devices: List<BeckonDevice>, device: BeckonDevice): List<BeckonDevice> {
    return removeDevice(devices, device) + device
}

private fun removeDevice(devices: List<BeckonDevice>, device: BeckonDevice): List<BeckonDevice> {
    val macAddress = device.metadata().macAddress
    return devices.filter { it.metadata().macAddress != macAddress }
}
