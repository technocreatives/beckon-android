package com.technocreatives.beckon.mesh

import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.mesh.state.MeshState
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import java.util.*

sealed interface MeshError

data class IllegalMeshStateError(val state: MeshState) : MeshError, Exception()

sealed interface MeshLoadError : MeshError

data class NetworkLoadFailedError(val id: UUID, val error: String) : MeshLoadError
data class CreateNetworkFailedError(val error: String) : MeshLoadError
data class NetworkImportedFailedError(val id: UUID, val error: String) : MeshLoadError
data class MeshIdNotFound(val id: UUID) : MeshLoadError
object NoCurrentMeshFound : MeshLoadError
data class BleDisconnectError(val throwable: Throwable) : MeshLoadError

sealed class ProvisioningError : MeshError {

    data class ProvisioningFailed(
        val node: UnprovisionedMeshNode?,
        val state: ProvisioningState.States?,
        val data: ByteArray?
    ) : ProvisioningError()

    object NoAvailableUnicastAddress : ProvisioningError()
    object NoAllocatedUnicastRange : ProvisioningError()
    data class BleDisconnectError(val throwable: Throwable) : ProvisioningError()
}

sealed interface SendMessageError : SendAckMessageError

data class InvalidAddress(val dst: Int) : SendMessageError
object LabelUuidUnavailable : SendMessageError
object ProvisionerAddressNotSet : SendMessageError
data class BleError(val error: BeckonActionError) : SendMessageError

sealed interface SendAckMessageError

object TimeoutError: SendAckMessageError