package com.technocreatives.beckon.mesh.data.serializer


import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.data.SubscriptionAddress
import com.technocreatives.beckon.mesh.data.VirtualAddress
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nordicsemi.android.mesh.utils.MeshParserUtils
import java.util.*

@Serializer(forClass = SubscriptionAddress::class)
object SubscriptionAddressSerializer : KSerializer<SubscriptionAddress> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("SubscriptionAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SubscriptionAddress) {
        return when (value) {
            is GroupAddress -> HexToIntSerializer.serialize(encoder, value.value)
            is VirtualAddress -> encoder.encodeString(value.value.toString().filter { it != '-' })
        }
    }

    override fun deserialize(decoder: Decoder): SubscriptionAddress {
        val hexAddress = decoder.decodeString()
        return if (hexAddress.length == 32) {
            VirtualAddress(UUID.fromString(MeshParserUtils.formatUuid(hexAddress)))
        } else {
            GroupAddress(
                hexAddress.toInt(16)
            )
        }
    }
}
