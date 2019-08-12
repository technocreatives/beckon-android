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

class CreateBondFailedException(val macAddress: String, status: Int) : Throwable()
class RemoveBondFailedException(val macAddress: String, status: Int) : Throwable()

class DisconnectDeviceFailedException(val macAddress: String, status: Int) : Throwable()

class WriteDataException(val macAddress: String, uuid: UUID, status: Int) : Throwable()
class ReadDataException(val macAddress: String, uuid: UUID, status: Int) : Throwable()
class SubscribeDataException(val macAddress: String, uuid: UUID, status: Int) : Throwable()

sealed class BeckonError {
    data class DeviceNotFoundException(val macAddress: String) : BeckonError()
    data class ConnectFailedException(val macAddress: String, val status: Int) : BeckonError()
    object BluetoothGattNull : BeckonError()
}

typealias BeckonResult<T> = Either<Throwable, T>