package com.technocreatives.beckon.mesh.data.serializer


import com.technocreatives.beckon.mesh.data.AddressValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(forClass = AddressValue::class)
object AddressSerializer : KSerializer<AddressValue> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Address", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AddressValue) {
        encoder.encodeString(String.format("%04X", value.value))
    }

    override fun deserialize(decoder: Decoder): AddressValue {
        val int = decoder.decodeString().toInt(16)
        return AddressValue(int)
    }
}