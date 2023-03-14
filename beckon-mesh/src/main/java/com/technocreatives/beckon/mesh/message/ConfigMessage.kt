package com.technocreatives.beckon.mesh.message

import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.data.Element
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nordicsemi.android.mesh.transport.*
import no.nordicsemi.android.mesh.utils.RelaySettings.RELAY_FEATURE_ENABLED

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ConfigMessage<out T : ConfigStatusMessage> {
    abstract val responseOpCode: StatusOpCode
    abstract val dst: Int
    abstract fun toMeshMessage(): MeshMessage
    abstract fun fromResponse(message: MeshMessage): T
}

@Serializable
@SerialName("GetCompositionData")
data class GetCompositionData(override val dst: Int) :
    ConfigMessage<GetCompositionDataResponse>() {
    override val responseOpCode = StatusOpCode.ConfigComposition
    override fun toMeshMessage() = ConfigCompositionDataGet()
    override fun fromResponse(message: MeshMessage): GetCompositionDataResponse =
        (message as ConfigCompositionDataStatus).transform()

}

@Serializable
@SerialName("GetNodeIdentity")
data class GetNodeIdentity(
    override val dst: Int,
    val networkKey: NetKey
) : ConfigMessage<GetNodeIdentityResponse>() {
    override val responseOpCode = StatusOpCode.ConfigNodeIdentity
    override fun toMeshMessage() = ConfigNodeIdentityGet(networkKey.transform())
    override fun fromResponse(message: MeshMessage): GetNodeIdentityResponse =
        (message as ConfigNodeIdentityStatus).transform()
}

@Serializable
@SerialName("GetDefaultTtl")
data class GetDefaultTtl(override val dst: Int) :
    ConfigMessage<ConfigDefaultTtlResponse>() {
    override val responseOpCode = StatusOpCode.ConfigDefaultTtl
    override fun toMeshMessage() = ConfigDefaultTtlGet()
    override fun fromResponse(message: MeshMessage): ConfigDefaultTtlResponse =
        (message as ConfigDefaultTtlStatus).transform()
}

@Serializable
@SerialName("SetDefaultTtl")
data class SetDefaultTtl(
    override val dst: Int,
    val ttl: Int
) :
    ConfigMessage<ConfigDefaultTtlResponse>() {
    override val responseOpCode = StatusOpCode.ConfigDefaultTtl
    override fun toMeshMessage() = ConfigDefaultTtlSet(ttl)
    override fun fromResponse(message: MeshMessage): ConfigDefaultTtlResponse =
        (message as ConfigDefaultTtlStatus).transform()
}

@Serializable
@SerialName("SetConfigRelay")
data class SetRelayConfig(
    override val dst: Int,
    val relay: Int = RELAY_FEATURE_ENABLED,
    val retransmit: RelayRetransmit?
) :
    ConfigMessage<ConfigRelayResponse>() {
    override val responseOpCode = StatusOpCode.ConfigRelaySet

    // TODO remove !!
    override fun toMeshMessage() = ConfigRelaySet(relay, retransmit!!.count, retransmit.interval)
    override fun fromResponse(message: MeshMessage): ConfigRelayResponse =
        (message as ConfigRelayStatus).transform()
}

@Serializable
@SerialName("SetConfigNetworkTransmit")
data class SetConfigNetworkTransmit(override val dst: Int, val count: Int, val steps: Int) :
    ConfigMessage<ConfigNetworkTransmitResponse>() {
    override val responseOpCode = StatusOpCode.ConfigNetworkSet
    override fun toMeshMessage() = ConfigNetworkTransmitSet(count, steps)
    override fun fromResponse(message: MeshMessage): ConfigNetworkTransmitResponse =
        (message as ConfigNetworkTransmitStatus).transform()
}

@Serializable
@SerialName("AddConfigAppKey")
data class AddConfigAppKey(
    override val dst: Int,
    val netKey: NetKey,
    val appKey: AppKey,
) : ConfigMessage<ConfigAppKeyResponse>() {
    override val responseOpCode = StatusOpCode.ConfigAppKey
    override fun toMeshMessage() = ConfigAppKeyAdd(netKey.transform(), appKey.transform())
    override fun fromResponse(message: MeshMessage): ConfigAppKeyResponse =
        (message as ConfigAppKeyStatus).transform()

    // send a andThen send not
    operator fun not() = DeleteConfigAppKey(dst, netKey, appKey)
}

