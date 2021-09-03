package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Model(
    val modelId: ModelId,
    val bind: List<AppKeyIndex> = emptyList(),
    val subscribe: List<GroupAddress> = emptyList(),
    val publish: Publish? = null
)

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
