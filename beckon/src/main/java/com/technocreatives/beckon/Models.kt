package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.lang.Exception
import java.util.UUID

typealias MacAddress = String

data class DeviceChange(val address: String, val change: Change)

data class DeviceState(val state: ConnectionState, val change: Change)

data class Change(val key: UUID, val data: Data)

data class BeckonScanResult(internal val device: BluetoothDevice, val rssi: Int) {
    val macAddress: String = device.address
    val name: String = device.name
}

sealed class ConnectionState {
    object NotStarted : ConnectionState()
    object Disconnecting : ConnectionState()
    object Disconnected : ConnectionState()
    object Connected : ConnectionState()
    object NotSupported : ConnectionState()
    object BondinFailed : ConnectionState()
    object Bonded : ConnectionState()
    object Ready : ConnectionState()
    object Connecting : ConnectionState()
    class Failed(val message: String, val errorCode: Int) : ConnectionState()
}

data class ScannerSetting(
    val settings: ScanSettings,
    val filters: List<ScanFilter>
)

data class Characteristic(val uuid: UUID, val service: UUID, val notify: Boolean)

class ScanFailureException(val errorCode: Int, message: String? = null) : Exception(message)

typealias CharacteristicMapper<T> = (Change) -> T
