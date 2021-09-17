package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.utils.NetworkTransmitSettings
import no.nordicsemi.android.mesh.utils.RelaySettings
import java.util.*

@Serializable
data class Node(
    @SerialName("UUID")
    val uuid: NodeId,
    val name: String,
    @Serializable(with = KeySerializer::class)
    val deviceKey: Key? = null,
    val unicastAddress: UnicastAddress,
    @Serializable(with = NodeSecuritySerializer::class)
    val security: Int,
    @SerialName("configComplete")
    val isConfigured: Boolean,
    @SerialName("cid")
    @Serializable(with = VersionCompositionToIntSerializer::class)
    val companyIdentifier: Int? = null,
    @SerialName("pid")
    @Serializable(with = VersionCompositionToIntSerializer::class)
    val productIdentifier: Int? = null,
    @SerialName("vid")
    @Serializable(with = VersionCompositionToIntSerializer::class)
    val versionIdentifier: Int? = null,
    val crpl: Int? = null,
    val features: Features? = null,
    val defaultTTL: Int,
    val excluded: Boolean,
    val networkTransmit: NetworkTransmit? = null,
//    val relayRetransmit: RelayRetransmit? = null, //TODO ??
    val netKeys: List<NodeNetKey> = emptyList(),
    val appKeys: List<NodeAppKey> = emptyList(),
    val elements: List<Element> = emptyList(),
    @Transient val sequenceNumber: Int = 0,
)

@Serializable
@JvmInline
value class NodeId(
    @Serializable(with = UuidSerializer::class)
    val uuid: UUID
)

@Serializable
@JvmInline
value class UnicastAddress(
    @Serializable(with = HexToIntSerializer::class)
    val value: Int
) : PublishableAddress

@Serializable
data class NodeNetKey(
    val index: NetKeyIndex,
    val updated: Boolean
)

fun List<NodeNetKey>.toNetKeys(allKeys: List<NetKey>) =
    mapNotNull { key -> allKeys.find { it.index == key.index } }

fun List<NodeAppKey>.toAppKeys(allKeys: List<AppKey>) =
    mapNotNull { key -> allKeys.find { it.index == key.index } }

@Serializable
data class NodeAppKey(
    val index: AppKeyIndex,
    val updated: Boolean
)

@Serializable
data class Features(
    val friend: Int,
    val lowPower: Int,
    val proxy: Int,
    val relay: Int,
) {
    companion object {
        fun Unsupported() = Features(2, 2, 2, 2)
    }
}

@Serializable(with = NetworkTransmitSerializer::class)
data class NetworkTransmit(
    val count: Int,
    val interval: Int
) {
    fun toData() = TransmitData(count, interval)
}

@Serializable(with = RelayRetransmitSerializer::class)
data class RelayRetransmit(
    val count: Int,
    val interval: Int
) {
    fun toData() = TransmitData(count, interval)
}

@Serializable
data class TransmitData(
    val count: Int,
    val interval: Int
) {
    // from Nrf Mesh library NodeDeserializer
    fun toNetworkTransmit(): NetworkTransmit? {
        return if (count != 0 && interval != 0) {
            // Some versions of nRF Mesh lib for Android were exporting interval
            // as number of steps, not the interval, therefore we can try to fix that.
            return if (interval % 10 != 0 && interval <= 32) {
                // Interval that was exported as intervalSteps are imported as it is.
                NetworkTransmit(count, interval)
            } else if (interval % 10 == 0) {
                // Interval that was exported as intervalSteps are decoded to intervalSteps.
                val steps = NetworkTransmitSettings.decodeNetworkTransmissionInterval(interval)
                NetworkTransmit(count, steps)
            } else null
        } else null
    }

    // from Nrf Mesh library NodeDeserializer
    fun toRelayRetransmit(): RelayRetransmit? {
        return if (count != 0 && interval != 0) {
            // Some versions of nRF Mesh lib for Android were exporting interval
            // as number of steps, not the interval, therefore we can try to fix that.
            if (interval % 10 != 0 && interval <= 32) {
                // Interval that was exported as intervalSteps are imported as it is.
                RelayRetransmit(count, interval)
            } else if (interval % 10 == 0) {
                // Interval that was exported as intervalSteps are imported as it is.
                val steps = RelaySettings.decodeRelayRetransmitInterval(interval)
                RelayRetransmit(count, steps)
            } else null
        } else null
    }
}