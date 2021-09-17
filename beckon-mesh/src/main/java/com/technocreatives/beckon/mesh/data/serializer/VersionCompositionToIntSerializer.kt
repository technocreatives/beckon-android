package com.technocreatives.beckon.mesh.data.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nordicsemi.android.mesh.utils.CompositionDataParser

@Serializer(forClass = Int::class)
object VersionCompositionToIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("VersionCompositionToInt", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Int) {
        val str = CompositionDataParser.formatCompanyIdentifier(value, false)
        encoder.encodeString(str)
    }

    override fun deserialize(decoder: Decoder): Int {
        return decoder.decodeString().toInt(16)
    }
}
