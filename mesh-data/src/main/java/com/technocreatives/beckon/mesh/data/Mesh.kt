package com.technocreatives.beckon.mesh.data

import arrow.optics.optics
import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeToLongSerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import com.technocreatives.beckon.mesh.data.util.toHex
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@optics
data class MeshConfig @OptIn(ExperimentalSerializationApi::class) constructor(
    @SerialName("\$schema")
    val schema: String, // TODO URL
    val id: String, // TODO URL
    val version: String,
    @Serializable(with = UuidSerializer::class)
    @SerialName("meshUUID")
    val meshUuid: UUID,
    val meshName: String,
    @Serializable(with = OffsetDateTimeToLongSerializer::class)
    val timestamp: Long, // TODO Verify format to spec
    val partial: Boolean,
    val provisioners: List<Provisioner>,
    val netKeys: List<NetKey> = emptyList(),
    val appKeys: List<AppKey> = emptyList(),
    val nodes: List<Node> = emptyList(),
    val groups: List<Group> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val networkExclusions: List<NetworkExclusion> = emptyList(),
) {
    companion object

    fun isNetworkIdMatch(id: NetworkId): Boolean =
        netKeys.any { it.isNetworkIdMatch(id) }

    fun networkIds(): List<NetworkId> =
        netKeys.map { it.networkId() }

}

@ExperimentalSerializationApi
fun MeshConfig.nodesWithoutProvisioners(): List<Node> {
    val provisioners = provisioners.map { it.id }
    return nodes.filter { it.id !in provisioners }
}

@ExperimentalSerializationApi
fun MeshConfig.provisionerNodes(): List<Node> {
    val provisioners = provisioners.map { it.id }
    return nodes.filter { it.id in provisioners }
}

@Serializable
data class NetworkExclusion(val ivIndex: Int, val addresses: List<UnicastAddress>)