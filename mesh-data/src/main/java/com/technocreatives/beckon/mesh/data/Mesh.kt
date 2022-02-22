package com.technocreatives.beckon.mesh.data

import arrow.optics.optics
import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeToLongSerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@optics
data class MeshConfig @OptIn(ExperimentalSerializationApi::class) constructor(
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
fun MeshConfig.nodesWithoutProvisioners(): List<Node> {
    val provisioners = provisioners.map { it.uuid }
    return nodes.filter { it.uuid.uuid !in provisioners }
}

@ExperimentalSerializationApi
fun MeshConfig.provisionerNodes(): List<Node> {
    val provisioners = provisioners.map { it.uuid }
    return nodes.filter { it.uuid.uuid in provisioners }
}

@Serializable
data class NetworkExclusion(val ivIndex: Int, val addresses: List<UnicastAddress>)