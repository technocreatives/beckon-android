package com.technocreatives.beckon.mesh.callbacks

import com.technocreatives.beckon.mesh.blockingEmit
import com.technocreatives.beckon.mesh.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.mesh.MeshProvisioningStatusCallbacks
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import timber.log.Timber

class ProvisioningStatusCallbacks : MeshProvisioningStatusCallbacks {

    private val subject by lazy {
        MutableSharedFlow<ProvisioningStatus>(1)
    }

    fun status(): Flow<ProvisioningStatus> =
        subject.asSharedFlow()


    override fun onProvisioningStateChanged(
        meshNode: UnprovisionedMeshNode?,
        state: ProvisioningState.States?,
        data: ByteArray?
    ) {
        Timber.d("onProvisioningStateChanged with node ${meshNode?.debug()}")
        subject.blockingEmit(ProvisioningStatus.StateChanged(meshNode, state, data))
    }

    override fun onProvisioningFailed(
        meshNode: UnprovisionedMeshNode?,
        state: ProvisioningState.States?,
        data: ByteArray?
    ) {
        Timber.d("onProvisioningFailed with node ${meshNode?.debug()}")
        subject.blockingEmit(ProvisioningStatus.Failed(meshNode, state, data))
    }

    override fun onProvisioningCompleted(
        meshNode: ProvisionedMeshNode?,
        state: ProvisioningState.States?,
        data: ByteArray?
    ) {
        Timber.d("onProvisioningCompleted with node ${meshNode?.debug()}")
        subject.blockingEmit(ProvisioningStatus.Completed(meshNode, state, data))
    }

}

sealed class ProvisioningStatus {
    data class StateChanged(
        val node: UnprovisionedMeshNode?,
        val state: ProvisioningState.States?,
        val data: ByteArray?
    ) : ProvisioningStatus()

    data class Failed(
        val node: UnprovisionedMeshNode?,
        val state: ProvisioningState.States?,
        val data: ByteArray?
    ) : ProvisioningStatus()

    data class Completed(
        val node: ProvisionedMeshNode?,
        val state: ProvisioningState.States?,
        val data: ByteArray?
    ) : ProvisioningStatus()
}