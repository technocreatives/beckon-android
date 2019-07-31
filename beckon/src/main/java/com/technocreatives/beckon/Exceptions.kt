package com.technocreatives.beckon

import arrow.core.Either
import java.util.UUID

class ScanFailedException(val errorCode: Int) : Exception()
class StopScannerFailedException(val errorCode: Int) : Exception()

class CharacteristicFailedException(message: String) : Exception(message)
object BluetoothGattNullException : Exception()
class RequirementFailedException(val fails: List<CharacteristicFailed>) : Exception()
object CharacteristicNotFoundException : Exception()
object NotAWriteCharacteristicException : Exception()
object NotAReadCharacteristicException : Exception()
object NotANotifyCharacteristicException : Exception()
class ConnectFailedException(val macAddress: String, status: Int) : Exception()

class DeviceNotFoundException(val macAddress: String) : Exception()
class BluetoothDeviceNotFoundException(val macAddress: String) : Exception()

class CreateBondFailedException(val macAddress: String, status: Int) : Exception()
class RemoveBondFailedException(val macAddress: String, status: Int) : Exception()

class DisconnectDeviceFailedException(val macAddress: String, status: Int) : Exception()

class WriteDataException(val macAddress: String, uuid: UUID, status: Int) : Exception()
class ReadDataException(val macAddress: String, uuid: UUID, status: Int) : Exception()
class SubscribeDataException(val macAddress: String, uuid: UUID, status: Int) : Exception()

sealed class BeckonError {
    data class DeviceNotFoundException(val macAddress: String) : BeckonError()
    data class ConnectFailedException(val macAddress: String, val status: Int) : BeckonError()
    object BluetoothGattNull : BeckonError()
}

typealias BeckonResult<T> = Either<Throwable, T>