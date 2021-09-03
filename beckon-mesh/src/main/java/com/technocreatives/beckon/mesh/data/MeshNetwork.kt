package com.technocreatives.beckon.mesh.data

import android.annotation.SuppressLint
import no.nordicsemi.android.mesh.ApplicationKey
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.NetworkKey
import no.nordicsemi.android.mesh.NodeKey
import no.nordicsemi.android.mesh.Provisioner as NrfProvisioner
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.mesh.utils.CompositionDataParser
import no.nordicsemi.android.mesh.utils.MeshAddress
import no.nordicsemi.android.mesh.utils.NetworkTransmitSettings
import java.util.*
import no.nordicsemi.android.mesh.transport.MeshModel as NrfMeshModel
import no.nordicsemi.android.mesh.transport.Element as NrfElement
import no.nordicsemi.android.mesh.Features as NrfFeatures
import no.nordicsemi.android.mesh.Group as NrfGroup

fun MeshNetwork.transform() = Mesh(
    "",
    "",
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
    emptyList(), // TODO support scense
    emptyList() // TODO support NetworkExclusion
)

fun MeshNetwork.findAppKey(index: AppKeyIndex) =
    appKeys.find { index.value == it.keyIndex }

fun MeshNetwork.findNetKey(index: NetKeyIndex) =
    netKeys.find { index.value == it.keyIndex }

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
        subscribe = subscribedAddresses.map { it.toSubscriptionAddress(this) }
    ).toModel()

fun NrfElement.transform(index: ElementIndex) = Element(
    UnicastAddress(elementAddress),
    name,
    index,
    locationDescriptor,
    meshModels.map { it.value.transform() }
)

@SuppressLint("RestrictedApi")
fun NrfProvisioner.transform() = Provisioner(
    provisionerName, UUID.fromString(meshUuid),
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

fun ProvisionedMeshNode.transform() = Node(
    NodeId(UUID.fromString(uuid)),
    nodeName,
    Key(deviceKey),
    UnicastAddress(unicastAddress),
    security,
    isConfigured,
    cid = CompositionDataParser.formatCompanyIdentifier(companyIdentifier, false),
    pid = CompositionDataParser.formatProductIdentifier(productIdentifier, false),
    vid = CompositionDataParser.formatProductIdentifier(versionIdentifier, false),
    crpl = CompositionDataParser.formatProductIdentifier(crpl, false),
    nodeFeatures.transform(),
    ttl,
    isExcluded,
    networkTransmitSettings?.transform(),
    addedNetKeys.map { it.toNetKey() },
    addedAppKeys.map { it.toAppKey() },
    elements.map { it.value.transform(ElementIndex(it.key)) },
    sequenceNumber,
    versionIdentifier
)

fun NetworkTransmitSettings.transform() = NetworkTransmit(
    networkTransmitCount, networkIntervalSteps
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