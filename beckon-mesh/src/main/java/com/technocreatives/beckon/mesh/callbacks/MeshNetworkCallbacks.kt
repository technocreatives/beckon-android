package com.technocreatives.beckon.mesh.callbacks

import com.technocreatives.beckon.Mtu
import com.technocreatives.beckon.mesh.blockingEmit
import com.technocreatives.beckon.mesh.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.mesh.MeshManagerCallbacks
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import timber.log.Timber

class MeshNetworkCallbacks(val queryMtu: () -> Mtu) : MeshManagerCallbacks {

    private val subject by lazy {
        MutableSharedFlow<MeshStatus>(1)
    }

    fun status(): Flow<MeshStatus> =
        subject.asSharedFlow()

    override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
        Timber.d("onNetworkLoaded mesh ${meshNetwork?.debug()}")
        val status = meshNetwork?.let { MeshStatus.Loaded(meshNetwork) } ?: MeshStatus.LoadedEmpty
        subject.blockingEmit(status)
    }

    override fun onNetworkUpdated(meshNetwork: MeshNetwork?) {
        Timber.d("onNetworkUpdated mesh ${meshNetwork?.debug()}")
        val status = meshNetwork?.let { MeshStatus.Updated(meshNetwork) } ?: MeshStatus.UpdatedEmpty
        subject.blockingEmit(status)
    }

    override fun onNetworkLoadFailed(error: String?) {
        Timber.w("onNetworkLoadFailed: $error")
        subject.blockingEmit(
            MeshStatus.LoadedFailed(
                error ?: "Network Loaded failed - error message is empty"
            )
        )
    }

    override fun onNetworkImported(meshNetwork: MeshNetwork?) {
        Timber.d("onNetworkImported mesh ${meshNetwork?.debug()}")
        val status =
            meshNetwork?.let { MeshStatus.Imported(meshNetwork) } ?: MeshStatus.ImportedEmpty
        subject.blockingEmit(status)
    }

    override fun onNetworkImportFailed(error: String?) {
        Timber.w("onNetworkLoadFailed: $error")
        subject.blockingEmit(
            MeshStatus.ImportFailed(
                error ?: "Network Loaded failed - error message is empty"
            )
        )
    }

    override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode?, pdu: ByteArray?) {
        Timber.d("sendProvisioningPdu with node ${meshNode?.debug()}, pdu: ${pdu?.toList()}")
        subject.blockingEmit(MeshStatus.SendProvisioningPdu(meshNode, pdu))
    }

    override fun onMeshPduCreated(pdu: ByteArray?) {
        Timber.d("onMeshPduCreated with pdu: ${pdu?.toList()}")
        subject.blockingEmit(MeshStatus.MeshPduCreated(pdu))
    }

    override fun getMtu(): Int {
        val value = queryMtu().value
        Timber.d("getMtu $value")
        return value
    }
}

sealed class MeshStatus {
    data class Loaded(val mesh: MeshNetwork) : MeshStatus()
    object LoadedEmpty : MeshStatus()
    data class Updated(val mesh: MeshNetwork) : MeshStatus()
    object UpdatedEmpty : MeshStatus()
    data class LoadedFailed(val error: String) : MeshStatus()
    data class Imported(val mesh: MeshNetwork) : MeshStatus()
    object ImportedEmpty : MeshStatus()
    data class ImportFailed(val error: String) : MeshStatus()
    data class SendProvisioningPdu(val node: UnprovisionedMeshNode?, val pdu: ByteArray?) :
        MeshStatus()

    data class MeshPduCreated(val pdu: ByteArray?) : MeshStatus()
}

