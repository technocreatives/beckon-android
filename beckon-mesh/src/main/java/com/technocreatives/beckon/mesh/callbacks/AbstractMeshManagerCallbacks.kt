package com.technocreatives.beckon.mesh.callbacks

import com.technocreatives.beckon.BeckonDevice
import no.nordicsemi.android.mesh.MeshManagerCallbacks
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode

abstract class AbstractMeshManagerCallbacks : MeshManagerCallbacks {
    override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
        TODO("Not yet implemented")
    }


    override fun onNetworkLoadFailed(error: String?) {
        TODO("Not yet implemented")
    }

    override fun onNetworkImported(meshNetwork: MeshNetwork?) {
        TODO("Not yet implemented")
    }

    override fun onNetworkImportFailed(error: String?) {
        TODO("Not yet implemented")
    }

    override fun onNetworkUpdated(meshNetwork: MeshNetwork?) {
        TODO("Not yet implemented")
    }

    override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun onMeshPduCreated(pdu: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun getMtu(): Int {
        TODO("Not yet implemented")
    }
}

class ConnectedCallbacks(val beckonDevice: BeckonDevice): AbstractMeshManagerCallbacks() {

}