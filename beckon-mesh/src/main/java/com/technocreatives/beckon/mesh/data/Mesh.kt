package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeSerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.MeshNetwork
import java.time.OffsetDateTime
import java.util.*

@Serializable
data class Mesh(
    @SerialName("\$schema")
    val schema: String,
    val id: String,
    val version: String,
    @Serializable(with = UuidSerializer::class)
    @SerialName("meshUUID")
    val meshUuid: UUID,
    val meshName: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val timestamp: Long,
    val partial: Boolean,
    val netKeys: List<NetKey> = emptyList(),
    val appKeys: List<AppKey> = emptyList(),
    val provisioners: List<Provisioner>,
    val nodes: List<Node> = emptyList(),
    val groups: List<Group> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val networkExclusions: List<NetworkExclusion> = emptyList()
)

fun Mesh.nodesWithoutProvisioner(): List<Node> {
    val selectedProvisioner = provisioners.findLast { it.isLastSelected }
    return nodes.filter { it.uuid.uuid != selectedProvisioner?.uuid }
}

@Serializable
data class NetworkExclusion(val ivIndex: Int, val addresses: List<Int>)