package com.technocreatives.beckon.mesh.data.serializer

import com.technocreatives.beckon.mesh.data.NetworkTransmit
import com.technocreatives.beckon.mesh.data.TransmitData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

@Serializer(forClass = NetworkTransmit::class)
object NetworkTransmitSerializer : KSerializer<NetworkTransmit?> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("offsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NetworkTransmit?) {
        if (value != null) {
            encoder.encodeSerializableValue(TransmitData.serializer(), value.toData())
        }
    }

    override fun deserialize(decoder: Decoder): NetworkTransmit? {
        val data = decoder.decodeSerializableValue(TransmitData.serializer())
        return data.toNetworkTransmit()
    }
}
