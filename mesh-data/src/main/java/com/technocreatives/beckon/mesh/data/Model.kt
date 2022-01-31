package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

@Serializable(with = ModelSerializer::class)
sealed interface Model {
    val modelId: ModelId
    val bind: List<AppKeyIndex>
    val subscribe: List<SubscriptionAddress>
    val publish: Publish?
    fun toSerialization() = ModelData(
        modelId, bind, subscribe, publish
    )
}

data class VendorModel(
    override val modelId: ModelId,
    override val bind: List<AppKeyIndex> = emptyList(),
    override val subscribe: List<SubscriptionAddress> = emptyList(),
    override val publish: Publish? = null
) : Model {

    val buffer by lazy {
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(modelId.value)
        }
    }

    val name get() = "Vendor Model"
}

data class SigModel(
    override val modelId: ModelId,
    override val bind: List<AppKeyIndex> = emptyList(),
    override val subscribe: List<SubscriptionAddress> = emptyList(),
    override val publish: Publish? = null
) : Model

@Serializable
data class ModelData(
    val modelId: ModelId,
    val bind: List<AppKeyIndex> = emptyList(),
    val subscribe: List<SubscriptionAddress> = emptyList(),
    val publish: Publish? = null
) {
    private fun isVendorModel() = modelId.value < Short.MIN_VALUE || modelId.value > Short.MAX_VALUE

    fun toModel() = if (isVendorModel()) {
        toVendorModel()
    } else {
        toSigModel()
    }

    private fun toSigModel() = SigModel(
        modelId, bind, subscribe, publish
    )

    private fun toVendorModel() = VendorModel(
        modelId, bind, subscribe, publish
    )
}

fun List<AppKeyIndex>.toAppKeys(allKeys: List<AppKey>) =
    mapNotNull { index -> allKeys.find { it.index == index } }

@Serializable(with = ModelIdSerializer::class)
@JvmInline
value class ModelId(val value: Int) {
    fun isVendorModel() = value < Short.MIN_VALUE || value > Short.MAX_VALUE
    fun format() = if (isVendorModel()) {
        String.format(Locale.US, "%08X", value)
    } else {
        String.format(Locale.US, "%04X", value)
    }
}

@Serializable
data class Publish(
    val address: GroupAddress, // TODO Addressable
    val index: AppKeyIndex,
    val period: Period,
    @SerialName("credentials")
    @Serializable(with = IntToBooleanSerializer::class)
    val credentialsFlag: Boolean,
    val ttl: Int,
    val retransmit: Retransmit
)

@Serializable
data class Retransmit(
    val count: Int,
    val interval: Int
)

@Serializable
data class Period(
    val numberOfSteps: Int,
    val resolution: Int,
)


