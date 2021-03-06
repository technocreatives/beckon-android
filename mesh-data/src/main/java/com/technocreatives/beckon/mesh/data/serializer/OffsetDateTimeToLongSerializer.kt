package com.technocreatives.beckon.mesh.data.serializer

import kotlinx.serialization.ExperimentalSerializationApi
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

@ExperimentalSerializationApi
@Serializer(forClass = Long::class)
object OffsetDateTimeToLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("offsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        val date = OffsetDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.of("Z"))
        encoder.encodeString(date.toString())
    }

    override fun deserialize(decoder: Decoder): Long {
        return OffsetDateTime.parse(decoder.decodeString()).toInstant().toEpochMilli()
    }
}
