package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.mesh.data.util.Constants
import com.technocreatives.beckon.mesh.data.util.generateRandomNumber
import java.time.Instant
import java.util.*

// TODO remove
object MeshConfigHelper {

    val SCHEMA = "http://json-schema.org/draft-04/schema#"
    val ID =
        "http://www.bluetooth.com/specifications/assigned-numbers/mesh-profile/cdb-schema.json#"
    val VERSION = "1.0.0"

    fun generateMesh(meshName: String, provisionerName: String): MeshConfig =
        generateMesh(meshName, UUID.randomUUID(), provisionerName, UUID.randomUUID())

    fun generateMesh(meshName: String, meshId: UUID, provisionerName: String, provisionerId: UUID): MeshConfig {
        val pId = NodeId(provisionerId)
        val appKeys = generateAppKeys()
        val netKeys = generateNetKeys()
        val unicastRanges = listOf(AddressRange(AddressValue(0x0001), AddressValue(0x199A)))
        val groupRanges = listOf(AddressRange(AddressValue(0xC000), AddressValue(0xCC9A)))
        val sceneRanges = listOf(SceneRange(AddressValue(0x0001), AddressValue(0x3333)))
        val provisioner = Provisioner(
            provisionerName,
            pId,
            unicastRanges,
            groupRanges,
            sceneRanges,
            true
        )
        val provisionerNode = generateProvisionerNode(provisioner, appKeys, netKeys)
        return MeshConfig(
            SCHEMA,
            ID,
            VERSION,
            meshId,
            meshName,
            Instant.now().toEpochMilli(),
            false,
            listOf(provisioner),
            netKeys,
            appKeys,
            listOf(provisionerNode)
        )
    }

    private fun generateProvisionerNode(
        provisioner: Provisioner,
        appKeys: List<AppKey>,
        netKeys: List<NetKey>
    ): Node {
        val model =
            SigModel(ModelId(Constants.CONFIGURATION_CLIENT.toInt()))
        val unicast = UnicastAddress(1)
        val element = Element(
            "Element 0x0001",
            ElementIndex(0),
            location = 0,
            listOf(model)
        ) //TODO
        val nodeNetKeys = netKeys.map { NodeNetKey(it.index, true) }
        val nodeAppKeys = appKeys.map { NodeAppKey(it.index, true) }
        return Node(
            provisioner.id,
            UnicastAddress(1),
            Key(generateRandomNumber()),
            security = 0,
            netKeys = nodeNetKeys,
            isConfigured = true,
            provisioner.name,
            defaultTTL = 5,
            features = Features.Unsupported(),
            excluded = false,
            elements = listOf(element),
            appKeys = nodeAppKeys,
        )
    }

    private fun generateNetKeys(): List<NetKey> =
        listOf(NetKey("NetKey 1", NetKeyIndex(0), key = Key(generateRandomNumber())))

    private fun generateAppKeys() =
        listOf(
            AppKey(
                "AppKey 1",
                AppKeyIndex(0),
                NetKeyIndex(0),
                Key(generateRandomNumber())
            )
        )

    fun randomAppKey(appKeyIndex: Int, netKeyIndex: Int) =
        AppKey(
            "AppKey $appKeyIndex",
            AppKeyIndex(appKeyIndex),
            NetKeyIndex(netKeyIndex),
            Key(generateRandomNumber())
        )

    fun randomNetKey(appKeyIndex: Int, netKeyIndex: Int) =
        NetKey(
            "NetKey $netKeyIndex",
            NetKeyIndex(netKeyIndex),
            key = Key(generateRandomNumber())
        )
}