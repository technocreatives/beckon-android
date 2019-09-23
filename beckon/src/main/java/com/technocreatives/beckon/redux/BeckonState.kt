package com.technocreatives.beckon.redux

import arrow.core.Option
import arrow.core.toOption
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.SavedMetadata

typealias ConnectingDevice = SavedMetadata

internal data class BeckonState(
    val connectedDevices: List<BeckonDevice>,
    val connectingDevices: List<ConnectingDevice>,
    val bluetoothState: BluetoothState
) {
    fun findConnectedDevice(macAddress: MacAddress): Option<BeckonDevice> {
        return connectedDevices.find { it.metadata().macAddress == macAddress }.toOption()
    }

    fun findConnectingDevice(macAddress: MacAddress): Option<ConnectingDevice> {
        return connectingDevices.find { it.macAddress == macAddress }.toOption()
    }

    fun addConnectedDevice(device: BeckonDevice): BeckonState {
        val newConnectedDevices = addConnectedDevice(connectedDevices, device)
        val newConnectingDevice =
            removeConnectingDevice(connectingDevices, device.metadata().macAddress)
        return BeckonState(newConnectedDevices, newConnectingDevice, this.bluetoothState)
    }

    fun removeConnectedDevice(macAddress: MacAddress): BeckonState {
        return copy(connectedDevices = removeConnectedDevice(connectedDevices, macAddress))
    }

    fun addConnectingDevice(device: ConnectingDevice): BeckonState {
        val newConnectedDevices = removeConnectedDevice(connectedDevices, device.macAddress)
        val newConnectingDevice = addConnectingDevice(connectingDevices, device)
        return BeckonState(newConnectedDevices, newConnectingDevice, this.bluetoothState)
    }

    fun removeConnectingDevice(macAddress: MacAddress): BeckonState {
        return copy(connectingDevices = removeConnectingDevice(connectingDevices, macAddress))
    }
}

private fun addConnectedDevice(
    devices: List<BeckonDevice>,
    device: BeckonDevice
): List<BeckonDevice> {
    return removeConnectedDevice(devices, device.metadata().macAddress) + device
}

private fun removeConnectedDevice(
    devices: List<BeckonDevice>,
    macAddress: MacAddress
): List<BeckonDevice> {
    return devices.filter { it.metadata().macAddress != macAddress }
}

private fun removeConnectingDevice(
    devices: List<ConnectingDevice>,
    macAddress: MacAddress
): List<ConnectingDevice> {
    return devices.filter { it.macAddress != macAddress }
}

private fun addConnectingDevice(
    devices: List<ConnectingDevice>,
    device: ConnectingDevice
): List<ConnectingDevice> {
    return removeConnectingDevice(devices, device.macAddress) + device
}
