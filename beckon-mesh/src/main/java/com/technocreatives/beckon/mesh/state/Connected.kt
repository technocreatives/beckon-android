package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.extensions.onDisconnect
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.model.AppKey
import com.technocreatives.beckon.mesh.model.NetworkKey
import com.technocreatives.beckon.mesh.model.Node
import com.technocreatives.beckon.mesh.processor.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.ConfigAppKeyAdd
import no.nordicsemi.android.mesh.transport.ConfigNetworkTransmitSet
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber

class Connected(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val beckonDevice: BeckonDevice
) : MeshState(beckonMesh, meshApi) {

    private val processor: MessageProcessor = MessageProcessor(object : PduSender {
        override fun createPdu(
            dst: Int,
            meshMessage: MeshMessage
        ) = meshApi.createPdu(dst, meshMessage)

        override suspend fun sendPdu(pdu: Pdu): PduSenderResult =
            with(meshApi) {
                beckonDevice.sendPdu(pdu.data, MeshConstants.proxyDataInCharacteristic)
            }
    })

    val bearer = MessageBearer(processor)

    private var disconnectJob: Job? = null

    init {
        meshApi.setMeshManagerCallbacks(
            ConnectedMeshManagerCallbacks(
                beckonDevice,
                meshApi,
                processor
            )
        )
        meshApi.setMeshStatusCallbacks(
            ConnectedMessageStatusCallbacks(
                meshApi,
                processor
            )
        )
        with(processor) {
            beckonMesh.execute()
        }
        disconnectJob = beckonMesh.execute {
            beckonDevice.onDisconnect {
                beckonMesh.updateState(Loaded(beckonMesh, meshApi))
            }
        }
    }

    override suspend fun isValid(): Boolean = beckonMesh.isCurrentState<Connected>()

    suspend fun disconnect(): Either<BleDisconnectError, Loaded> = either {
        disconnectJob?.cancel()
        beckonDevice.disconnect().mapLeft { BleDisconnectError(it) }.bind()
        val loaded = Loaded(beckonMesh, meshApi)
        beckonMesh.updateState(loaded)
        loaded
    }

}


suspend fun Connected.setUpAppKey(
    node: Node,
    netKey: NetworkKey,
    appKey: AppKey,
): Either<Any, Unit> = either {

    bearer.getConfigCompositionData(node.unicastAddress).bind()

    bearer.getConfigDefaultTtl(node.unicastAddress).bind()

    val networkTransmitSet = ConfigNetworkTransmitSet(2, 1)

    bearer.setConfigNetworkTransmit(node.unicastAddress, networkTransmitSet).bind()

    val configAppKeyAdd = ConfigAppKeyAdd(netKey.actualKey, appKey.applicationKey)

    bearer.addConfigAppKey(node.unicastAddress, configAppKeyAdd).bind()
}

class ConnectedMeshManagerCallbacks(
    private val beckonDevice: BeckonDevice,
    private val meshApi: BeckonMeshManagerApi,
    private val processor: MessageProcessor,
) : AbstractMeshManagerCallbacks() {

    override fun onMeshPduCreated(pdu: ByteArray) {
        Timber.d("sendPdu - onMeshPduCreated - ${pdu.size}")
        runBlocking {
            processor.sendPdu(Pdu(pdu))
        }
    }

    override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
        runBlocking {
            meshApi.updateNetwork()
        }
    }

    override fun getMtu(): Int {
        return beckonDevice.getMaximumPacketSize()
    }
}

class ConnectedMessageStatusCallbacks(
    meshApi: BeckonMeshManagerApi,
    private val processor: MessageProcessor
) : AbstractMessageStatusCallbacks(meshApi) {
    override fun onMeshMessageReceived(src: Int, message: MeshMessage) {
        super.onMeshMessageReceived(src, message)
        Timber.d("onMeshMessageReceived - src: $src, dst: ${message.dst}, meshMessage: ${message.sequenceNumber()}")
        runBlocking {
            processor.messageReceived(message)
        }
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
        Timber.d("onMeshMessageProcessed - src: $dst, dst: ${meshMessage.dst},  sequenceNumber: ${meshMessage.sequenceNumber()}")
        runBlocking {
            processor.messageProcessed(meshMessage)
        }
    }

    override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
        Timber.d("onBlockAcknowledgementProcessed - dst: $dst, src: ${message.src}, sequenceNumber: ${message.sequenceNumber.toInt()}")
    }

    override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
        Timber.d("onBlockAcknowledgementReceived - src: $src, dst: ${message.dst}, sequenceNumber: ${message.sequenceNumber.toInt()}")
    }

    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray?) {
        Timber.d("onUnknownPduReceived - src: $src, accessPayload $accessPayload")
    }

    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        Timber.d("onUnknownPduReceived - meshLayer: $meshLayer, errorMessage $errorMessage")
    }
}