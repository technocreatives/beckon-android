package com.technocreatives.beckon.mesh

sealed class Mesh(val mesh: BeckonMeshManagerApi)

class Unloaded(mesh: BeckonMeshManagerApi) : Mesh(mesh) {
    suspend fun load() {
        mesh.loadMeshNetwork()
    }
}

class Loaded(mesh: BeckonMeshManagerApi) : Mesh(mesh) {
    suspend fun connected(): Connected {
        return Connected(mesh)
    }
}

// connected to a provisioner
class Connected(mesh: BeckonMeshManagerApi) : Mesh(mesh) {
//    suspend fun proxy(): ProxyPhase {
//        return ProxyPhase(mesh)
//    }

    suspend fun disconnect(): Loaded {
        return Loaded(mesh)
    }

    suspend fun provision(): ProvisioningPhase {
        return ProvisioningPhase(mesh)
    }

}

// ???
// scan & connect to provisioner
//class Connecting(mesh: BeckonMeshManagerApi) : Mesh(mesh) {

//class ProxyPhase(mesh: BeckonMeshManagerApi) : Mesh(mesh) {
//    suspend fun cancel(): Loaded {
//        return Loaded(mesh)
//    }
//}

// scan
// connect as a proxy device
// identify
// provision
// scan again
// exchange messages
// complete
class ProvisioningPhase(mesh: BeckonMeshManagerApi) : Mesh(mesh) {
    suspend fun cancel(): Loaded {
        return Loaded(mesh)
    }
}