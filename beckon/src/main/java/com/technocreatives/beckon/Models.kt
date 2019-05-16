package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.UUID

typealias MacAddress = String

data class DeviceChange(val address: String, val change: Change)

data class Change(val info: DeviceMetadata, val characteristic: Characteristic, val data: Data)

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

sealed class BondState {
    object NotStarted : BondState()
    object Bonding : BondState()
    object RemovingBond : BondState()
    object Bonded : BondState()
    object BondingFailed : BondState()
}

data class ScannerSetting(
    val settings: ScanSettings,
    val filters: List<DeviceFilter>
)

data class Characteristic(val uuid: UUID, val service: UUID, val types: List<Type>, val required: Boolean)

enum class Type {
    //    WRITE, // don't support
//    READ, // don't support
    NOTIFY // read & notify
}

class ScanFailureException(val errorCode: Int, message: String? = null) : Exception(message)

typealias CharacteristicMapper<T> = (Change) -> T

data class DeviceMetadata(val macAddress: MacAddress, val name: String, val characteristics: List<Characteristic>)

sealed class CharacteristicResult {
    class Success(val characteristic: Characteristic, val gatt: BluetoothGattCharacteristic) : CharacteristicResult()
    class Failed(val characteristic: Characteristic, val reason: CharacteristicFailureException) :
            CharacteristicResult()
}

class CharacteristicFailureException(message: String) : Exception(message)
class DeviceNotFoundException(val macAddress: String) : Exception()
class BluetoothDeviceNotFoundException(val macAddress: String) : Exception()
class BondFailureException(val macAddress: String) : Exception()

data class DeviceFilter(
    val deviceName: String?,
    val deviceAddress: String?
)

data class Descriptor(
    val setting: ScannerSetting,
    val characteristics: List<Characteristic>
)

sealed class DiscoveredDevice {
    class SuccessDevice(val info: DeviceMetadata, val results: List<CharacteristicResult>) : DiscoveredDevice()
    class FailureDevice(val info: DeviceMetadata, val results: List<CharacteristicResult>) : DiscoveredDevice()

    override fun toString(): String {
        return when (this) {
            is SuccessDevice -> "Success Device $info $results"
            is FailureDevice -> "Failure Device $info $results"
        }
    }

    fun deviceInfo(): DeviceMetadata {
        return when (this) {
            is SuccessDevice -> info
            is FailureDevice -> info
        }
    }
}
