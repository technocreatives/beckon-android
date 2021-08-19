package com.technocreatives.beckon.mesh

import com.technocreatives.beckon.mesh.state.MeshState
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode

sealed interface MeshError

data class IllegalMeshStateError(val state: MeshState) : MeshError, Exception()

data class MeshLoadFailedError(val error: String) : MeshError

sealed class ProvisioningError : MeshError {

    data class ProvisioningFailed(
        val node: UnprovisionedMeshNode?,
        val state: ProvisioningState.States?,
        val data: ByteArray?
    ) : ProvisioningError()

    object NoAvailableUnicastAddress : ProvisioningError()
    object NoAllocatedUnicastRange : ProvisioningError()
}

sealed class CreateMeshPduError: MeshError {
    data class InvalidAddress(val dst: Int): CreateMeshPduError()
    object LabelUuidUnavailable : CreateMeshPduError()
    object ProvisionerAddressNotSet : CreateMeshPduError()
}