package com.technocreatives.beckon.mesh.data

import android.annotation.SuppressLint
import no.nordicsemi.android.mesh.ApplicationKey
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.NetworkKey
import no.nordicsemi.android.mesh.NodeKey
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.mesh.transport.PublicationSettings
import no.nordicsemi.android.mesh.utils.MeshAddress
import no.nordicsemi.android.mesh.utils.NetworkTransmitSettings
import no.nordicsemi.android.mesh.utils.RelaySettings
import java.util.*
import no.nordicsemi.android.mesh.Features as NrfFeatures
import no.nordicsemi.android.mesh.Group as NrfGroup
import no.nordicsemi.android.mesh.Provisioner as NrfProvisioner
import no.nordicsemi.android.mesh.transport.Element as NrfElement
import no.nordicsemi.android.mesh.transport.MeshModel as NrfMeshModel

fun MeshNetwork.transform() = MeshConfig(
    MeshConfigHelper.SCHEMA,
    MeshConfigHelper.ID,
    version,
    UUID.fromString(meshUUID),
    meshName,
    timestamp,
    isPartial,
    provisioners.map { it.transform() },
    netKeys(),
    appKeys(),
    nodes.map { it.transform() },
    groups.map { it.transform() },
    emptyList(), // TODO support scene
    networkExclusions = networkExclusions.map { it.transform() }
)

fun MeshNetwork.findAppKey(index: AppKeyIndex) =
    appKeys.find { index.value == it.keyIndex }

fun MeshNetwork.findNetKey(index: NetKeyIndex) =
    netKeys.find { index.value == it.keyIndex }

fun MeshNetwork.findNode(id: NodeId) =
    nodes.find { it.uuid == id.uuid.toString() }

fun MeshNetwork.appKeys() =
    appKeys.map { it.transform() }

fun MeshNetwork.netKeys() =
    netKeys.map { it.transform() }

fun ApplicationKey.transform(): AppKey = AppKey(
    name, AppKeyIndex(keyIndex), NetKeyIndex(boundNetKeyIndex), Key(key)
)

fun NetworkKey.transform(): NetKey = NetKey(
    name,
    NetKeyIndex(keyIndex),
    phase,
    Key(key),
    isMinSecurity,
    oldKey?.let { Key(it) },
    timestamp
)

fun NrfMeshModel.transform(): Model =
    ModelData(
        ModelId(modelId),
        bind = boundAppKeyIndexes.map { AppKeyIndex(it) },
        subscribe = subscribedAddresses.map { it.toSubscriptionAddress(this) },
        publish = publicationSettings?.transform()
    ).toModel()

fun PublicationSettings.transform() = Publish(
    GroupAddress(publishAddress),
    AppKeyIndex(appKeyIndex),
    publishTtl,
    Period(
        publicationSteps,
        PublicationResolution.valueOf(publicationResolution)
    ),
    credentialFlag,
    Retransmit(
        publishRetransmitCount,
        retransmissionInterval
    )
)

fun NrfElement.transform(index: ElementIndex) = Element(
    name,
    index,
    locationDescriptor,
    meshModels.map { it.value.transform() }
)

fun Map<Int, NrfElement>.transform(nodeAddress: UnicastAddress): List<Element> =
    map { it.value.transform(ElementIndex(it.key - nodeAddress.value)) }

@SuppressLint("RestrictedApi")
fun NrfProvisioner.transform() = Provisioner(
    provisionerName,
    NodeId(UUID.fromString(provisionerUuid)),
    allocatedUnicastRanges.map {
        AddressRange(
            AddressValue(it.lowAddress),
            AddressValue(it.highAddress)
        )
    },
    allocatedGroupRanges.map {
        AddressRange(
            AddressValue(it.lowAddress),
            AddressValue(it.highAddress)
        )
    },
    allocatedSceneRanges.map {
        SceneRange(
            AddressValue(it.firstScene),
            AddressValue(it.lastScene)
        )
    },
)

fun ProvisionedMeshNode.transform(): Node {
    val nodeAddress = UnicastAddress(unicastAddress)

    return Node(
        NodeId(UUID.fromString(uuid)),
        nodeAddress,
        deviceKey?.let { Key(it) },
        security,
        addedNetKeys.map { it.toNetKey() },
        isConfigured,
        nodeName,
        companyIdentifier = companyIdentifier,
        productIdentifier = productIdentifier,
        versionIdentifier = versionIdentifier,
        crpl = crpl,
        nodeFeatures?.transform(),
        secureNetworkBeacon = isSecureNetworkBeaconSupported,
        ttl,
        networkTransmitSettings?.transform(),
        relaySettings?.transform(),
        addedAppKeys.map { it.toAppKey() },
        elements.transform(nodeAddress),
        isExcluded,
        null,
        null
    )
}

fun NetworkTransmitSettings.transform() = NetworkTransmit(
    transmissions, networkTransmissionInterval
)

fun RelaySettings.transform() = RelayRetransmit(
    relayTransmitCount, retransmissionIntervals
)

fun NrfFeatures.transform() = Features(
    relay, proxy, lowPower, friend
)

@SuppressLint("RestrictedApi")
fun NodeKey.toNetKey() = NodeNetKey(
    NetKeyIndex(index),
    isUpdated
)

@SuppressLint("RestrictedApi")
fun NodeKey.toAppKey() = NodeAppKey(
    AppKeyIndex(index),
    isUpdated
)

fun NrfGroup.transform() = Group(
    name, GroupAddress(address), GroupAddress(parentAddress),
)

fun Int.toSubscriptionAddress(model: NrfMeshModel): SubscriptionAddress =
    if (MeshAddress.isValidVirtualAddress(this)) {
        val uuid = model.getLabelUUID(this)
        VirtualAddress(uuid)
    } else {
        GroupAddress(this)
    }

fun MeshNetwork.proxyFilter(): ProxyFilter? =
    proxyFilter?.transform()

fun Map.Entry<Int, List<Int>>.transform(): NetworkExclusion = NetworkExclusion(
    key,
    value.map { UnicastAddress(it) }
)