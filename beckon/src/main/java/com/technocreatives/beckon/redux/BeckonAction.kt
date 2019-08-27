package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState

internal sealed class BeckonAction {

    data class AddConnectedDevice(val device: BeckonDevice) : BeckonAction()
    data class RemoveConnectedDevice(val device: BeckonDevice) : BeckonAction()
    data class ChangeBluetoothState(val state: BluetoothState) : BeckonAction()
    object RemoveAllConnectedDevices : BeckonAction()
}
