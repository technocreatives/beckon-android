package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import com.technocreatives.beckon.mesh.data.serializer.KeySerializer
import com.technocreatives.beckon.mesh.data.serializer.NodeSecuritySerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    val cid: String? = null,
    val pid: String? = null,
    val vid: String? = null,
    val crpl: String? = null,
    val features: Features,
    val defaultTTL: Int,
    val excluded: Boolean,
    val networkTransmit: NetworkTransmit? = null,
    val netKeys: List<NodeNetKey> = emptyList(),
    val appKeys: List<NodeAppKey> = emptyList(),
    val elements: List<Element> = emptyList(),
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
)

@Serializable
data class NodeNetKey(
    val index: NetKeyIndex,
    val updated: Boolean
)

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
)

@Serializable
data class NetworkTransmit(
    val count: Int,
    val interval: Int
)

@Serializable
data class RelayRetransmit(
    val count: Int,
    val interval: Int
)