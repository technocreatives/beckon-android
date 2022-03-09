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
    netKeys(),
    appKeys(),
    provisioners.map { it.transform() },
    nodes.map { it.transform() },
    groups.map { it.transform() },
    emptyList(), // TODO support scene
    emptyList() // TODO support NetworkExclusion
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
    name, NetKeyIndex(keyIndex), Key(key), phase, isMinSecurity, timestamp
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
    Period(
        publicationSteps,
        publicationResolution
    ),
    credentialFlag,
    publishTtl,
    Retransmit(
        publishRetransmitCount,
        retransmissionInterval
    )
)

fun NrfElement.transform(index: ElementIndex) = Element(
    UnicastAddress(elementAddress),
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
    UUID.fromString(provisionerUuid),
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
    isLastSelected,
)

fun ProvisionedMeshNode.transform(): Node {
    val nodeAddress = UnicastAddress(unicastAddress)

    return Node(
        NodeId(UUID.fromString(uuid)),
        nodeName,
        deviceKey?.let { Key(it) },
        nodeAddress,
        security,
        isConfigured,
        companyIdentifier = companyIdentifier,
        productIdentifier = productIdentifier,
        versionIdentifier = versionIdentifier,
        crpl = crpl,
        nodeFeatures?.transform(),
        ttl,
        isExcluded,
        networkTransmitSettings?.transform(),
        relaySettings?.transform(),
        addedNetKeys.map { it.toNetKey() },
        addedAppKeys.map { it.toAppKey() },
        elements.transform(nodeAddress),
        sequenceNumber,
    )
}

fun NetworkTransmitSettings.transform() = NetworkTransmit(
    networkTransmitCount, networkIntervalSteps
)

fun RelaySettings.transform() = RelayRetransmit(
    relayTransmitCount, relayIntervalSteps
)

fun NrfFeatures.transform() = Features(
    friend, lowPower, proxy, relay
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