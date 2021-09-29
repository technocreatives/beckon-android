package com.technocreatives.beckon.mesh.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nordicsemi.android.mesh.utils.AddressArray
import no.nordicsemi.android.mesh.utils.MeshAddress
import java.lang.IllegalArgumentException

@Serializable(with = PublishableAddressSerializer::class)
sealed interface PublishableAddress

fun PublishableAddress.value(): Int = when (this) {
    is GroupAddress -> value
    is UnicastAddress -> value
}

internal fun PublishableAddress.toAddressArray(): AddressArray {
    val intAddress = value()
    val b1 = intAddress.shr(8).toByte()
    val b2 = intAddress.toByte()
    return AddressArray(b1, b2)
}

private fun Int.toPublishableAddress(): PublishableAddress =
    when {
        MeshAddress.isValidGroupAddress(this) -> {
            GroupAddress(this)
        }
        MeshAddress.isValidUnicastAddress(this) -> {
            UnicastAddress(this)
        }
        else -> {
            throw IllegalArgumentException("$this is not a valid PublishableAddress")
        }
    }

@Serializer(forClass = Model::class)
object PublishableAddressSerializer : KSerializer<PublishableAddress> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("PublishableAddress", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: PublishableAddress) {
        return encoder.encodeInt(value.value())
    }

    override fun deserialize(decoder: Decoder): PublishableAddress {
        val value = decoder.decodeInt()
        return value.toPublishableAddress()
    }
}
