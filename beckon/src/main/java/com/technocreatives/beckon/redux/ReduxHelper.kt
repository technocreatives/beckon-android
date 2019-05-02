package com.technocreatives.beckon.redux

import com.technocreatives.beckon.DeviceInfo
import com.technocreatives.beckon.internal.BluetoothState

internal fun combineReducers(vararg reducers: Reducer): Reducer {
    return { initial, action ->
        reducers.fold(initial) { state, reducer ->
            reducer(state, action)
        }
    }
}

internal fun createBeckonStore(): Store {
    val reducer: Reducer = { state, action ->
        when (action) {
            is AddDevice -> state.copy(devices = addDevice(state.devices, action.device))
            is RemoveDevice -> state.copy(devices = removeDevice(state.devices, action.device))
            is ChangeBluetoothState -> state.copy(bluetoothState = action.state)
        }
    }
    return BeckonStore(reducer, BeckonState(emptyList(), BluetoothState.UN_KNOWN))
}

private fun addDevice(devices: List<DeviceInfo>, device: DeviceInfo): List<DeviceInfo> {
    return if (devices.any { it.macAddress == device.macAddress }) devices
    else devices.plus(device)
}

private fun removeDevice(devices: List<DeviceInfo>, device: DeviceInfo): List<DeviceInfo> {
    return devices.minus(device)
}
