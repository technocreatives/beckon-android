package com.technocreatives.beckon

import java.util.*

@Deprecated("Use BleActionError")
data class WriteDataException(val macAddress: String, val uuid: UUID, val status: Int) :
    Throwable(), BeckonError

@Deprecated("Use BleActionError")
data class ReadDataException(val macAddress: String, val uuid: UUID, val status: Int) :
    Throwable(),
    BeckonError

@Deprecated("Use BleActionError")
data class SubscribeDataException(val macAddress: String, val uuid: UUID, val status: Int) :
    Throwable(), BeckonError

sealed interface BeckonActionError: BeckonError

data class BleActionError(
    val macAddress: String,
    val uuid: UUID,
    val status: Int,
    val property: Property
) : BeckonActionError

data class MtuRequestError(val macAddress: String, val status: Int) :  BeckonError

object BeckonTimeOutError : BeckonError

sealed interface RequirementFailed : BeckonActionError
data class CharacteristicNotFound(val characteristic: Characteristic) : RequirementFailed
data class ServiceNotFound(val characteristic: Characteristic) : RequirementFailed
data class PropertyNotSupport(val characteristic: Characteristic) : RequirementFailed

// todo use BeckonException instead of Throwable
//typealias BeckonResult<T> = Either<Throwable, T>

sealed interface BeckonError {
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
    data class Timeout(val macAddress: MacAddress) : ConnectionError()

    data class RequirementFailed(val fails: List<CharacteristicFailed>) : ConnectionError()
    data class ConnectedDeviceNotFound(val macAddress: MacAddress) : ConnectionError()
    data class DisconnectDeviceFailed(val macAddress: String, val status: Int) : ConnectionError()

    data class GeneralError(val macAddress: MacAddress, val throwable: Throwable) :
        ConnectionError()
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
