package com.technocreatives.beckon.redux

import com.lenguyenthanh.redux.Log
import com.lenguyenthanh.redux.Store
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState
import timber.log.Timber

internal typealias BeckonStore = Store<BeckonState, BeckonAction>

internal fun createBeckonStore(): BeckonStore {
    val reducer = { state: BeckonState, action: BeckonAction ->
        when (action) {
            is BeckonAction.AddConnectedDevice -> state.copy(devices = addDevice(state.devices, action.device))
            is BeckonAction.RemoveConnectedDevice -> state.copy(devices = removeDevice(state.devices, action.device))
            is BeckonAction.ChangeBluetoothState -> state.copy(bluetoothState = action.state)
            is BeckonAction.RemoveAllConnectedDevices -> state.copy(devices = emptyList())
        }
    }

    return Store(reducer, { BeckonState(emptyList(), BluetoothState.UNKNOWN) }, log = object : Log {
        override fun log(tag: String, message: String) {
            Timber.tag(tag).d(message)
        }
    })
}

private fun addDevice(devices: List<BeckonDevice>, device: BeckonDevice): List<BeckonDevice> {
    return removeDevice(devices, device) + device
}

private fun removeDevice(devices: List<BeckonDevice>, device: BeckonDevice): List<BeckonDevice> {
    val macAddress = device.metadata().macAddress
    return devices.filter { it.metadata().macAddress != macAddress }
}
