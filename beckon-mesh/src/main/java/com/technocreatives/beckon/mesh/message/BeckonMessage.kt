package com.technocreatives.beckon.mesh.message

import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.data.Element
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.*

//sealed interface BeckonMessage {
//    val dst: Int
//    fun toMeshMessage(): MeshMessage
//}

sealed interface AckBeckonMessage {
    val responseOpCode: Int
    val dst: Int
    fun toMeshMessage(): MeshMessage
//    fun <T : Any> get(clazz: KClass<T>) : KClass<T>
}

object EmptyAckBeckonMessage : AckBeckonMessage {
    override val responseOpCode: Int = 0
    override val dst: Int = 0
    override fun toMeshMessage(): MeshMessage {
        TODO("Never call this!!!!")
    }
}

data class GetCompositionData(override val dst: Int) : AckBeckonMessage {
    override fun toMeshMessage() = ConfigCompositionDataGet()

    override val responseOpCode = ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt()

}

data class GetDefaultTtl(override val dst: Int) : AckBeckonMessage {
    override fun toMeshMessage() = ConfigDefaultTtlGet()
    override val responseOpCode = ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS
}

data class SetConfigNetworkTransmit(override val dst: Int, val count: Int, val steps: Int) :
    AckBeckonMessage {
    override val responseOpCode = ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS
    override fun toMeshMessage() = ConfigNetworkTransmitSet(count, steps)
}

data class AddConfigAppKey(
    override val dst: Int,
    val netKey: NetKey,
    val appKey: AppKey,
) : AckBeckonMessage {
    override val responseOpCode = ConfigMessageOpCodes.CONFIG_APPKEY_STATUS
    override fun toMeshMessage() = ConfigAppKeyAdd(netKey.transform(), appKey.transform())

    // send a andThen send not
    operator fun not() = DeleteConfigAppKey(dst, netKey, appKey)
}

data class DeleteConfigAppKey(
    override val dst: Int,
    val netKey: NetKey,
    val appKey: AppKey,
) : AckBeckonMessage {
    override val responseOpCode = ConfigMessageOpCodes.CONFIG_APPKEY_STATUS
    override fun toMeshMessage() = ConfigAppKeyDelete(netKey.transform(), appKey.transform())
    operator fun not() = AddConfigAppKey(dst, netKey, appKey)

}

sealed interface BeckonResponseMessage {
    val dst: Int
    val src: Int

    companion object {
        fun from(message: MeshMessage): BeckonResponseMessage =
            when (StatusOpCode.from(message.opCode)) {
                StatusOpCode.ConfigComposition -> (message as ConfigCompositionDataStatus).transform()
                StatusOpCode.ConfigDefaultTtl -> (message as ConfigDefaultTtlStatus).transform()
                StatusOpCode.ConfigNetworkSet -> (message as ConfigNetworkTransmitStatus).transform()
                StatusOpCode.ConfigAppKey -> (message as ConfigAppKeyStatus).transform()
            }
    }
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
) : BeckonResponseMessage

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
) : BeckonResponseMessage

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
) : BeckonResponseMessage

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
) : BeckonResponseMessage

internal fun ConfigAppKeyStatus.transform() =
    ConfigAppKeyResponse(
        dst,
        src,
        statusCode,
        NetKeyIndex(netKeyIndex),
        AppKeyIndex(appKeyIndex),
    )

private fun Boolean.toFeature() = if (this) 1 else 2