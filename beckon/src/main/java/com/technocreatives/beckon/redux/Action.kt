package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.internal.BluetoothState

internal sealed class Action {

    class AddSavedDevice(val device: BeckonDevice) : Action()
    class RemoveSavedDevice(val device: BeckonDevice) : Action()

    class AddConnectedDevice(val device: BeckonDevice) : Action()
    class RemoveConnectedDevice(val device: BeckonDevice) : Action()

    class ChangeBluetoothState(val state: BluetoothState) : Action()
}
