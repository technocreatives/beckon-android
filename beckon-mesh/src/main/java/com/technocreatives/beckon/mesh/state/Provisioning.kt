package com.technocreatives.beckon.mesh.state

import android.annotation.SuppressLint
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.extensions.findProxyDevice
import com.technocreatives.beckon.mesh.extensions.nextAvailableUnicastAddress
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.model.Node
import com.technocreatives.beckon.mesh.model.UnprovisionedNode
import com.technocreatives.beckon.mesh.model.UnprovisionedScanResult
import com.technocreatives.beckon.mesh.utils.tap
import com.technocreatives.beckon.util.filterZ
import com.technocreatives.beckon.util.mapEither
import com.technocreatives.beckon.util.mapZ
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import no.nordicsemi.android.mesh.ApplicationKey
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.MeshProvisioningStatusCallbacks
import no.nordicsemi.android.mesh.NetworkKey
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.*
import timber.log.Timber

// provisioning phase
// TODO remove failed provisioning device to avoid conflict unicast address
class Provisioning(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val appKey: ApplicationKey
) : MeshState(beckonMesh, meshApi) {

    private val inviteEmitter =
        CompletableDeferred<Either<ProvisioningError.ProvisioningFailed, UnprovisionedNode>>()

    private val provisioningEmitter =
        CompletableDeferred<Either<ProvisioningError, Node>>()

    private val exchangeKeysEmitter =
        CompletableDeferred<Either<Unit, Node>>()

    private var unicast = -1

    private val provisioningStatusCallbacks = object : MeshProvisioningStatusCallbacks {

        var accumulatedStates = emptyList<ProvisioningState.States>()

        override fun onProvisioningStateChanged(
            meshNode: UnprovisionedMeshNode,
            state: ProvisioningState.States,
            data: ByteArray?
        ) {
            Timber.d("onProvisioningStateChanged: ${meshNode.nodeName} $state, data: ${data?.size}")
            // todo check provisioning failed state
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
            Timber.d("onProvisioningCompleted: ${meshNode.nodeName} $state")
            Timber.d("onProvisioningCompleted: ${accumulatedStates.size} - ${accumulatedStates.map { it.name }}")
            provisioningEmitter.complete(Node(meshNode).right())
            beckonMesh.execute {
                meshApi.updateNodes()
            }
        }
    }

    private var beckonDevice: BeckonDevice? = null

    init {
        meshApi.setProvisioningStatusCallbacks(provisioningStatusCallbacks)
        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
            override fun onMeshPduCreated(pdu: ByteArray) {
                Timber.d("sendPdu - onMeshPduCreated - ${pdu.info()}")
                beckonDevice?.let { bd ->
                    beckonMesh.execute {
                        with(meshApi) {
                            bd.sendPdu(pdu, MeshConstants.proxyDataInCharacteristic).fold(
                                { Timber.w("SendPdu error: $it") },
                                { Timber.d("sendPdu success") }
                            )
                        }
                    }
                }
            }

            override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
                Timber.d("onNetworkUpdated")
                meshApi.loadNodes()
            }

            override fun sendProvisioningPdu(meshNode: UnprovisionedMeshNode, pdu: ByteArray) {
                Timber.d("sendPdu - sendProvisioningPdu - ${pdu.size}")
                beckonDevice?.let { bd ->
                    beckonMesh.execute {
                        with(meshApi) {
                            bd.sendPdu(pdu, MeshConstants.provisioningDataInCharacteristic).fold(
                                { Timber.w("SendPdu error: $it") },
                                { Timber.d("sendPdu success") }
                            )
                        }
                    }
                }
            }

            override fun getMtu(): Int {
                return beckonDevice?.getMaximumPacketSize() ?: 66
            }
        })

        meshApi.setMeshStatusCallbacks(object : AbstractMessageStatusCallbacks(meshApi) {
            override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
                super.onMeshMessageReceived(src, meshMessage)
                Timber.w("onMeshMessageReceived - src: $src, dst: ${meshMessage.dst}, meshMessage: ${meshMessage.sequenceNumber()}, instance: ${meshMessage.javaClass}")
                handleMessageReceived(src, meshMessage)
            }

            override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
                Timber.w("onMeshMessageProcessed - src: ${meshMessage.src}, dst: $dst,  sequenceNumber: ${meshMessage.sequenceNumber()}")
            }

            override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
                Timber.w("onBlockAcknowledgementProcessed - src: ${message.src}, dst: $dst, sequenceNumber: ${message.sequenceNumber.toInt()}")
            }

            override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
                Timber.w("onBlockAcknowledgementReceived - src: $src, dst: ${message.dst}, sequenceNumber: ${message.sequenceNumber.toInt()}")
            }
        })
    }

    // todo think about it?
    // disconnect device if needed
    // change state of MeshManagerApi
    suspend fun cancel(): Either<Throwable, Unit> = either {
        beckonDevice?.disconnect()?.bind()
        beckonMesh.updateState(Loaded(beckonMesh, meshApi))
    }

    suspend fun connect(scanResult: UnprovisionedScanResult): Either<BeckonError, BeckonDevice> {
        return beckonMesh.connectForProvisioning(scanResult)
            .tap {
                beckonDevice = it
            }
    }

    suspend fun identify(
        scanResult: UnprovisionedScanResult,
        attentionTimer: Int
    ): Either<ProvisioningError.ProvisioningFailed, UnprovisionedNode> {
        meshApi.identifyNode(scanResult.uuid, attentionTimer)
        return inviteEmitter.await()
    }

    suspend fun startProvisioning(unprovisionedMeshNode: UnprovisionedNode): Either<ProvisioningError, Node> {
        Timber.d("startProvisioning ${unprovisionedMeshNode.node.deviceUuid}")
        meshApi.nextAvailableUnicastAddress(unprovisionedMeshNode.node).fold({
            provisioningEmitter.complete(
                it.left()
            )
        }, { unicast = it })
        meshApi.startProvisioning(unprovisionedMeshNode.node)
        return provisioningEmitter.await()
    }

    suspend fun scanAndConnect(node: Node): Either<BeckonError, BeckonDevice> {
        // TODO Won't stop if we don't find device?
        return beckonMesh.scanForProxy()
            .mapZ {
                it.firstOrNull {
                    meshApi.findProxyDevice(it.scanRecord!!, node.node) { beckonMesh.stopScan() }
                }
            }.filterZ { it != null }
            .mapEither { beckonMesh.connectForProxy(it!!.macAddress) }
            .first()
            .tap {
                beckonDevice = it
            }
    }

    suspend fun exchangeKeys(
        beckonDevice: BeckonDevice,
        node: Node
    ): Either<Unit, Node> {
        val compositionDataGet = ConfigCompositionDataGet()
        Timber.d("createMeshPdu ConfigCompositionDataGet ${node.sequenceNumber}")
        meshApi.createMeshPdu(node.unicastAddress, compositionDataGet)
        return exchangeKeysEmitter.await().tap {
            beckonMesh.updateState(Connected(beckonMesh, meshApi, beckonDevice))
        }
    }

    fun List<ProvisioningState.States>.isInviting(): Boolean {
        // todo correct this formula
        return this.size <= 1
    }

    @SuppressLint("RestrictedApi")
    fun handleMessageReceived(src: Int, meshMessage: MeshMessage) {
        if (src != unicast) {
            Timber.w("onMessageReceived src is not our current provisioning device")
            return
        }
        val meshNetwork = meshApi.meshNetwork!!
        val node = meshNetwork.getNode(src)!!
        when (meshMessage.opCode) {
            ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt() -> {
                Timber.d("onMessageReceived CONFIG_COMPOSITION_DATA_STATUS:")
                beckonMesh.execute {
                    // TODO delay
                    delay(500)

                    val configDefaultTtlGet = ConfigDefaultTtlGet()
                    Timber.d("createMeshPdu ConfigDefaultTtlGet ${node.sequenceNumber}")
                    meshApi.createMeshPdu(
                        node.unicastAddress,
                        configDefaultTtlGet
                    )
                }
            }
            ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS -> {
                val status = meshMessage as ConfigDefaultTtlStatus
                Timber.d("onMessageReceived CONFIG_DEFAULT_TTL_STATUS: $status")
                beckonMesh.execute {
                    // TODO delay
                    delay(1500)
                    val networkTransmitSet = ConfigNetworkTransmitSet(2, 1)
                    Timber.d("createMeshPdu ConfigNetworkTransmitSet ${node.sequenceNumber}")
                    meshApi.createMeshPdu(
                        node.unicastAddress,
                        networkTransmitSet
                    )
                }
            }
            ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS -> {
                Timber.d("onMessageReceived CONFIG_NETWORK_TRANSMIT_STATUS")
                beckonMesh.execute {
                    // TODO delay global
                    delay(1500)
                    val index: Int = node.addedNetKeys!!.get(0)!!.index
                    val networkKey: NetworkKey = meshNetwork.netKeys[index]
                    val configAppKeyAdd = ConfigAppKeyAdd(networkKey, appKey)
                    Timber.d("createMeshPdu ConfigAppKeyAdd ${node.sequenceNumber}")
                    meshApi.createMeshPdu(
                        node.unicastAddress,
                        configAppKeyAdd
                    )
                }
            }
            ConfigMessageOpCodes.CONFIG_APPKEY_STATUS -> {
                val status = meshMessage as ConfigAppKeyStatus
                Timber.d("onMessageReceived CONFIG_APPKEY_STATUS: ${status.isSuccessful}")
                exchangeKeysEmitter.complete(Node(node).right())
            }
            else -> {
                Timber.d("onMessageReceived other message when provisioning $src, $meshMessage")
                exchangeKeysEmitter.complete(Unit.left())
            }
        }
    }
}