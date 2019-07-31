package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState

internal sealed class Action {

    class AddConnectedDevice(val device: BeckonDevice) : Action()
    class RemoveConnectedDevice(val device: BeckonDevice) : Action()

    class ChangeBluetoothState(val state: BluetoothState) : Action()
}
