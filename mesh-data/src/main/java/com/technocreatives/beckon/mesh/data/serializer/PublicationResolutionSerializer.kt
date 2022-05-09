package com.technocreatives.beckon.mesh.data.serializer

import com.technocreatives.beckon.mesh.data.PublicationResolution
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@ExperimentalSerializationApi
@Serializer(forClass = PublicationResolution::class)
object PublicationResolutionSerializer : KSerializer<PublicationResolution> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("publishResolution", PrimitiveKind.INT)

    private const val RESOLUTION_100MS = 100
    private const val RESOLUTION_1S = 1000
    private const val RESOLUTION_10S = 10 * 1000
    private const val RESOLUTION_10M = 10 * 60 * 1000

    override fun serialize(encoder: Encoder, value: PublicationResolution) {
        val serializedValue = when (value) {
            PublicationResolution.RESOLUTION_100MS -> RESOLUTION_100MS
            PublicationResolution.RESOLUTION_1S -> RESOLUTION_1S
            PublicationResolution.RESOLUTION_10S -> RESOLUTION_10S
            PublicationResolution.RESOLUTION_10M -> RESOLUTION_10M
        }
        encoder.encodeInt(serializedValue)
    }

    override fun deserialize(decoder: Decoder): PublicationResolution {
        return when (decoder.decodeInt()) {
            RESOLUTION_1S -> PublicationResolution.RESOLUTION_1S
            RESOLUTION_10S -> PublicationResolution.RESOLUTION_10S
            RESOLUTION_10M -> PublicationResolution.RESOLUTION_10M
            else -> PublicationResolution.RESOLUTION_100MS
        }
    }
}
