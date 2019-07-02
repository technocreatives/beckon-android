package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.internal.BluetoothState

internal fun createBeckonStore(): Store {
    val reducer: Reducer = { state, action ->
        when (action) {
            is Action.AddSavedDevice -> state.copy(saved = addDevice(state.saved, action.device), connected = removeDevice(state.connected, action.device))
            is Action.RemoveSavedDevice -> state.copy(saved = removeDevice(state.saved, action.device))
            is Action.AddConnectedDevice -> state.copy(connected = addDevice(state.connected, action.device))
            is Action.RemoveConnectedDevice -> state.copy(connected = removeDevice(state.connected, action.device))
            is Action.ChangeBluetoothState -> state.copy(bluetoothState = action.state)
        }
    }
    return BeckonStore(reducer, BeckonState(emptyList(), emptyList(), BluetoothState.UNKNOWN))
}

private fun addDevice(devices: List<BeckonDevice>, device: BeckonDevice): List<BeckonDevice> {
    return if (devices.any { it.metadata() == device.metadata() }) devices
    else devices.plus(device)
}

private fun removeDevice(devices: List<BeckonDevice>, device: BeckonDevice): List<BeckonDevice> {
    return devices.minus(device)
}
