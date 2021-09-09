package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.utils.MeshAddress

@Serializable
data class Group(
    val name: String,
    val address: GroupAddress,
    val parentAddress: GroupAddress = GroupAddress(MeshAddress.UNASSIGNED_ADDRESS),
)

@Serializable
@JvmInline
value class GroupAddress(@Serializable(with = HexToIntSerializer::class) val value: Int): SubscriptionAddress