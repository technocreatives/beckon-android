package com.technocreatives.beckon.mesh.data.serializer


import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(forClass = Int::class)
object NodeSecuritySerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("NodeSecurity", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Int) {
        // nodeJson.addProperty("security", (node.getSecurity() == ProvisionedBaseMeshNode.HIGH) ? "secure" : "insecure");
        encoder.encodeString(if (value == 1) "secure" else "insecure")
    }

    override fun deserialize(decoder: Decoder): Int {
        return if (decoder.decodeString() == "secure") 1 else 0
    }
}
