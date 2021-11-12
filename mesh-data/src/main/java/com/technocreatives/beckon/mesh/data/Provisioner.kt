package com.technocreatives.beckon.mesh.data

import arrow.optics.optics
import com.technocreatives.beckon.mesh.data.serializer.HexToIntSerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@optics
data class Provisioner(
    @SerialName("provisionerName")
    val name: String,
    @Serializable(with = UuidSerializer::class)
    @SerialName("UUID")
    val uuid: UUID,
    val allocatedUnicastRange: List<AddressRange>,
    val allocatedGroupRange: List<AddressRange>,
    val allocatedSceneRange: List<SceneRange>,
    @Transient val isLastSelected: Boolean = false,
) {
    companion object
}

@Serializable
@JvmInline
value class AddressValue(
    @Serializable(with = HexToIntSerializer::class)
    val value: Int
)

@Serializable
data class AddressRange(
    val lowAddress: AddressValue,
    val highAddress: AddressValue,
)

@Serializable
data class SceneRange(
    val firstScene: AddressValue,
    val lastScene: AddressValue,
)