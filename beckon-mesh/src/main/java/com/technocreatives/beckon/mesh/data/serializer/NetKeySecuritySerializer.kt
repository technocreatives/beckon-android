package com.technocreatives.beckon.mesh.data.serializer


import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(forClass = Boolean::class)
object NetKeySecuritySerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("NetKeySecurity", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeString(if (value) "secure" else "insecure")
    }

    override fun deserialize(decoder: Decoder): Boolean {
        return decoder.decodeString() == "secure"
    }
}
