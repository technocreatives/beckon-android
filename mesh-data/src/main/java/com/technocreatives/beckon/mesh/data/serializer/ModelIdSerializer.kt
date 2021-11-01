package com.technocreatives.beckon.mesh.data.serializer


import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.util.MeshParserUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@ExperimentalSerializationApi
@Serializer(forClass = ModelId::class)
object ModelIdSerializer : KSerializer<ModelId> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("ModelId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ModelId) {
        encoder.encodeString(value.format())
    }

    override fun deserialize(decoder: Decoder): ModelId {
        val int = MeshParserUtils.hexToInt(decoder.decodeString())
        return ModelId(int)
    }
}