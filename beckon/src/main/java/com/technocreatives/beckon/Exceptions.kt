package com.technocreatives.beckon

import arrow.core.Either

class ScanFailedException(val errorCode: Int, message: String? = null) : Exception(message)

class CharacteristicFailedException(message: String) : Exception(message)
object CharacteristicNotFoundException : Exception()
object NotAWriteCharacteristicException : Exception()
object NotAReadCharacteristicException : Exception()
object NotANotifyCharacteristicException : Exception()

class DeviceNotFoundException(val macAddress: String) : Exception()
class BluetoothDeviceNotFoundException(val macAddress: String) : Exception()

class CreateBondFailedException(val macAddress: String, status: Int) : Exception()
class RemoveBondFailedException(val macAddress: String, status: Int) : Exception()

class DisconnectDeviceFailedException(val macAddress: String, status: Int) : Exception()

class WriteDataException(val macAddress: String, status: Int, characteristic: Characteristic) : Exception()
class ReadDataEXception(val macAddress: String, status: Int, characteristic: Characteristic) : Exception()

sealed class BeckonError {
    data class DeviceNotFoundException(val macAddress: String) : BeckonError()
}

typealias BeckonResult<T> = Either<BeckonError, T>