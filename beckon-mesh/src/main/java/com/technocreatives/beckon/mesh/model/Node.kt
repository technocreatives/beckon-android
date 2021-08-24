package com.technocreatives.beckon.mesh.model

import com.technocreatives.beckon.internal.toUuid
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode


class Node(internal val node: ProvisionedMeshNode) {
    val unicastAddress: Int
        get() = node.unicastAddress

    val elements get() = node.elements.map { Element(it.value!!, it.key) }
    val sequenceNumber get() = node.sequenceNumber

    val uuid get() = node.uuid.toUuid()

    val name = node.nodeName

    val versionIdentifier get() = node.versionIdentifier

    val appKeys get() = node.addedAppKeys.map { NodeAppKey(it) }
    val netKeys get() = node.addedNetKeys.map { NodeNetworkKey(it) }
}

