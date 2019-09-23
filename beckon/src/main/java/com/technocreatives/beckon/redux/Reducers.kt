package com.technocreatives.beckon.redux

internal val reducer = { state: BeckonState, action: BeckonAction ->
    when (action) {
        is BeckonAction.AddConnectedDevice -> state.addConnectedDevice(action.device)
        is BeckonAction.RemoveConnectedDevice -> state.removeConnectedDevice(action.device.metadata().macAddress)
        is BeckonAction.AddConnectingDevice -> state.addConnectingDevice(action.metadata)
        is BeckonAction.RemoveConnectingDevice -> state.removeConnectingDevice(action.metadata.macAddress)
        is BeckonAction.RemoveAllConnectedDevices -> state.copy(connectedDevices = emptyList())
        is BeckonAction.ChangeBluetoothState -> state.copy(bluetoothState = action.state)
    }
}
