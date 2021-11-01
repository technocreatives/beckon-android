package com.technocreatives.beckon.mesh.data.serializer

import com.technocreatives.beckon.mesh.data.RelayRetransmit
import com.technocreatives.beckon.mesh.data.TransmitData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@ExperimentalSerializationApi
@Serializer(forClass = RelayRetransmit::class)
object RelayRetransmitSerializer : KSerializer<RelayRetransmit?> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("offsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RelayRetransmit?) {
        if (value != null) {
            encoder.encodeSerializableValue(TransmitData.serializer(), value.toData())
        }
    }

    override fun deserialize(decoder: Decoder): RelayRetransmit? {
        val data = decoder.decodeSerializableValue(TransmitData.serializer())
        return data.toRelayRetransmit()
    }
}
