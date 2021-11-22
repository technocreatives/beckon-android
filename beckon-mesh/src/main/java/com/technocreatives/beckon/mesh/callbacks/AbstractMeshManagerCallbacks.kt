package com.technocreatives.beckon.mesh.callbacks

import com.technocreatives.beckon.Mtu
import no.nordicsemi.android.mesh.MeshManagerCallbacks
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import timber.log.Timber

abstract class AbstractMeshManagerCallbacks : MeshManagerCallbacks {
    override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
        TODO("Callback is not implemented: onNetworkLoaded")
    }


    override fun onNetworkLoadFailed(error: String?) {
        TODO("Callback is not implemented: onNetworkLoadFailed: $error")
    }

    override fun onNetworkImported(meshNetwork: MeshNetwork?) {
        TODO("Callback is not implemented: onNetworkImported")
    }

    override fun onNetworkImportFailed(error: String?) {
        TODO("Callback is not implemented: onNetworkImportFailed $error")
    }

    override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
        TODO("Callback is not implemented: onNetworkUpdated")
    }

    override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
        TODO("Callback is not implemented: sendProvisioningPdu")
    }

    override fun onMeshPduCreated(pdu: ByteArray) {
        Timber.w("onMeshPduCreated in incorrect state")
    }

    override fun getMtu(): Int {
        Timber.w("getMTU in incorrect state")
        return Mtu.MAX.toInt()
    }
}
