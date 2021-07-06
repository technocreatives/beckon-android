package com.technocreatives.beckon

import arrow.core.Either
import java.util.UUID

sealed interface WriteDataError
sealed interface WriteRequirementFailed : WriteDataError
data class WriteDataException(val macAddress: String, val uuid: UUID, val status: Int) : Throwable(), BeckonError, WriteDataError
data class NotSupportWrite(val characteristic: Characteristic) : WriteRequirementFailed

sealed interface SubscribeDataError
sealed interface SubscribeRequirementFailed: SubscribeDataError
data class SubscribeDataException(val macAddress: String, val uuid: UUID, val status: Int) : Throwable(), BeckonError, SubscribeDataError
data class NotSupportSubscribe(val characteristic: Characteristic) : SubscribeRequirementFailed

data class CharacteristicNotFound(val characteristic: Characteristic): WriteRequirementFailed, SubscribeRequirementFailed, ReadRequirementFailed
data class ServiceNotFound(val characteristic: Characteristic) : WriteRequirementFailed, SubscribeRequirementFailed, ReadRequirementFailed

sealed interface ReadDataError
sealed interface ReadRequirementFailed : ReadDataError
data class ReadDataException(val macAddress: String, val uuid: UUID, val status: Int) : Throwable(), BeckonError, ReadDataError
data class NotSupportRead(val characteristic: Characteristic) : ReadRequirementFailed

data class MtuRequestError(val macAddress: String, val status: Int) : BeckonError

// todo use BeckonException instead of Throwable
typealias BeckonResult<T> = Either<Throwable, T>

interface BeckonError {
    fun toException(): BeckonException {
        return BeckonException(this)
    }
}

data class BeckonException(val beckonError: BeckonError) : Throwable()

sealed class ScanError : BeckonError {
    data class ScanFailed(val errorCode: Int) : ScanError()
}

sealed class ConnectionError : BeckonError {
    data class BleConnectFailed(val macAddress: MacAddress, val status: Int) : ConnectionError()
    data class CreateBondFailed(val macAddress: String, val status: Int) : ConnectionError()
    data class RemoveBondFailed(val macAddress: String, val status: Int) : ConnectionError()
    data class BluetoothGattNull(val macAddress: MacAddress) : ConnectionError()

    data class RequirementFailed(val fails: List<CharacteristicFailed>) : ConnectionError()
    data class ConnectedDeviceNotFound(val macAddress: MacAddress) : ConnectionError()
    data class DisconnectDeviceFailed(val macAddress: String, val status: Int) : ConnectionError()

    data class GeneralError(val macAddress: MacAddress, val throwable: Throwable) : ConnectionError()
}

sealed class BeckonDeviceError : BeckonError {
    data class SavedDeviceNotFound(val address: MacAddress) : BeckonDeviceError()
    data class BondedDeviceNotFound(val savedMetadata: SavedMetadata) : BeckonDeviceError()
    data class ConnectedDeviceNotFound(val savedMetadata: SavedMetadata) : BeckonDeviceError()
    data class Connecting(val savedMetadata: SavedMetadata) : BeckonDeviceError()

    fun macAddress(): MacAddress {
        return when (this) {
            is SavedDeviceNotFound -> address
            is BondedDeviceNotFound -> savedMetadata.macAddress
            is ConnectedDeviceNotFound -> savedMetadata.macAddress
            is Connecting -> savedMetadata.macAddress
        }
    }
}
