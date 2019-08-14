package com.technocreatives.beckon

import android.bluetooth.BluetoothGattCharacteristic
import arrow.core.Either
import arrow.core.left
import arrow.core.toOption
import com.technocreatives.beckon.util.parallelValidate
import com.technocreatives.beckon.util.toValidated
import java.util.UUID

data class Metadata(
    val macAddress: MacAddress,
    val name: String, // BluetoothDevice name
    val services: List<UUID>,
    val characteristics: List<CharacteristicSuccess>,
    val descriptor: Descriptor
) {

    fun savedMetadata(): SavedMetadata {
        return SavedMetadata(
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
): Either<ConnectionError.RequirementFailed, List<CharacteristicSuccess>> {
    return requirements
            .map { checkRequirement(it, services, characteristics).toValidated() }
            .parallelValidate()
            .leftMap { ConnectionError.RequirementFailed(it.all) }
            .toEither()
}

internal fun checkNotify(
    characteristic: Characteristic,
    services: List<UUID>,
    characteristics: List<CharacteristicSuccess>
): Either<CharacteristicFailed, CharacteristicSuccess.Notify> {
    return checkRequirement(characteristic.toRequirement(Property.NOTIFY), services, characteristics)
            .map { it as CharacteristicSuccess.Notify }
}

internal fun checkNotifyList(
    characteristics: List<Characteristic>,
    services: List<UUID>,
    details: List<CharacteristicSuccess>
): Either<ConnectionError.RequirementFailed, List<CharacteristicSuccess.Notify>> {
    return checkRequirements(characteristics.map { it.toRequirement(Property.NOTIFY) }, services, details)
            .map { it.map { it as CharacteristicSuccess.Notify } }
}

internal fun List<CharacteristicSuccess>.findCharacteristic(requirement: Requirement): Either<CharacteristicFailed, CharacteristicSuccess> {
    return find { it.toRequirement() == requirement }
            .toOption()
            .toEither { requirement.toFailed() }
}

sealed class CharacteristicSuccess(val id: UUID, val service: UUID, val gatt: BluetoothGattCharacteristic) {
    class Notify(id: UUID, service: UUID, gatt: BluetoothGattCharacteristic) : CharacteristicSuccess(id, service, gatt)
    class Read(id: UUID, service: UUID, gatt: BluetoothGattCharacteristic) : CharacteristicSuccess(id, service, gatt)
    class Write(id: UUID, service: UUID, gatt: BluetoothGattCharacteristic) : CharacteristicSuccess(id, service, gatt)

    private fun property(): Property {
        return when (this) {
            is Notify -> Property.NOTIFY
            is Read -> Property.READ
            is Write -> Property.WRITE
        }
    }

    fun toRequirement(): Requirement {
        return Requirement(id, service, property())
    }

    override fun toString(): String {
        val prefix = when (this) {
            is Notify -> "CharacteristicSuccess.Notify"
            is Read -> "CharacteristicSuccess.Read"
            is Write -> "CharacteristicSuccess.Write"
        }
        return "$prefix(id=$id, service=$service, gatt=$gatt)"
    }
}

sealed class CharacteristicFailed(val requirement: Requirement) {
    class NotSupportWrite(requirement: Requirement) : CharacteristicFailed(requirement)
    class NotSupportRead(requirement: Requirement) : CharacteristicFailed(requirement)
    class NotSupportNotify(requirement: Requirement) : CharacteristicFailed(requirement)
    class ServiceNotFound(requirement: Requirement) : CharacteristicFailed(requirement)
    class UUIDNotFound(requirement: Requirement) : CharacteristicFailed(requirement)
}

fun Requirement.toFailed(): CharacteristicFailed {
    return when (property) {
        Property.READ -> CharacteristicFailed.NotSupportRead(this)
        Property.NOTIFY -> CharacteristicFailed.NotSupportNotify(this)
        Property.WRITE -> CharacteristicFailed.NotSupportWrite(this)
    }
}

data class SavedMetadata(
    val macAddress: MacAddress,
    val name: String,
    val descriptor: Descriptor
)
