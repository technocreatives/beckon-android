package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.UUID

typealias MacAddress = String

data class DeviceFilter(
    val deviceName: String?,
    val deviceAddress: String?
)

data class ScannerSetting(
    val settings: ScanSettings,
    val filters: List<DeviceFilter>
)

data class BeckonScanResult(internal val device: BluetoothDevice, val rssi: Int) {
    val macAddress: String = device.address
    val name: String? = device.name

    fun filter(filters: List<DeviceFilter>): Boolean {
        return filters.any { this.filter(it) }
    }

    fun filter(filter: DeviceFilter): Boolean {
        return filterMacAddress(filter.deviceAddress) && filterName(filter.deviceName)
    }

    private fun filterName(deviceName: String?): Boolean {
        if (deviceName == null) return true
        return this.name == deviceName
    }

    private fun filterMacAddress(address: String?): Boolean {
        if (address == null) return true
        return macAddress == address
    }
}

// This should be simpler
sealed class ConnectionState {
    object NotStarted : ConnectionState()
    object Disconnecting : ConnectionState()
    object Disconnected : ConnectionState()
    object Connected : ConnectionState()
    object NotSupported : ConnectionState()
    object Ready : ConnectionState()
    object Connecting : ConnectionState()
    class Failed(val message: String, val errorCode: Int) : ConnectionState()
}

// should be parallel with BluetoothDevice.bondState()
sealed class BondState {
    object NotBonded : BondState()
    object CreatingBond : BondState()
    object RemovingBond : BondState()
    object Bonded : BondState()
    object BondingFailed : BondState()
}

data class Characteristic(val uuid: UUID, val service: UUID, val types: List<Type>, val required: Boolean)

data class Change(val characteristic: Characteristic, val data: Data)
enum class Type {
    WRITE,
    NOTIFY,
    READ
}
typealias CharacteristicMapper<T> = (Change) -> T
