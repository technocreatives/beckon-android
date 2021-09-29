package com.technocreatives.beckon.mesh.data

import arrow.core.Either
import com.technocreatives.beckon.mesh.data.serializer.OffsetDateTimeSerializer
import com.technocreatives.beckon.mesh.data.serializer.UuidSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nordicsemi.android.mesh.models.SigModelParser
import no.nordicsemi.android.mesh.utils.SecureUtils
import java.time.Instant
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
    val networkExclusions: List<NetworkExclusion> = emptyList(),
) {
    companion object {

        val SCHEMA = "http://json-schema.org/draft-04/schema#"
        val ID =
            "http://www.bluetooth.com/specifications/assigned-numbers/mesh-profile/cdb-schema.json#"
        val VERSION = "1.0.0"

        private val format by lazy { Json { encodeDefaults = true; explicitNulls = false } }
        private val prettyFormat by lazy {
            Json {
                encodeDefaults = true; explicitNulls = false; prettyPrint = true
            }
        }

        fun fromJson(json: String) =
            Either.catch { format.decodeFromString<Mesh>(json) }

        fun toJson(mesh: Mesh): String =
            format.encodeToString(mesh)

        fun toJsonPretty(mesh: Mesh): String = prettyFormat.encodeToString(mesh)

        internal fun generateMesh(meshName: String, provisionerName: String): Mesh {
            val uuid = UUID.randomUUID()
            val appKeys = generateAppKeys()
            val netKeys = generateNetKeys()
            val unicastRanges = listOf(AddressRange(AddressValue(0x0001), AddressValue(0x199A)))
            val groupRanges = listOf(AddressRange(AddressValue(0xC000), AddressValue(0xCC9A)))
            val sceneRanges = listOf(SceneRange(AddressValue(0x0001), AddressValue(0x3333)))
            val provisioner = Provisioner(
                provisionerName,
                UUID.randomUUID(),
                unicastRanges,
                groupRanges,
                sceneRanges,
                true
            )
            val provisionerNode = generateProvisionerNode(provisioner, appKeys, netKeys)
            return Mesh(
                SCHEMA,
                ID,
                VERSION,
                uuid,
                meshName,
                Instant.now().toEpochMilli(),
                false,
                netKeys,
                appKeys,
                listOf(provisioner),
                listOf(provisionerNode)
            )
        }

        private fun generateProvisionerNode(
            provisioner: Provisioner,
            appKeys: List<AppKey>,
            netKeys: List<NetKey>
        ): Node {
            val model =
                SigModelParser.getSigModel(SigModelParser.CONFIGURATION_CLIENT.toInt()).transform()
            val unicast = UnicastAddress(1)
            val element = Element(
                unicast,
                "Element 0x0001",
                ElementIndex(0),
                location = 0,
                listOf(model)
            ) //TODO
            val nodeNetKeys = netKeys.map { NodeNetKey(it.index, true) }
            val nodeAppKeys = appKeys.map { NodeAppKey(it.index, true) }
            return Node(
                NodeId(provisioner.uuid),
                provisioner.name,
                Key(SecureUtils.generateRandomNumber()),
                UnicastAddress(1),
                security = 0,
                isConfigured = true,
                defaultTTL = 5,
                features = Features.Unsupported(),
                excluded = false,
                sequenceNumber = 0,
                elements = listOf(element),
                netKeys = nodeNetKeys,
                appKeys = nodeAppKeys,
            )
        }

        private fun generateNetKeys(): List<NetKey> =
            listOf(NetKey("NetKey 1", NetKeyIndex(0), Key(SecureUtils.generateRandomNumber())))

        private fun generateAppKeys() =
            listOf(
                AppKey(
                    "AppKey 1",
                    AppKeyIndex(0),
                    NetKeyIndex(0),
                    Key(SecureUtils.generateRandomNumber())
                )
            )

        fun randomAppKey(appKeyIndex: Int, netKeyIndex: Int) =
            AppKey(
                "AppKey $appKeyIndex",
                AppKeyIndex(appKeyIndex),
                NetKeyIndex(netKeyIndex),
                Key(SecureUtils.generateRandomNumber())
            )

        fun randomNetKey(appKeyIndex: Int, netKeyIndex: Int) =
            NetKey(
                "NetKey $netKeyIndex",
                NetKeyIndex(netKeyIndex),
                Key(SecureUtils.generateRandomNumber())
            )
    }


}

fun Mesh.nodesWithoutProvisioner(): List<Node> {
    val selectedProvisioner = provisioners.findLast { it.isLastSelected }
    return nodes.filter { it.uuid.uuid != selectedProvisioner?.uuid }
}

@Serializable
data class NetworkExclusion(val ivIndex: Int, val addresses: List<Int>)