@Serializable
@SerialName("DeleteConfigAppKey")
data class DeleteConfigAppKey(
    override val dst: Int,
    val netKey: NetKey,
    val appKey: AppKey,
) : ConfigMessage<ConfigAppKeyResponse>() {
    override val responseOpCode = StatusOpCode.ConfigAppKey
    override fun toMeshMessage() = ConfigAppKeyDelete(netKey.transform(), appKey.transform())
    override fun fromResponse(message: MeshMessage): ConfigAppKeyResponse =
        (message as ConfigAppKeyStatus).transform()

    operator fun not() = AddConfigAppKey(dst, netKey, appKey)
}

@Serializable
@SerialName("AddConfigModelSubscription")
data class AddConfigModelSubscription(
    override val dst: Int,
    val elementAddress: Int,
    val subscriptionAddress: Int,
    val modelId: Int,
) : ConfigMessage<ConfigModelSubscriptionResponse>() {
    override val responseOpCode = StatusOpCode.ConfigModelSubscription
    override fun toMeshMessage() =
        ConfigModelSubscriptionAdd(elementAddress, subscriptionAddress, modelId)

    override fun fromResponse(message: MeshMessage): ConfigModelSubscriptionResponse =
        (message as ConfigModelSubscriptionStatus).transform()

    operator fun not() =
        RemoveConfigModelSubscription(dst, elementAddress, subscriptionAddress, modelId)
}

@Serializable
@SerialName("RemoveConfigModelSubscription")
data class RemoveConfigModelSubscription(
    override val dst: Int,
    val elementAddress: Int,
    val subscriptionAddress: Int,
    val modelId: Int,
) : ConfigMessage<ConfigModelSubscriptionResponse>() {
    override val responseOpCode = StatusOpCode.ConfigModelSubscription
    override fun toMeshMessage() =
        ConfigModelSubscriptionDelete(elementAddress, subscriptionAddress, modelId)

    override fun fromResponse(message: MeshMessage): ConfigModelSubscriptionResponse =
        (message as ConfigModelSubscriptionStatus).transform()

    operator fun not() =
        AddConfigModelSubscription(dst, elementAddress, subscriptionAddress, modelId)
}

@Serializable
@SerialName("GetConfigModelPublication")
data class GetConfigModelPublication(
    override val dst: Int,
    val elementAddress: UnicastAddress,
    val modelId: ModelId
) : ConfigMessage<ConfigModelPublicationResponse>() {
    override val responseOpCode = StatusOpCode.ConfigModelPublication
    override fun toMeshMessage() = ConfigModelPublicationGet(elementAddress.value, modelId.value)
    override fun fromResponse(message: MeshMessage): ConfigModelPublicationResponse =
        (message as ConfigModelPublicationStatus).transform()
}

@Serializable
@SerialName("SetConfigModelPublication")
data class SetConfigModelPublication(
    override val dst: Int,
    val elementAddress: UnicastAddress,
    val publish: Publish,
    val modelId: ModelId
) : ConfigMessage<ConfigModelPublicationResponse>() {
    override val responseOpCode = StatusOpCode.ConfigModelPublication
    override fun toMeshMessage() = ConfigModelPublicationSet(
        elementAddress.value,
        publish.address.value,
        publish.index.value,
        publish.credentialsFlag,
        publish.ttl,
        publish.period.numberOfSteps,
        publish.period.resolution.value,
        publish.retransmit.count,
        publish.retransmit.interval,
        modelId.value
    )

    override fun fromResponse(message: MeshMessage): ConfigModelPublicationResponse =
        (message as ConfigModelPublicationStatus).transform()

    operator fun not() =
        ClearConfigModelPublication(dst, elementAddress, modelId)
}


