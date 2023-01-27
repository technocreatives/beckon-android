package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import com.technocreatives.beckon.mesh.data.serializer.SubscriptionAddressSerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import com.technocreatives.beckon.mesh.data.util.Constants
import kotlinx.serialization.Serializable
import java.util.*

object UnassignedAddress {
    const val value = Constants.UnassignedAddress
}

@Serializable(with = SubscriptionAddressSerializer::class)
sealed interface SubscriptionAddress

@Serializable
@JvmInline
value class VirtualAddress(@Serializable(with = UuidSerializer::class) val value: UUID) :
    SubscriptionAddress


@Serializable
@JvmInline
value class GroupAddress(@Serializable(with = HexToIntSerializer::class) val value: Int) :
    SubscriptionAddress, PublishableAddress


@Serializable
@JvmInline
value class UnicastAddress(
    @Serializable(with = HexToIntSerializer::class)
    val value: Int
) : PublishableAddress, Comparable<UnicastAddress> { // TODO Can be SubscriptionAddress as well
    override fun compareTo(other: UnicastAddress): Int {
        return value.compareTo(other.value)
    }
}