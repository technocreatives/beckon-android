package com.technocreatives.beckon.redux

import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.MacAddress
import strikt.api.Assertion

data class EssentialBeckonState(
    val connected: Set<MacAddress>,
    val connecting: Set<MacAddress>,
    val bluetoothState: BluetoothState
)
internal fun BeckonState.connectedMacAddresses(): List<MacAddress> {
    return connectedDevices.map { it.metadata().macAddress }
}

internal fun BeckonState.connectingMacAddresses(): List<MacAddress> {
    return connectingDevices.map { it.macAddress }
}
internal fun BeckonState.toEssential(): EssentialBeckonState {
    return EssentialBeckonState(
        connected = connectedMacAddresses().toSet(),
        connecting = connectingMacAddresses().toSet(),
        bluetoothState = bluetoothState
    )
}

internal fun Assertion.Builder<BeckonState>.isNotChanged(state: BeckonState): Assertion.Builder<BeckonState> =
    assertThat("is not changed $state") {
        it.toEssential() == state.toEssential()
    }

internal fun Assertion.Builder<BeckonState>.hasConnectedDevice(macAddress: MacAddress): Assertion.Builder<BeckonState> =
    assertThat("has a connected device with mac address: $macAddress") {
        it.connectedMacAddresses().contains(macAddress)
    }

internal fun Assertion.Builder<BeckonState>.hasConnectingDevice(macAddress: MacAddress): Assertion.Builder<BeckonState> =
    assertThat("has a connecting device with mac address: $macAddress") {
        it.connectingMacAddresses().contains(macAddress)
    }

internal fun Assertion.Builder<BeckonState>.doesNotHaveConnectedDevice(macAddress: MacAddress): Assertion.Builder<BeckonState> =
    assertThat("does not have a connected device with mac address: $macAddress") {
        macAddress !in it.connectedMacAddresses()
    }

internal fun Assertion.Builder<BeckonState>.doesNotHaveConnectingDevice(macAddress: MacAddress): Assertion.Builder<BeckonState> =
    assertThat("does not have a connecting device with mac address: $macAddress") {
        macAddress !in it.connectingMacAddresses()
    }
