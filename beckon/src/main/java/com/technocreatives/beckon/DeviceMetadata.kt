package com.technocreatives.beckon

import android.bluetooth.BluetoothGattCharacteristic
import arrow.core.Either
import arrow.core.left
import arrow.core.toOption
import com.technocreatives.beckon.util.parallelValidate
import com.technocreatives.beckon.util.toValidated
import java.util.UUID

data class DeviceMetadata(
    val macAddress: MacAddress,
    val name: String, // BluetoothDevice name
    val services: List<UUID>,
    val characteristics: List<CharacteristicSuccess>,
    val descriptor: Descriptor
) {

    fun writableDeviceMetadata(): WritableDeviceMetadata {
        return WritableDeviceMetadata(
            macAddress,
            name,
            descriptor
        )
    }

    fun findCharacteristic(requirement: Requirement): Either<CharacteristicFailed, CharacteristicSuccess> {
        return characteristics.findCharacteristic(requirement)
    }
}

data class DeviceDetail(
    val services: List<UUID>,
    val characteristics: List<CharacteristicSuccess>
)

internal fun checkRequirement(
    requirement: Requirement,
    services: List<UUID>,
    characteristics: List<CharacteristicSuccess>
): Either<CharacteristicFailed, CharacteristicSuccess> {
    return when {
        requirement.service !in services -> CharacteristicFailed.ServiceNotFound(requirement).left()
        requirement.uuid !in characteristics.map { it.id } -> CharacteristicFailed.UUIDNotFound(requirement).left()
        else -> characteristics.findCharacteristic(requirement)
    }
}

internal fun checkRequirements(
    requirements: List<Requirement>,
    services: List<UUID>,
    characteristics: List<CharacteristicSuccess>
): Either<RequirementFailedException, List<CharacteristicSuccess>> {
    return requirements
        .map { checkRequirement(it, services, characteristics).toValidated() }
        .parallelValidate()
        .leftMap { RequirementFailedException(it.all) }
        .toEither()
}

internal fun checkNotify(
    characteristic: Characteristic,
    services: List<UUID>,
    characteristics: List<CharacteristicSuccess>
): Either<CharacteristicFailed, CharacteristicSuccess.Notify> {
    return checkRequirement(characteristic.toRequirement(Type.NOTIFY), services, characteristics)
        .map { it as CharacteristicSuccess.Notify }
}

internal fun checkNotifyList(
    characteristics: List<Characteristic>,
    services: List<UUID>,
    details: List<CharacteristicSuccess>
): Either<RequirementFailedException, List<CharacteristicSuccess.Notify>> {
    return checkRequirements(characteristics.map { it.toRequirement(Type.NOTIFY) }, services, details)
        .map { it.map { it as CharacteristicSuccess.Notify } }
}

internal fun List<CharacteristicSuccess>.findCharacteristic(requirement: Requirement): Either<CharacteristicFailed, CharacteristicSuccess> {
    return find { it.toRequirement() == requirement }
        .toOption()
        .toEither { requirement.toTypeFailed() }
}

sealed class CharacteristicSuccess(val id: UUID, val service: UUID, val gatt: BluetoothGattCharacteristic) {
    class Notify(id: UUID, service: UUID, gatt: BluetoothGattCharacteristic) : CharacteristicSuccess(id, service, gatt)
    class Read(id: UUID, service: UUID, gatt: BluetoothGattCharacteristic) : CharacteristicSuccess(id, service, gatt)
    class Write(id: UUID, service: UUID, gatt: BluetoothGattCharacteristic) : CharacteristicSuccess(id, service, gatt)

    fun getType(): Type {
        return when (this) {
            is Notify -> Type.NOTIFY
            is Read -> Type.READ
            is Write -> Type.WRITE
        }
    }

    fun toRequirement(): Requirement {
        return Requirement(id, service, getType())
    }
}

sealed class CharacteristicFailed(val requirement: Requirement) {
    class NotSupportWrite(requirement: Requirement) : CharacteristicFailed(requirement)
    class NotSupportRead(requirement: Requirement) : CharacteristicFailed(requirement)
    class NotSupportNotify(requirement: Requirement) : CharacteristicFailed(requirement)
    class ServiceNotFound(requirement: Requirement) : CharacteristicFailed(requirement)
    class UUIDNotFound(requirement: Requirement) : CharacteristicFailed(requirement)
}

fun Requirement.toTypeFailed(): CharacteristicFailed {
    return when (type) {
        Type.READ -> CharacteristicFailed.NotSupportRead(this)
        Type.NOTIFY -> CharacteristicFailed.NotSupportNotify(this)
        Type.WRITE -> CharacteristicFailed.NotSupportWrite(this)
    }
}

data class WritableDeviceMetadata(
    val macAddress: MacAddress,
    val name: String,
    val descriptor: Descriptor
)
