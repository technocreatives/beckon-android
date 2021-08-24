package com.technocreatives.beckon.mesh.model

import android.annotation.SuppressLint
import com.technocreatives.beckon.internal.toUuid
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode

class Node(
    internal val node: ProvisionedMeshNode,
    val appKeys: List<AppKey>,
    val netKeys: List<NetworkKey>
) {
    val unicastAddress: Int
        get() = node.unicastAddress

    val elements get() = node.elements.map { Element(it.value!!, it.key, appKeys) }
    val sequenceNumber get() = node.sequenceNumber

    val uuid get() = node.uuid.toUuid()

    val name = node.nodeName

    val versionIdentifier get() = node.versionIdentifier

}

@SuppressLint("RestrictedApi")
fun ProvisionedMeshNode.toNode(appKeys: List<AppKey>, netKeys: List<NetworkKey>): Node =
    Node(this, addedAppKeys.mapNotNull { key -> appKeys.find { it.keyIndex == key.index } },
        addedNetKeys.mapNotNull { key -> netKeys.find { it.key.keyIndex == key.index } })