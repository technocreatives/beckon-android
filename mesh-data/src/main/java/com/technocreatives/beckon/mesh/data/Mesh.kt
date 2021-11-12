package com.technocreatives.beckon.mesh.data

import arrow.optics.optics
import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeToLongSerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import com.technocreatives.beckon.mesh.data.util.Constants
import com.technocreatives.beckon.mesh.data.util.generateRandomNumber
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

@ExperimentalSerializationApi
@Serializable
@optics
data class MeshConfig(
    @SerialName("\$schema")
    val schema: String,
    val id: String,
    val version: String,
    @Serializable(with = UuidSerializer::class)
    @SerialName("meshUUID")
    val meshUuid: UUID,
    val meshName: String,
    @Serializable(with = OffsetDateTimeToLongSerializer::class)
    val timestamp: Long,
    val partial: Boolean,
    val netKeys: List<NetKey> = emptyList(),
    val appKeys: List<AppKey> = emptyList(),
    val provisioners: List<Provisioner>,
    val nodes: List<Node> = emptyList(),
    val groups: List<Group> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val networkExclusions: List<NetworkExclusion> = emptyList(),
) {
    companion object
}

@ExperimentalSerializationApi
fun MeshConfig.nodesWithoutProvisioner(): List<Node> {
    val selectedProvisioner = provisioners.findLast { it.isLastSelected }
    return nodes.filter { it.uuid.uuid != selectedProvisioner?.uuid }
}

@Serializable
data class NetworkExclusion(val ivIndex: Int, val addresses: List<Int>)