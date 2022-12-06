package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.util.Constants
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val name: String,
    val address: GroupAddress, // TODO Can also be virtual label UUID
    val parentAddress: GroupAddress = GroupAddress(Constants.UnassignedAddress), // TODO Unassinged, Group or Virutal label UUID and shall not be same as address property
)