package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.extensions.nextAvailableUnicastAddress
import com.technocreatives.beckon.mesh.extensions.onDisconnect
import com.technocreatives.beckon.mesh.model.Node
import com.technocreatives.beckon.mesh.model.UnprovisionedNode
import com.technocreatives.beckon.mesh.model.UnprovisionedScanResult
import com.technocreatives.beckon.mesh.utils.tapLeft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.MeshProvisioningStatusCallbacks
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import timber.log.Timber

// provisioning phase
// TODO remove failed provisioning device to avoid conflict unicast address
class Provisioning(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val beckonDevice: BeckonDevice,
) : MeshState(beckonMesh, meshApi) {

    private val inviteEmitter =
        CompletableDeferred<Either<ProvisioningError.ProvisioningFailed, UnprovisionedNode>>()

    private val provisioningEmitter =
        CompletableDeferred<Either<ProvisioningError, Node>>()

    private val provisioningStatusCallbacks = object : MeshProvisioningStatusCallbacks {

        var accumulatedStates = emptyList<ProvisioningState.States>()

        override fun onProvisioningStateChanged(
            meshNode: UnprovisionedMeshNode,
            state: ProvisioningState.States,
            data: ByteArray?
        ) {
            Timber.d("onProvisioningStateChanged: ${meshNode.nodeName} $state, data: ${data?.size}")
            accumulatedStates = accumulatedStates + state
            when (state) {
                ProvisioningState.States.PROVISIONING_CAPABILITIES -> {
                    inviteEmitter.complete(UnprovisionedNode(meshNode).right())
                }
                else -> {
                    Timber.d("Unprocessed state: $state")
                }
            }
        }

        override fun onProvisioningFailed(
            meshNode: UnprovisionedMeshNode?,
            state: ProvisioningState.States?,
            data: ByteArray?
        ) {
            val failed = ProvisioningError.ProvisioningFailed(meshNode, state, data).left()
            if (accumulatedStates.isInviting()) {
                inviteEmitter.complete(failed)
            } else {
                provisioningEmitter.complete(failed)
            }
        }

        override fun onProvisioningCompleted(
            meshNode: ProvisionedMeshNode,
            state: ProvisioningState.States,
            data: ByteArray?
        ) {
            Timber.d("onProvisioningCompleted: ${meshNode.nodeName} - $state - ${accumulatedStates.size} - ${accumulatedStates.map { it.name }}")
            provisioningEmitter.complete(
                Node(
                    meshNode,
                    beckonMesh.appKeys(),
                    beckonMesh.networkKeys()
                ).right()
            )
            beckonMesh.execute {
                meshApi.updateNetwork()
            }
        }
    }

    private var disconnectJob: Job? = null

    init {
        meshApi.setProvisioningStatusCallbacks(provisioningStatusCallbacks)
        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
            override fun onMeshPduCreated(pdu: ByteArray) {
                Timber.d("sendPdu - onMeshPduCreated - ${pdu.info()}")
                beckonMesh.execute {
                    with(meshApi) {
                        beckonDevice.sendPdu(pdu, MeshConstants.proxyDataInCharacteristic).fold(
                            { Timber.w("SendPdu error: $it") },
                            { Timber.d("sendPdu success") }
                        )
                    }
                }
            }

            override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
                Timber.d("onNetworkUpdated")
                runBlocking {
                    meshApi.updateNetwork()
                }
            }

            override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
                Timber.d("sendPdu - provisioningProcess - ${pdu.size}")
                beckonMesh.execute {
                    with(meshApi) {
                        beckonDevice.sendPdu(pdu, MeshConstants.provisioningDataInCharacteristic)
                            .fold(
                                { Timber.w("SendPdu provisioningProcess error: $it") },
                                { Timber.d("sendPdu provisioningProcess success") }
                            )
                    }
                }
            }

            override fun getMtu(): Int {
                return beckonDevice.getMaximumPacketSize()
            }
        })
        disconnectJob = beckonMesh.execute {
            beckonDevice.onDisconnect {
                beckonMesh.updateState(Loaded(beckonMesh, meshApi))
            }
        }
    }

    // todo think about it?
    // disconnect device if needed
    // change state of MeshManagerApi
    suspend fun cancel(): Either<ProvisioningError.BleDisconnectError, Loaded> = either {
        disconnectJob?.cancel()
        beckonDevice.disconnect().mapLeft { ProvisioningError.BleDisconnectError(it) }.bind()
        val loaded = Loaded(beckonMesh, meshApi)
        beckonMesh.updateState(loaded)
        loaded
    }

    suspend fun identify(
        scanResult: UnprovisionedScanResult,
        attentionTimer: Int
    ): Either<ProvisioningError.ProvisioningFailed, UnprovisionedNode> {
        meshApi.identifyNode(scanResult.uuid, attentionTimer)
        return inviteEmitter.await()
    }

    suspend fun startProvisioning(unprovisionedNode: UnprovisionedNode): Either<ProvisioningError, Node> =
        either {
            Timber.d("startProvisioning ${unprovisionedNode.node.deviceUuid}")
            meshApi.nextAvailableUnicastAddress(unprovisionedNode.node).tapLeft {
                provisioningEmitter.complete(
                    it.left()
                )
            }
            meshApi.startProvisioning(unprovisionedNode.node)
            val node = provisioningEmitter.await().bind()
            beckonDevice.disconnect().mapLeft { ProvisioningError.BleDisconnectError(it) }.bind()
            disconnectJob?.cancel()
            beckonMesh.updateState(Loaded(beckonMesh, meshApi))
            node
        }

    fun List<ProvisioningState.States>.isInviting(): Boolean {
        // todo correct this formula
        return this.size <= 1
    }
}