package com.technocreatives.beckon.redux

import com.lenguyenthanh.redux.Reducer
import com.lenguyenthanh.redux.Store
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState

internal typealias BeckonStore = Store<BeckonState>

internal fun createBeckonStore(): Store<BeckonState> {
    val reducer: Reducer<BeckonState> = { state, action ->
        when (action) {
            is BeckonAction.AddConnectedDevice -> state.copy(devices = addDevice(state.devices, action.device))
            is BeckonAction.RemoveConnectedDevice -> state.copy(devices = removeDevice(state.devices, action.device))
            is BeckonAction.ChangeBluetoothState -> state.copy(bluetoothState = action.state)
            is BeckonAction.RemoveAllConnectedDevices -> state.copy(devices = emptyList())
            else -> throw RuntimeException("Unsupported action exception")
        }
    }
    return Store(reducer, BeckonState(emptyList(), BluetoothState.UNKNOWN))
}

private fun addDevice(devices: List<BeckonDevice>, device: BeckonDevice): List<BeckonDevice> {
    return removeDevice(devices, device) + device
}

private fun removeDevice(devices: List<BeckonDevice>, device: BeckonDevice): List<BeckonDevice> {
    val macAddress = device.metadata().macAddress
    return devices.filter { it.metadata().macAddress != macAddress }
}
