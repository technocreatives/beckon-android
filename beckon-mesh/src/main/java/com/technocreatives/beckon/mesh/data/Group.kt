package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val name: String,
    val address: GroupAddress,
    val parentAddress: GroupAddress,
)

@Serializable
@JvmInline
value class GroupAddress(@Serializable(with = HexToIntSerializer::class) val value: Int): SubscriptionAddress