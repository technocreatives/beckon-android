package com.technocreatives.beckon

import android.bluetooth.BluetoothGattCharacteristic
import arrow.core.Either
import arrow.core.left
import arrow.core.rightIfNotNull
import arrow.core.toOption
import com.squareup.moshi.JsonClass
import com.technocreatives.beckon.util.parallelValidate
import java.util.UUID

data class Metadata(
    val macAddress: MacAddress,
    val name: String, // BluetoothDevice name
    val services: List<UUID>,
    val characteristics: List<FoundCharacteristic>,
    val descriptor: Descriptor
) {

    fun savedMetadata(): SavedMetadata {
        return SavedMetadata(
            macAddress,
            name,
            descriptor
        )
    }

    inline fun <reified T : FoundCharacteristic> findCharacteristic(characteristic: Characteristic): Either<RequirementFailed, T> {
        return if (characteristic.service !in services) {
            ServiceNotFound(characteristic).left()
        } else {
            val foundCharacteristics = characteristics
                .filter { it.toCharacteristic() == characteristic }
            if (foundCharacteristics.isEmpty()) {
                CharacteristicNotFound(characteristic).left()
            } else {
                foundCharacteristics.filterIsInstance(T::class.java)
                    .firstOrNull().rightIfNotNull { PropertyNotSupport(characteristic) }
            }
        }
    }
}

data class DeviceDetail(
    val services: List<UUID>,
    val characteristics: List<FoundCharacteristic>
)

fun checkRequirement(
    requirement: Requirement,
    services: List<UUID>,
    characteristics: List<FoundCharacteristic>
): Either<CharacteristicFailed, FoundCharacteristic> {
    return when {
        requirement.service !in services -> CharacteristicFailed.ServiceNotFound(requirement).left()
        requirement.uuid !in characteristics.map { it.id } -> CharacteristicFailed.CharacteristicNotFound(
            requirement
        ).left()
        else -> characteristics.findCharacteristic(requirement)
    }
}

fun checkRequirements(
    requirements: List<Requirement>,
    services: List<UUID>,
    characteristics: List<FoundCharacteristic>
): Either<ConnectionError.RequirementFailed, List<FoundCharacteristic>> {
    return requirements
        .map { checkRequirement(it, services, characteristics).toValidated() }
        .parallelValidate()
        .mapLeft { ConnectionError.RequirementFailed(it) }
        .toEither()
}

fun checkNotify(
    characteristic: Characteristic,
    services: List<UUID>,
    characteristics: List<FoundCharacteristic>
): Either<CharacteristicFailed, FoundCharacteristic.Notify> {
    return checkRequirement(
        characteristic.toRequirement(Property.NOTIFY),
        services,
        characteristics
    )
        .map { it as FoundCharacteristic.Notify }
}

fun checkNotifyList(
    characteristics: List<Characteristic>,
    services: List<UUID>,
    details: List<FoundCharacteristic>
): Either<ConnectionError.RequirementFailed, List<FoundCharacteristic.Notify>> {
    return checkRequirements(
        characteristics.map { it.toRequirement(Property.NOTIFY) },
        services,
        details
    )
        .map { it.map { it as FoundCharacteristic.Notify } }
}

fun checkReadList(
    characteristics: List<Characteristic>,
    services: List<UUID>,
    details: List<FoundCharacteristic>
): Either<ConnectionError.RequirementFailed, List<FoundCharacteristic.Read>> {
    return checkRequirements(
        characteristics.map { it.toRequirement(Property.READ) },
        services,
        details
    )
        .map { it.map { it as FoundCharacteristic.Read } }
}

fun List<FoundCharacteristic>.findCharacteristic(requirement: Requirement): Either<CharacteristicFailed, FoundCharacteristic> {
    return find { it.toRequirement() == requirement }
        .toOption()
        .toEither { requirement.toFailed() }
}

fun List<FoundCharacteristic>.findReadCharacteristic(requirement: Requirement): Either<CharacteristicFailed, FoundCharacteristic.Read> {
    return this.filterIsInstance(FoundCharacteristic.Read::class.java)
        .find { it.toRequirement() == requirement }
        .toOption()
        .toEither { requirement.toFailed() }
}

fun List<FoundCharacteristic>.findWriteCharacteristic(requirement: Requirement): Either<CharacteristicFailed, FoundCharacteristic.Write> {
    return this.filterIsInstance(FoundCharacteristic.Write::class.java)
        .find { it.toRequirement() == requirement }
        .toOption()
        .toEither { requirement.toFailed() }
}

sealed class FoundCharacteristic {
    abstract val id: UUID
    abstract val service: UUID

    data class Notify(
        override val id: UUID,
        override val service: UUID,
        val gatt: BluetoothGattCharacteristic
    ) : FoundCharacteristic()

    data class Read(
        override val id: UUID,
        override val service: UUID,
        val gatt: BluetoothGattCharacteristic
    ) : FoundCharacteristic()

    data class Write(
        override val id: UUID,
        override val service: UUID,
        val gatt: BluetoothGattCharacteristic
    ) : FoundCharacteristic()

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

    fun toCharacteristic(): Characteristic {
        return Characteristic(id, service)
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
