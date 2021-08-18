package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode

class Node(private val node: ProvisionedMeshNode) {
    val unicastAddress: Int
        get() = node.unicastAddress

    val elements get() = node.elements
}
