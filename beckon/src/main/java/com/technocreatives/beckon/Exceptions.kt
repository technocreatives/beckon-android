package com.technocreatives.beckon

import arrow.core.Either
import java.util.UUID

class ScanFailedException(val errorCode: Int) : Throwable()
class StopScannerFailedException(val errorCode: Int) : Throwable()
class CharacteristicFailedException(message: String) : Throwable(message)
object BluetoothGattNullException : Throwable()
class RequirementFailedException(val fails: List<CharacteristicFailed>) : Throwable()
object CharacteristicNotFoundException : Throwable()
object NotAWriteCharacteristicException : Throwable()
object NotAReadCharacteristicException : Throwable()
object NotANotifyCharacteristicException : Throwable()
class ConnectFailedException(val macAddress: String, status: Int) : Throwable()
class DeviceNotFoundException(val macAddress: String) : Throwable()
class BluetoothDeviceNotFoundException(val macAddress: String) : Throwable()
class DisconnectDeviceFailedException(val macAddress: String, status: Int) : Throwable()

class CreateBondFailedException(val macAddress: String, status: Int) : Throwable()
class RemoveBondFailedException(val macAddress: String, status: Int) : Throwable()

data class WriteDataException(val macAddress: String, val uuid: UUID, val status: Int) : Throwable()
data class ReadDataException(val macAddress: String, val uuid: UUID, val status: Int) : Throwable()
data class SubscribeDataException(val macAddress: String, val uuid: UUID, val status: Int) : Throwable()

typealias BeckonResult<T> = Either<Throwable, T>

interface BeckonError {
    fun toException(): BeckonException {
        return BeckonException(this)
    }
}

data class BeckonException(val beckonError: BeckonError) : Throwable()
data class GeneralError(val throwable: Throwable) : BeckonError

sealed class ScanError : BeckonError {
    data class ScanFailed(val errorCode: Int) : ScanError()
}

sealed class ConnectionError : BeckonError {
    data class ConnectFailed(val macAddress: MacAddress, val status: Int) : ConnectionError()
    data class BluetoothGattNull(val macAddress: MacAddress) : ConnectionError()
    data class RequirementFailed(val fails: List<CharacteristicFailed>) : ConnectionError()
    data class ConnectedDeviceNotFound(val macAddress: MacAddress) : ConnectionError()
    data class DisconnectDeviceFailed(val macAddress: String, val status: Int) : ConnectionError()
    data class CreateBondFailed(val macAddress: String, val status: Int) : ConnectionError()
    data class RemoveBondFailed(val macAddress: String, val status: Int) : ConnectionError()
}

sealed class BeckonDeviceError : BeckonError {
    data class SavedDeviceNotFound(val address: MacAddress) : BeckonDeviceError()
    data class BondedDeviceNotFound(val savedMetadata: SavedMetadata) : BeckonDeviceError()
    data class ConnectedDeviceNotFound(val savedMetadata: SavedMetadata) : BeckonDeviceError()

    fun macAddress(): MacAddress {
        return when (this) {
            is SavedDeviceNotFound -> address
            is BondedDeviceNotFound -> savedMetadata.macAddress
            is ConnectedDeviceNotFound -> savedMetadata.macAddress
        }
    }
}
