package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.SavedMetadata

internal sealed class BeckonAction {

    data class AddConnectedDevice(val device: BeckonDevice) : BeckonAction()
    data class RemoveConnectedDevice(val device: BeckonDevice) : BeckonAction()
    data class AddConnectingDevice(val metadata: SavedMetadata) : BeckonAction()
    data class RemoveConnectingDevice(val metadata: SavedMetadata) : BeckonAction()
    // data class MoveToConnectedDevice(val device: BeckonDevice): BeckonAction()
    data class ChangeBluetoothState(val state: BluetoothState) : BeckonAction()
    object RemoveAllConnectedDevices : BeckonAction()
}
