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
import com.technocreatives.beckon.mesh.extensions.isProxyDevice
import com.technocreatives.beckon.mesh.extensions.info
import com.technocreatives.beckon.mesh.extensions.nextAvailableUnicastAddress
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
                Timber.d("sendPdu - provisioningProcess - ${pdu.size}")
                beckonDevice?.let { bd ->
                    beckonMesh.execute {
                        with(meshApi) {
                            bd.sendPdu(pdu, MeshConstants.provisioningDataInCharacteristic).fold(
                                { Timber.w("SendPdu provisioningProcess error: $it") },
                                { Timber.d("sendPdu provisioningProcess success") }
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
            override fun onMeshMessageReceived(src: Int, message: MeshMessage) {
                super.onMeshMessageReceived(src, message)
                Timber.w("onMeshMessageReceived ${message.info()}")
                handleMessageReceived(src, message)
            }

            override fun onMeshMessageProcessed(dst: Int, message: MeshMessage) {
                Timber.w("onMeshMessageProcessed ${message.info()}")
            }

            override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
                Timber.w("onBlockAcknowledgementProcessed ${message.info()}")
            }

            override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
                Timber.w("onBlockAcknowledgementReceived ${message.info()}")
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

    suspend fun startProvisioning(unprovisionedNode: UnprovisionedNode): Either<ProvisioningError, Node> {
        Timber.d("startProvisioning ${unprovisionedNode.node.deviceUuid}")
        meshApi.nextAvailableUnicastAddress(unprovisionedNode.node).fold({
            provisioningEmitter.complete(
                it.left()
            )
        }, { unicast = it })
        meshApi.startProvisioning(unprovisionedNode.node)
        return provisioningEmitter.await()
    }

    suspend fun scanAndConnect(node: Node): Either<BeckonError, BeckonDevice> {
        // TODO Won't stop if we don't find device?
        return beckonMesh.scanForProxy()
            .mapZ {
                it.firstOrNull {
                    // TODO what if device is not proxy device? We do not need to connect to the current device.
                    meshApi.isProxyDevice(it.scanRecord!!, node.node) { beckonMesh.stopScan() }
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
        Timber.d("createMeshPdu ${compositionDataGet.opCode} ${compositionDataGet.info()}")
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
    fun handleMessageReceived(src: Int, message: MeshMessage) {
        if (src != unicast) {
            Timber.w("onMessageReceived src is not our current provisioning device")
            return
        }
        val meshNetwork = meshApi.meshNetwork!!
        val node = meshNetwork.getNode(src)!!
        when (message.opCode) {
            ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt() -> {
                Timber.d("onMessageReceived CONFIG_COMPOSITION_DATA_STATUS:")
                beckonMesh.execute {
                    // TODO delay
                    delay(500)

                    val configDefaultTtlGet = ConfigDefaultTtlGet()
                    Timber.d("createMeshPdu ConfigDefaultTtlGet ${configDefaultTtlGet.info()}")
                    meshApi.createMeshPdu(
                        node.unicastAddress,
                        configDefaultTtlGet
                    )
                }
            }
            ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS -> {
                val status = message as ConfigDefaultTtlStatus
                Timber.d("onMessageReceived CONFIG_DEFAULT_TTL_STATUS: $status")
                beckonMesh.execute {
                    // TODO delay
                    delay(1500)
                    val networkTransmitSet = ConfigNetworkTransmitSet(2, 1)
                    Timber.d("createMeshPdu ConfigNetworkTransmitSet ${networkTransmitSet.info()}")
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
                    Timber.d("createMeshPdu ConfigAppKeyAdd ${configAppKeyAdd.info()}")
                    meshApi.createMeshPdu(
                        node.unicastAddress,
                        configAppKeyAdd
                    )
                }
            }
            ConfigMessageOpCodes.CONFIG_APPKEY_STATUS -> {
                val status = message as ConfigAppKeyStatus
                Timber.d("onMessageReceived CONFIG_APPKEY_STATUS: ${status.isSuccessful}")
                exchangeKeysEmitter.complete(Node(node).right())
            }
            else -> {
                Timber.d("onMessageReceived other message when provisioning $src, $message")
                exchangeKeysEmitter.complete(Unit.left())
            }
        }
    }
}