package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.internal.BluetoothState

internal fun createBeckonStore(): Store {
    val reducer: Reducer = { state, action ->
        when (action) {
            is AddSavedDevice -> state.copy(saved = addDevice(state.saved, action.device), discovered = removeDevice(state.discovered, action.device))
            is RemoveSavedDevice -> state.copy(saved = removeDevice(state.saved, action.device))
            is AddDiscoveredDevice -> state.copy(discovered = addDevice(state.discovered, action.device))
            is RemoveDiscoveredDevice -> state.copy(discovered = removeDevice(state.discovered, action.device))
            is ChangeBluetoothState -> state.copy(bluetoothState = action.state)
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
