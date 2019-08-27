package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import java.util.UUID
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanSettings

typealias MacAddress = String

data class DeviceFilter(
    val deviceName: String?,
    val deviceAddress: String?,
    val serviceUuid: String?
)

data class ScannerSetting(
    val settings: ScanSettings,
    val filters: List<DeviceFilter>
)

data class Requirement(val uuid: UUID, val service: UUID, val property: Property) // mandatory characteristic

data class Descriptor(val requirements: List<Requirement>, val subscribes: List<Characteristic>)

data class ScanResult(internal val device: BluetoothDevice, val rssi: Int) {
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

internal sealed class BleConnectionState {
    object NotStarted : BleConnectionState()
    object Disconnecting : BleConnectionState()
    object Disconnected : BleConnectionState()
    object Connected : BleConnectionState()
    object NotSupported : BleConnectionState()
    object Ready : BleConnectionState()
    object Connecting : BleConnectionState()
    class Failed(val message: String, val errorCode: Int) : BleConnectionState()
}

sealed class ConnectionState {
    object NotConnected : ConnectionState()
    object Disconnecting : ConnectionState()
    object Connected : ConnectionState()
    object Connecting : ConnectionState()

    override fun toString(): String {
        return when (this) {
            is NotConnected -> "ConnectionState.NotConnected"
            is Disconnecting -> "ConnectionState.Disconnecting"
            is Connected -> "ConnectionState.Connected"
            is Connecting -> "ConnectionState.Connecting"
        }
    }
}

// should be parallel with BluetoothDevice.bondState()
sealed class BondState {
    object NotBonded : BondState()
    object CreatingBond : BondState()
    object RemovingBond : BondState()
    object Bonded : BondState()

    override fun toString(): String {
        return when (this) {
            is NotBonded -> "BondState.NotBonded"
            is CreatingBond -> "BondState.CreatingBond"
            is RemovingBond -> "BondState.RemovingBond"
            is Bonded -> "BondState.Bonded"
        }
    }
}

data class Characteristic(val uuid: UUID, val service: UUID) {
    fun toRequirement(property: Property): Requirement {
        return Requirement(uuid, service, property)
    }
}

data class Change(val uuid: UUID, val data: Data)
typealias State = Map<UUID, Data>

enum class Property {
    WRITE,
    NOTIFY,
    READ
}

typealias CharacteristicMapper<T> = (Change) -> T

enum class BluetoothState {
    UNKNOWN,
    TURNING_ON,
    TURNING_OFF,
    ON,
    OFF;
}
