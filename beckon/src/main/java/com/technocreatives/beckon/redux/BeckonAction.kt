package com.technocreatives.beckon.redux

import com.lenguyenthanh.redux.Action
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState

internal sealed class BeckonAction : Action {

    class AddConnectedDevice(val device: BeckonDevice) : BeckonAction()
    class RemoveConnectedDevice(val device: BeckonDevice) : BeckonAction()
    class ChangeBluetoothState(val state: BluetoothState) : BeckonAction()
    object RemoveAllConnectedDevices : BeckonAction()
}
