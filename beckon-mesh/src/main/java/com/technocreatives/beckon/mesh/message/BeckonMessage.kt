package com.technocreatives.beckon.mesh.message

import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.data.Element
import no.nordicsemi.android.mesh.transport.*

//sealed interface BeckonMessage {
//    val dst: Int
//    fun toMeshMessage(): MeshMessage
//}

sealed interface ConfigMessage {
    val responseOpCode: StatusOpCode
    val dst: Int
    fun toMeshMessage(): MeshMessage
}

data class GetCompositionData(override val dst: Int) :
    ConfigMessage {
    override fun toMeshMessage() = ConfigCompositionDataGet()

    override val responseOpCode = StatusOpCode.ConfigComposition

}

data class GetDefaultTtl(override val dst: Int) :
    ConfigMessage {
    override fun toMeshMessage() = ConfigDefaultTtlGet()
    override val responseOpCode = StatusOpCode.ConfigDefaultTtl
}

data class SetConfigNetworkTransmit(override val dst: Int, val count: Int, val steps: Int) :
    ConfigMessage {
    override val responseOpCode = StatusOpCode.ConfigNetworkSet
    override fun toMeshMessage() = ConfigNetworkTransmitSet(count, steps)
}

data class AddConfigAppKey(
    override val dst: Int,
    val netKey: NetKey,
    val appKey: AppKey,
) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ConfigAppKey
    override fun toMeshMessage() = ConfigAppKeyAdd(netKey.transform(), appKey.transform())

    // send a andThen send not
    operator fun not() = DeleteConfigAppKey(dst, netKey, appKey)
}

data class DeleteConfigAppKey(
    override val dst: Int,
    val netKey: NetKey,
    val appKey: AppKey,
) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ConfigAppKey
    override fun toMeshMessage() = ConfigAppKeyDelete(netKey.transform(), appKey.transform())
    operator fun not() = AddConfigAppKey(dst, netKey, appKey)
}

data class AddConfigModelSubscription(
    override val dst: Int,
    val elementAddress: Int,
    val subscriptionAddress: Int,
    val modelId: Int,
) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ConfigModelSubscription
    override fun toMeshMessage() = ConfigModelSubscriptionAdd(elementAddress, subscriptionAddress, modelId)
    operator fun not() = RemoveConfigModelSubscription(dst, elementAddress, subscriptionAddress, modelId)
}

data class RemoveConfigModelSubscription(
    override val dst: Int,
    val elementAddress: Int,
    val subscriptionAddress: Int,
    val modelId: Int,
) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ConfigModelSubscription
    override fun toMeshMessage() = ConfigModelSubscriptionDelete(elementAddress, subscriptionAddress, modelId)
    operator fun not() = AddConfigModelSubscription(dst, elementAddress, subscriptionAddress, modelId)
}

sealed interface BeckonStatusMessage {
    val dst: Int
    val src: Int
}

data class GetCompositionDataResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val crpl: Int? = null,
    val features: Features?,
    val elements: List<Element>,
    val companyIdentifier: Int?,
    val productIdentifier: Int?,
    val versionIdentifier: Int?,
) : BeckonStatusMessage

internal fun ConfigCompositionDataStatus.transform(): GetCompositionDataResponse {
    val features = Features(
        friend = isFriendFeatureSupported.toFeature(),
        relay = isRelayFeatureSupported.toFeature(),
        proxy = isProxyFeatureSupported.toFeature(),
        lowPower = isLowPowerFeatureSupported.toFeature(),
    )

    return GetCompositionDataResponse(
        dst,
        src,
        statusCode,
        crpl,
        features,
        elements.transform(),
        companyIdentifier,
        productIdentifier,
        versionIdentifier,
    )
}

data class ConfigDefaultTtlResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val ttl: Int,
) : BeckonStatusMessage

internal fun ConfigDefaultTtlStatus.transform() = ConfigDefaultTtlResponse(
    dst,
    src,
    statusCode,
    ttl,
)

data class ConfigNetworkTransmitResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val count: Int,
    val steps: Int,
) : BeckonStatusMessage

internal fun ConfigNetworkTransmitStatus.transform() =
    ConfigNetworkTransmitResponse(
        dst,
        src,
        statusCode,
        networkTransmitCount,
        networkTransmitIntervalSteps,
    )

data class ConfigAppKeyResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val netKeyIndex: NetKeyIndex,
    val appKeyIndex: AppKeyIndex,
) : BeckonStatusMessage

internal fun ConfigAppKeyStatus.transform() =
    ConfigAppKeyResponse(
        dst,
        src,
        statusCode,
        NetKeyIndex(netKeyIndex),
        AppKeyIndex(appKeyIndex),
    )

data class ConfigModelSubscriptionResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val elementAddress: Int,
    val subscriptionAddress: Int,
    val modelId: Int
) : BeckonStatusMessage

internal fun ConfigModelSubscriptionStatus.transform() =
    ConfigModelSubscriptionResponse(
        dst,
        src,
        statusCode,
        elementAddress,
        subscriptionAddress,
        modelIdentifier
    )



private fun Boolean.toFeature() = if (this) 1 else 2