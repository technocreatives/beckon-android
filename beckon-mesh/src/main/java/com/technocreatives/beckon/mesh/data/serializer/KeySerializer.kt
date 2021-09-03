package com.technocreatives.beckon.mesh.data.serializer


import com.technocreatives.beckon.mesh.data.AddressValue
import com.technocreatives.beckon.mesh.data.Key
import com.technocreatives.beckon.mesh.hexStringToByteArray
import com.technocreatives.beckon.mesh.toHex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(forClass = AddressValue::class)
object KeySerializer : KSerializer<Key> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Key", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Key) {
        encoder.encodeString(value.value.toHex())
    }

    override fun deserialize(decoder: Decoder): Key {
        return Key(decoder.decodeString().hexStringToByteArray())
    }
}
