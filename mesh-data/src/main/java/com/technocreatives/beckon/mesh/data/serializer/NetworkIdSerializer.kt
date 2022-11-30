package com.technocreatives.beckon.mesh.data.serializer


import com.technocreatives.beckon.mesh.data.AddressValue
import com.technocreatives.beckon.mesh.data.Key
import com.technocreatives.beckon.mesh.data.NetworkId
import com.technocreatives.beckon.mesh.data.util.hexStringToByteArray
import com.technocreatives.beckon.mesh.data.util.toHex
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@ExperimentalSerializationApi
@Serializer(forClass = AddressValue::class)
object NetworkIdSerializer : KSerializer<NetworkId> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Key", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NetworkId) {
        encoder.encodeString(value.value.toHex())
    }

    override fun deserialize(decoder: Decoder): NetworkId {
        return NetworkId(decoder.decodeString().hexStringToByteArray())
    }
}
