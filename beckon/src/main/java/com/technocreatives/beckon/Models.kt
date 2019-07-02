package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import arrow.core.Either
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.UUID

typealias MacAddress = String

data class Change(val characteristic: Characteristic, val data: Data)

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

data class ScannerSetting(
    val settings: ScanSettings,
    val filters: List<DeviceFilter>
)

data class Characteristic(val uuid: UUID, val service: UUID, val types: List<Type>, val required: Boolean)

enum class Type {
    WRITE,
    NOTIFY,
    READ
}

typealias CharacteristicMapper<T> = (Change) -> T

sealed class CharacteristicResult {
    data class Notify(val characteristic: Characteristic, val gatt: BluetoothGattCharacteristic) :
        CharacteristicResult()

    data class Write(val characteristic: Characteristic, val gatt: BluetoothGattCharacteristic) : CharacteristicResult()

    data class Read(val characteristic: Characteristic, val gatt: BluetoothGattCharacteristic) : CharacteristicResult()

    data class Failed(val characteristic: Characteristic, val reason: CharacteristicFailedException) :
        CharacteristicResult()

    fun isSuccess(): Boolean {
        return this !is Failed
    }

    fun characteristic(): Characteristic {
        return when (this) {
            is Notify -> characteristic
            is Write -> characteristic
            is Failed -> characteristic
            is Read -> characteristic
        }
    }
}

data class DeviceFilter(
    val deviceName: String?,
    val deviceAddress: String?
)

data class DeviceMetadata(
    val macAddress: MacAddress,
    val name: String,
    val characteristics: List<CharacteristicResult>
) {

    fun metadata(): WritableDeviceMetadata {
        return WritableDeviceMetadata(macAddress, name, characteristics.map { it.characteristic() })
    }

    fun success(): Boolean {
        return !characteristics.any { it is CharacteristicResult.Failed && it.characteristic.required }
    }

    fun findCharacteristic(uuid: UUID): Either<Throwable, List<CharacteristicResult>> {
        val characteristics = this.characteristics.filter { it.characteristic().uuid == uuid }
        return if (characteristics.isEmpty()) {
            Either.left(CharacteristicNotFoundException)
        } else {
            Either.right(characteristics)
        }
    }

    fun findWriteCharacteristic(uuid: UUID): Either<Throwable, CharacteristicResult.Write> {
        return findCharacteristic(uuid).map {
            val characteristic = it.find { it is CharacteristicResult.Write }
            return if (characteristic == null) {
                Either.left(NotAWriteCharacteristicException)
            } else {
                Either.right(characteristic as CharacteristicResult.Write)
            }
        }
    }

    fun findReadCharacteristic(uuid: UUID): Either<Throwable, CharacteristicResult.Read> {
        return findCharacteristic(uuid).map {
            val characteristic = it.find { it is CharacteristicResult.Read }
            return if (characteristic == null) {
                Either.left(NotAReadCharacteristicException)
            } else {
                Either.right(characteristic as CharacteristicResult.Read)
            }
        }
    }

    fun findNotifyCharacteristic(uuid: UUID): Either<Throwable, CharacteristicResult.Notify> {
        return findCharacteristic(uuid).map {
            val characteristic = it.find { it is CharacteristicResult.Notify }
            return if (characteristic == null) {
                Either.left(NotANotifyCharacteristicException)
            } else {
                Either.right(characteristic as CharacteristicResult.Notify)
            }
        }
    }
}

data class WritableDeviceMetadata(
    val macAddress: MacAddress,
    val name: String,
    val characteristics: List<Characteristic>
)
