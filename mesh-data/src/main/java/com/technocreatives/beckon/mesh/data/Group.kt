package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.util.Constants
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val name: String,
    val address: GroupAddress,
    val parentAddress: GroupAddress = GroupAddress(Constants.UnassignedAddress),
)