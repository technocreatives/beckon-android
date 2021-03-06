package com.technocreatives.beckon.mesh.model

import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode

class UnprovisionedNode(internal val node: UnprovisionedMeshNode) {
    val uuid get() = node.deviceUuid
    var name: String
        set(value) {
            node.nodeName = value
        }
        get() = node.nodeName

}