@Serializable
@SerialName("ClearConfigModelPublication")
data class ClearConfigModelPublication(
    override val dst: Int,
    val elementAddress: UnicastAddress,
    val modelId: ModelId,
) : ConfigMessage<ConfigModelPublicationResponse>() {
    override val responseOpCode = StatusOpCode.ConfigModelPublication
    override fun toMeshMessage() = ConfigModelPublicationSet(elementAddress.value, modelId.value)

    override fun fromResponse(message: MeshMessage): ConfigModelPublicationResponse =
        (message as ConfigModelPublicationStatus).transform()
}

@Serializable
sealed class ConfigStatusMessage {
    abstract val dst: Int
    abstract val src: Int
}

@Serializable
data class ConfigModelPublicationResponse(
    override val dst: Int,
    override val src: Int,
    val mElementAddress: UnicastAddress,
    val publish: Publish,
    val modelId: ModelId
) : ConfigStatusMessage()

internal fun ConfigModelPublicationStatus.transform() =
    ConfigModelPublicationResponse(
        dst,
        src,
        UnicastAddress(elementAddress),
        Publish(
            address = GroupAddress(publishAddress),
            index = AppKeyIndex(appKeyIndex),
            period = Period(
                numberOfSteps = publicationSteps,
                resolution = PublicationResolution.valueOf(publicationResolution)
            ),
            credentialsFlag = credentialFlag,
            ttl = publishTtl,
            retransmit = Retransmit(
                publishRetransmitCount,
                publishRetransmitIntervalSteps
            )
        ),
        modelId = ModelId(modelIdentifier)
    )

@Serializable
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
) : ConfigStatusMessage()

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
        elements.transform(UnicastAddress(src)),
        companyIdentifier,
        productIdentifier,
        versionIdentifier,
    )
}

@Serializable
data class GetNodeIdentityResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val nodeIdentityState: Int,
    val netKeyIndex: NetKeyIndex
) : ConfigStatusMessage()

internal fun ConfigNodeIdentityStatus.transform() = GetNodeIdentityResponse(
    dst,
    src,
    statusCode,
    nodeIdentityState,
    NetKeyIndex(netKeyIndex)
)

@Serializable
data class ConfigDefaultTtlResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val ttl: Int,
) : ConfigStatusMessage()

internal fun ConfigDefaultTtlStatus.transform() = ConfigDefaultTtlResponse(
    dst,
    src,
    statusCode,
    ttl,
)

@Serializable
data class ConfigRelayResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val count: Int,
    val steps: Int,
) : ConfigStatusMessage()

internal fun ConfigRelayStatus.transform() = ConfigRelayResponse(
    dst,
    src,
    statusCode,
    relayRetransmitCount,
    relayRetransmitIntervalSteps
)

@Serializable
data class ConfigNetworkTransmitResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val count: Int,
    val steps: Int,
) : ConfigStatusMessage()

internal fun ConfigNetworkTransmitStatus.transform() =
    ConfigNetworkTransmitResponse(
        dst,
        src,
        statusCode,
        networkTransmitCount,
        networkTransmitIntervalSteps,
    )

@Serializable
data class ConfigAppKeyResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val netKeyIndex: NetKeyIndex,
    val appKeyIndex: AppKeyIndex,
) : ConfigStatusMessage()

internal fun ConfigAppKeyStatus.transform() =
    ConfigAppKeyResponse(
        dst,
        src,
        statusCode,
        NetKeyIndex(netKeyIndex),
        AppKeyIndex(appKeyIndex),
    )

@Serializable
data class ConfigModelSubscriptionResponse(
    override val dst: Int,
    override val src: Int,
    val statusCode: Int,
    val elementAddress: UnicastAddress,
    val subscriptionAddress: SubscriptionAddress,
    val modelId: ModelId
) : ConfigStatusMessage()

internal fun ConfigModelSubscriptionStatus.transform() =
    ConfigModelSubscriptionResponse(
        dst,
        src,
        statusCode,
        UnicastAddress(elementAddress),
        GroupAddress(subscriptionAddress),
        ModelId(modelIdentifier)
    )

private fun Boolean.toFeature() = if (this) 1 else 2