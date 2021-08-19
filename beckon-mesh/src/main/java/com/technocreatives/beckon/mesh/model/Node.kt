package com.technocreatives.beckon.mesh.model

import com.technocreatives.beckon.internal.toUuid
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode

class Node(private val node: ProvisionedMeshNode) {
    val unicastAddress: Int
        get() = node.unicastAddress

    val elements get() = node.elements

    val uuid get() = node.uuid.toUuid()

    val name = node.nodeName

    val versionIdentifier get() = node.versionIdentifier

    val appKeys get() = node.addedAppKeys.map { NodeAppKey(it) }
    val netKeys get() = node.addedNetKeys.map { NodeNetworkKey(it) }
}
