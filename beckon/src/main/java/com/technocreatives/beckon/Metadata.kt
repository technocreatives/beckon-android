package com.technocreatives.beckon

import android.bluetooth.BluetoothGattCharacteristic
import arrow.core.Either
import arrow.core.left
import arrow.core.toOption
import com.squareup.moshi.JsonClass
import com.technocreatives.beckon.util.parallelValidate
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

    fun findReadCharacteristic(characteristic: Characteristic): Either<CharacteristicFailed, CharacteristicSuccess.Read> {
        return characteristics.findReadCharacteristic(characteristic.toRequirement(Property.READ))
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
        requirement.uuid !in characteristics.map { it.id } -> CharacteristicFailed.CharacteristicNotFound(
            requirement
        ).left()
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
        .mapLeft { ConnectionError.RequirementFailed(it) }
        .toEither()
}

internal fun checkNotify(
    characteristic: Characteristic,
    services: List<UUID>,
    characteristics: List<CharacteristicSuccess>
): Either<CharacteristicFailed, CharacteristicSuccess.Notify> {
    return checkRequirement(
        characteristic.toRequirement(Property.NOTIFY),
        services,
        characteristics
    )
        .map { it as CharacteristicSuccess.Notify }
}

internal fun checkNotifyList(
    characteristics: List<Characteristic>,
    services: List<UUID>,
    details: List<CharacteristicSuccess>
): Either<ConnectionError.RequirementFailed, List<CharacteristicSuccess.Notify>> {
    return checkRequirements(
        characteristics.map { it.toRequirement(Property.NOTIFY) },
        services,
        details
    )
        .map { it.map { it as CharacteristicSuccess.Notify } }
}

internal fun checkReadList(
    characteristics: List<Characteristic>,
    services: List<UUID>,
    details: List<CharacteristicSuccess>
): Either<ConnectionError.RequirementFailed, List<CharacteristicSuccess.Read>> {
    return checkRequirements(
        characteristics.map { it.toRequirement(Property.READ) },
        services,
        details
    )
        .map { it.map { it as CharacteristicSuccess.Read } }
}

internal fun List<CharacteristicSuccess>.findCharacteristic(requirement: Requirement): Either<CharacteristicFailed, CharacteristicSuccess> {
    return find { it.toRequirement() == requirement }
        .toOption()
        .toEither { requirement.toFailed() }
}

internal fun List<CharacteristicSuccess>.findReadCharacteristic(requirement: Requirement): Either<CharacteristicFailed, CharacteristicSuccess.Read> {
    return this.filterIsInstance(CharacteristicSuccess.Read::class.java)
        .find { it.toRequirement() == requirement }
        .toOption()
        .toEither { requirement.toFailed() }
}

sealed class CharacteristicSuccess {
    abstract val id: UUID
    abstract val service: UUID

    data class Notify(
        override val id: UUID,
        override val service: UUID,
        val gatt: BluetoothGattCharacteristic
    ) : CharacteristicSuccess()

    data class Read(
        override val id: UUID,
        override val service: UUID,
        val gatt: BluetoothGattCharacteristic
    ) : CharacteristicSuccess()

    data class Write(
        override val id: UUID,
        override val service: UUID,
        val gatt: BluetoothGattCharacteristic
    ) : CharacteristicSuccess()

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
}

sealed class CharacteristicFailed {
    data class NotSupportWrite(val requirement: Requirement) : CharacteristicFailed()
    data class NotSupportRead(val requirement: Requirement) : CharacteristicFailed()
    data class NotSupportNotify(val requirement: Requirement) : CharacteristicFailed()
    data class ServiceNotFound(val requirement: Requirement) : CharacteristicFailed()
    data class CharacteristicNotFound(val requirement: Requirement) : CharacteristicFailed()
}

fun Requirement.toFailed(): CharacteristicFailed {
    return when (property) {
        Property.READ -> CharacteristicFailed.NotSupportRead(this)
        Property.NOTIFY -> CharacteristicFailed.NotSupportNotify(this)
        Property.WRITE -> CharacteristicFailed.NotSupportWrite(this)
    }
}

@JsonClass(generateAdapter = true)
data class SavedMetadata(
    val macAddress: MacAddress,
    val name: String,
    val descriptor: Descriptor
)
