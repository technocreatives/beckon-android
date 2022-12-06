package com.technocreatives.beckon.mesh.data

import arrow.optics.optics
import com.technocreatives.beckon.mesh.data.serializer.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@optics
data class Node(
    @SerialName("UUID")
    val id: NodeId,
    val unicastAddress: UnicastAddress,
    @Serializable(with = KeySerializer::class)
    val deviceKey: Key? = null,
    // TODO Should be enum
    @Serializable(with = NodeSecuritySerializer::class)
    val security: Int,
    val netKeys: List<NodeNetKey> = emptyList(),
    @SerialName("configComplete")
    val isConfigured: Boolean,
    val name: String,
    @SerialName("cid")
    @Serializable(with = VersionCompositionToIntSerializer::class)
    val companyIdentifier: Int? = null, // TODO make CompanyIdentifier value class, must be 4 char hex
    @SerialName("pid")
    @Serializable(with = VersionCompositionToIntSerializer::class)
    val productIdentifier: Int? = null, // TODO make ProductIdentifier value class, must be 4 char hex
    @SerialName("vid")
    @Serializable(with = VersionCompositionToIntSerializer::class)
    val versionIdentifier: Int? = null, // TODO make VersionIdentifier value class, must be 4 char hex
    @Serializable(with = HexToIntSerializer::class)
    val crpl: Int? = null, // TODO make VersionIdentifier value class, must be 4 char hex
    val features: Features? = null,
    val secureNetworkBeacon: Boolean? = null,
    val defaultTTL: Int? = null, // TODO make DefaultTTL value class, must be 0-127
    val networkTransmit: NetworkTransmit? = null,
    val relayRetransmit: RelayRetransmit? = null,
    val appKeys: List<NodeAppKey> = emptyList(),
    val elements: List<Element> = emptyList(),
    val excluded: Boolean,
    val heartbeatPub: HeartbeatPub? = null,
    val heartbeatSub: HeartbeatSub? = null,
) {
    companion object
}

// TODO Review this class according to spec
@Serializable
data class HeartbeatPub(
    val address: PublishableAddress, // TODO Unicast or Group (not Virtual)
    val period: Int, // TODO Must be in range 0-65536
    val ttl: Int, // TODO Should be TTL Type, must be 0-127
    val index: NetKeyIndex,
    val features: List<String> // TODO Should be new feature type The allowed values are “relay”, “proxy”, “friend”, and “lowPower”
)

// TODO Review this class according to spec
@Serializable
data class HeartbeatSub(
    val source: UnicastAddress,
    val destination: PublishableAddress // TODO Unicast or Group (not Virtual)
)

@Serializable
@JvmInline
value class NodeId(
    @Serializable(with = UuidSerializer::class)
    val uuid: UUID
)

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

// 2 = Not supported
// 1 = Enabled
// 0 = Not enabled
// null = Unknown
@Serializable
data class Features(
    val relay: Int?,
    val proxy: Int?,
    val lowPower: Int?,
    val friend: Int?,
) {
    companion object {
        fun Unsupported() = Features(2, 2, 2, 2)
    }
}

@Serializable(with = NetworkTransmitSerializer::class)
data class NetworkTransmit(
    val count: Int, // TODO must be 1-8
    val interval: Int // TODO must be 10-320
) {
    fun toData() = TransmitData(count, interval)
}

@Serializable(with = RelayRetransmitSerializer::class)
data class RelayRetransmit(
    val count: Int, // TODO must be 1-8
    val interval: Int // TODO must be 10-320
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
                val steps = decodeNetworkTransmissionInterval(interval)
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
                val steps = decodeRelayRetransmitInterval(interval)
                RelayRetransmit(count, steps)
            } else null
        } else null
    }
    companion object {
        fun decodeNetworkTransmissionInterval(interval: Int): Int {
            require(!(interval in 10..320 && interval % 10 != 0)) { "Network Transmission Interval must be 10-320 ms with a step of 10 ms" }
            return interval / 10 - 1
        }
        fun decodeRelayRetransmitInterval(interval: Int): Int {
            require(!(interval in 10..320 && interval % 10 != 0)) { "Relay Retransmit Interval must be in range of 10-320 ms." }
            return interval / 10 - 1
        }
    }
}