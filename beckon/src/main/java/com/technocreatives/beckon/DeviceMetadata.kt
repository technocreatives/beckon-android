package com.technocreatives.beckon

import android.bluetooth.BluetoothGattCharacteristic
import arrow.core.Either
import java.util.UUID

data class DeviceMetadata(
    val macAddress: MacAddress,
    val name: String,
    val characteristics: List<CharacteristicDetail>
) {

    fun writableDeviceMetadata(): WritableDeviceMetadata {
        return WritableDeviceMetadata(
            macAddress,
            name,
            characteristics.map { it.characteristic() }.distinctBy { it.uuid })
    }

    fun success(): Boolean {
        return !characteristics.any { it is CharacteristicDetail.Failed && it.characteristic.required }
    }

    private fun findCharacteristic(uuid: UUID): Either<Throwable, List<CharacteristicDetail>> {
        val characteristics = this.characteristics.filter { it.characteristic().uuid == uuid }
        return if (characteristics.isEmpty()) {
            Either.left(CharacteristicNotFoundException)
        } else {
            Either.right(characteristics)
        }
    }

    fun findWriteCharacteristic(uuid: UUID): Either<Throwable, CharacteristicDetail.Write> {
        return findCharacteristic(uuid).map {
            val characteristic = it.find { it is CharacteristicDetail.Write }
            return if (characteristic == null) {
                Either.left(NotAWriteCharacteristicException)
            } else {
                Either.right(characteristic as CharacteristicDetail.Write)
            }
        }
    }

    fun findReadCharacteristic(uuid: UUID): Either<Throwable, CharacteristicDetail.Read> {
        return findCharacteristic(uuid).map {
            val characteristic = it.find { it is CharacteristicDetail.Read }
            return if (characteristic == null) {
                Either.left(NotAReadCharacteristicException)
            } else {
                Either.right(characteristic as CharacteristicDetail.Read)
            }
        }
    }
}

data class WritableDeviceMetadata(
    val macAddress: MacAddress,
    val name: String,
    val characteristics: List<Characteristic>
)

sealed class CharacteristicDetail {
    data class Notify(val characteristic: Characteristic, val gatt: BluetoothGattCharacteristic) :
        CharacteristicDetail()

    data class Write(val characteristic: Characteristic, val gatt: BluetoothGattCharacteristic) : CharacteristicDetail()

    data class Read(val characteristic: Characteristic, val gatt: BluetoothGattCharacteristic) : CharacteristicDetail()

    data class Failed(val characteristic: Characteristic, val reason: CharacteristicFailedException) :
        CharacteristicDetail()

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