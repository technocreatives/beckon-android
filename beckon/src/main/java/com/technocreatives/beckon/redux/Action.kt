package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.internal.BluetoothState

internal sealed class Action

internal class AddSavedDevice(val device: BeckonDevice) : Action()
internal class RemoveSavedDevice(val device: BeckonDevice) : Action()

internal class AddDiscoveredDevice(val device: BeckonDevice) : Action()
internal class RemoveDiscoveredDevice(val device: BeckonDevice) : Action()

internal class ChangeBluetoothState(val state: BluetoothState) : Action()
