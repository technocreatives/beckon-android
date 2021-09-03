package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import com.technocreatives.beckon.mesh.data.serializer.ModelSerializer
import com.technocreatives.beckon.mesh.data.serializer.SubscriptionAddressSerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.models.SigModelParser
import no.nordicsemi.android.mesh.utils.CompanyIdentifiers
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

    private val buffer by lazy {
        val b = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        b.putInt(modelId.value)
        b
    }

    val companyIdentifier by lazy { buffer.getShort(0) }
    val companyName by lazy { CompanyIdentifiers.getCompanyName(companyIdentifier) }

    val name get() = "Vendor Model"
}

data class SigModel(
    override val modelId: ModelId,
    override val bind: List<AppKeyIndex> = emptyList(),
    override val subscribe: List<SubscriptionAddress> = emptyList(),
    override val publish: Publish? = null
) : Model {
    val name get() = SigModelParser.getSigModel(modelId.value).modelName
}

@Serializable
data class ModelData(
    val modelId: ModelId,
    val bind: List<AppKeyIndex> = emptyList(),
    val subscribe: List<SubscriptionAddress> = emptyList(),
    val publish: Publish? = null
) {

    fun toModel() = if (modelId.value < Short.MIN_VALUE || modelId.value > Short.MAX_VALUE) {
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

@Serializable
@JvmInline
value class ModelId(@Serializable(with = HexToIntSerializer::class) val value: Int)

@Serializable
data class Publish(
    val address: UnicastAddress, // TODO ???
    val index: Int, // TODO what index?
    val period: Period,
    val credentials: Int,
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

@Serializable(with = SubscriptionAddressSerializer::class)
sealed interface SubscriptionAddress

@Serializable
@JvmInline
value class VirtualAddress(@Serializable(with = UuidSerializer::class) val value: UUID) :
    SubscriptionAddress