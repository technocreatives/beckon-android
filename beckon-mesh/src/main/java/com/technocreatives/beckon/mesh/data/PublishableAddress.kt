package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.utils.MeshAddress
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nordicsemi.android.mesh.utils.AddressArray
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Serializable(with = PublishableAddressSerializer::class)
sealed interface PublishableAddress {
    companion object {
        fun from(value: Int): PublishableAddress =
            when {
                MeshAddress.isValidGroupAddress(value) -> {
                    GroupAddress(value)
                }
                MeshAddress.isValidUnicastAddress(value) -> {
                    UnicastAddress(value)
                }
                else -> {
                    throw IllegalArgumentException("$value is not a valid PublishableAddress")
                }
            }

        fun from(bytes: ByteArray): PublishableAddress {
            val value = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
            return when {
                MeshAddress.isValidGroupAddress(value) -> GroupAddress(value)
                MeshAddress.isValidUnicastAddress(value) -> UnicastAddress(value)
                else -> throw IllegalArgumentException("Not supported $this")
            }
        }
    }
}

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

@Serializer(forClass = Model::class)
object PublishableAddressSerializer : KSerializer<PublishableAddress> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("PublishableAddress", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: PublishableAddress) {
        return encoder.encodeInt(value.value())
    }

    override fun deserialize(decoder: Decoder): PublishableAddress {
        val value = decoder.decodeInt()
        return PublishableAddress.from(value)
    }
}
