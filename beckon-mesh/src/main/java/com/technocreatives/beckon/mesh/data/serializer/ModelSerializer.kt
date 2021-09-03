package com.technocreatives.beckon.mesh.data.serializer


import com.technocreatives.beckon.mesh.data.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nordicsemi.android.mesh.models.SigModelParser
import no.nordicsemi.android.mesh.models.VendorModel
import no.nordicsemi.android.mesh.utils.MeshParserUtils
import java.util.*

@Serializer(forClass = Model::class)
object ModelSerializer : KSerializer<Model> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Model", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Model) {
        return encoder.encodeSerializableValue(ModelData.serializer(), value.toSerialization())
    }

    override fun deserialize(decoder: Decoder): Model {
        val data = decoder.decodeSerializableValue(ModelData.serializer())
        return data.toModel()
    }
}
