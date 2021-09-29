package com.technocreatives.beckon.mesh.message

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.opcodes.ProxyConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.*

@Serializer(forClass = StatusOpCode::class)
object StatusOpCodeSerializer : KSerializer<StatusOpCode> {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("StatusOpCode", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): StatusOpCode {
        return StatusOpCode.from(decoder.decodeInt())
    }

    override fun serialize(encoder: Encoder, obj: StatusOpCode) {
        encoder.encodeInt(obj.value)
    }
}

@Serializable(with = StatusOpCodeSerializer::class)
enum class StatusOpCode(val value: Int) {
    ConfigComposition(2), // ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt()
    ConfigDefaultTtl(ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS),
    ConfigNetworkSet(ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS),
    ConfigAppKey(ConfigMessageOpCodes.CONFIG_APPKEY_STATUS),
    ConfigModelApp(ConfigMessageOpCodes.CONFIG_MODEL_APP_STATUS),
    ConfigModelSubscription(ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS),
    ConfigModelPublication(ConfigMessageOpCodes.CONFIG_MODEL_PUBLICATION_STATUS),
    ConfigNodeReset(ConfigMessageOpCodes.CONFIG_NODE_RESET_STATUS),
    ProxyFilterType(ProxyConfigMessageOpCodes.FILTER_STATUS)
    ;

    companion object {
        fun from(value: Int): StatusOpCode = values().first { it.value == value }
    }

}