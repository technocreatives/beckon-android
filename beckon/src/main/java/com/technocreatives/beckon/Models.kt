package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import android.content.Context
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.UUID

data class DiscoveredPeripheral(val device: BluetoothDevice, val rssi: Int) : Comparable<DiscoveredPeripheral> {
    override fun compareTo(other: DiscoveredPeripheral): Int {
        return this.device.address.compareTo(other.device.address)
    }
}

class CharacteristicNotFoundException(override val message: String) : Exception()

data class DeviceError(
    val device: BluetoothDevice?,
    override val message: String?,
    val errorCode: Int
) : Throwable(message)

data class ScannerSetting(
    val filters: List<ScanFilter>,
    val settings: ScanSettings
)

class ScanFailureException(val errorCode: Int) : Exception()

typealias DeviceState<State> = Pair<ConnectionState, State>

typealias CharacteristicMapper<Changes> = (Data) -> Changes

data class Characteristic<Changes>(
    val characteristic: UUID,
    val mapper: CharacteristicMapper<Changes>
    // val type: [notify, read, write]
)

data class Characteristics<Changes>(
    val serviceUUID: UUID,
    val characteristics: List<Characteristic<Changes>>
)

typealias Reducer<Changes, State> = (Changes, State) -> State
typealias BeckonDeviceFactory<Changes, State> = (Context, BluetoothDevice, BeckonManagerCallbacks) -> BeckonDevice<Changes, State>

fun <Changes, State> createFactory(
    list: List<Characteristics<Changes>>,
    reducer: Reducer<Changes, State>,
    state: State
): BeckonDeviceFactory<Changes, State> {
    return { context, peripheral, callbacks ->
        BeckonDevice(
            context, callbacks, peripheral,
            list,
            reducer,
            state
        )
    }
}