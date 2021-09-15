package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.data.AppKey
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.data.NetKey
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.extensions.info
import com.technocreatives.beckon.mesh.extensions.onDisconnect
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.message.MessageBearer
import com.technocreatives.beckon.mesh.processor.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.descriptors.PrimitiveKind
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.ConfigAppKeyAdd
import no.nordicsemi.android.mesh.transport.ConfigNetworkTransmitSet
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber

class Connected(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val filteredSubject: MutableSharedFlow<BeckonMesh.ProxyFilterMessage>,
    private val beckonDevice: BeckonDevice,
) : MeshState(beckonMesh, meshApi) {

    private val processor by lazy {
        MessageProcessor(object : PduSender {
            override fun createPdu(
                dst: Int,
                meshMessage: MeshMessage
            ) = meshApi.createPdu(dst, meshMessage)

            override suspend fun sendPdu(pdu: Pdu) =
                with(meshApi) {
                    beckonDevice.sendPdu(pdu.data, MeshConstants.proxyDataInCharacteristic)
                }
        }, 30000) // todo timeout
    }

    internal val bearer by lazy { MessageBearer(processor) }

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
                processor,
                filteredSubject
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
    nodeAddress: UnicastAddress,
    netKey: NetKey,
    appKey: AppKey,
): Either<Any, Unit> = either {

    val compositionStatus = bearer.getConfigCompositionData(nodeAddress).bind()
    Timber.d("getConfigCompositionData Status $compositionStatus")

    val defaultTtlStatus = bearer.getConfigDefaultTtl(nodeAddress).bind()
    Timber.d("getConfigDefaultTtl Status $defaultTtlStatus")

    val networkTransmitSet = ConfigNetworkTransmitSet(2, 1)

    val networkTransmit = bearer.setConfigNetworkTransmit(nodeAddress, networkTransmitSet).bind()
    Timber.d("setConfigNetworkTransmit Status $networkTransmit")

    val networkKey = beckonMesh.netKey(netKey.index)!!
    val applicationKey = beckonMesh.appKey(appKey.index)!!
    val configAppKeyAdd = ConfigAppKeyAdd(networkKey, applicationKey)

    val appKeyAddStatus = bearer.addConfigAppKey(nodeAddress, configAppKeyAdd).bind()
    Timber.d("addConfigAppKey Status $appKeyAddStatus")
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
    private val processor: MessageProcessor,
    private val filteredSubject: MutableSharedFlow<BeckonMesh.ProxyFilterMessage>,
) : AbstractMessageStatusCallbacks(meshApi) {

    private val sequenceNumberMap = mutableMapOf<Int, Int>()

    private fun verifySequenceNumber(src: Int, sequenceNumber: Int): Boolean =
        if (sequenceNumber <= sequenceNumberMap[src] ?: 0) false
        else {
            sequenceNumberMap[src] = sequenceNumber
            true
        }

    override fun onMeshMessageReceived(src: Int, message: MeshMessage) {
        super.onMeshMessageReceived(src, message)
        Timber.d("onMeshMessageReceived ${message.info()}")
        if (verifySequenceNumber(src, message.sequenceNumber() ?: 0)) {
            val filteredMessage = BeckonMesh.ProxyFilterMessage(GroupAddress(message.dst), message)
            runBlocking {
                filteredSubject.emit(filteredMessage)
                processor.messageReceived(message)
            }
        } else {
            Timber.w("Duplicated sequence number ${message.info()}")
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