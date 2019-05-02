package com.technocreatives.beckon.redux

import com.technocreatives.beckon.DeviceInfo
import com.technocreatives.beckon.internal.BluetoothState

internal sealed class Action

internal class AddDevice(val device: DeviceInfo) : Action()
internal class RemoveDevice(val device: DeviceInfo) : Action()
internal class ChangeBluetoothState(val state: BluetoothState) : Action()
