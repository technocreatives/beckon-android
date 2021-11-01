package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.right
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.data.AccessPayload
import com.technocreatives.beckon.mesh.data.ProxyFilterMessage
import com.technocreatives.beckon.mesh.extensions.info
import com.technocreatives.beckon.mesh.extensions.onDisconnect
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.message.*
import com.technocreatives.beckon.mesh.processor.MessageProcessor
import com.technocreatives.beckon.mesh.processor.Pdu
import com.technocreatives.beckon.mesh.processor.PduSender
import com.technocreatives.beckon.mesh.scenario.RepeatRetry
import com.technocreatives.beckon.mesh.scenario.Retry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber

class Connected(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    filteredSubject: MutableSharedFlow<ProxyFilterMessage>,
    private val beckonDevice: BeckonDevice,
) : MeshState(beckonMesh, meshApi) {

    private val processor =
        MessageProcessor(object : PduSender {
            override fun createPdu(
                dst: Int,
                meshMessage: MeshMessage
            ) = meshApi.createPdu(dst, meshMessage)

            override suspend fun sendPdu(pdu: Pdu) =
                with(meshApi) {
                    beckonDevice.sendPdu(pdu.data, MeshConstants.proxyDataInCharacteristic)
                }
        }, beckonMesh.config.ackMessageTimeout)

    internal val bearer = MessageBearer(processor)

    private var disconnectJob: Job? = null

    init {
        with(processor) {
            beckonMesh.execute()
        }

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

        disconnectJob = beckonMesh.execute {
            beckonDevice.onDisconnect {
                beckonMesh.updateState(Loaded(beckonMesh, meshApi))
            }
        }
    }

    override suspend fun isValid(): Boolean = beckonMesh.isCurrentState<Connected>()

    suspend fun disconnect(): Either<BleDisconnectError, Loaded> = either {
        disconnectJob?.cancel()
//        processor.close()
        beckonDevice.disconnect().mapLeft { BleDisconnectError(it) }.bind()
        val loaded = Loaded(beckonMesh, meshApi)
        beckonMesh.updateState(loaded)
        loaded
    }

    suspend fun <T : ConfigStatusMessage> sendConfigMessage(message: ConfigMessage<T>): Either<SendAckMessageError, T> =
        bearer.sendConfigMessage(message)

    suspend fun <T : ConfigStatusMessage> sendConfigMessage(
        message: ConfigMessage<T>,
        retry: Retry
    ): Either<SendAckMessageError, T> =
        retry {
            bearer.sendConfigMessage(message)
        }

}

suspend fun Connected.setUpAppKey(
    nodeAddress: UnicastAddress,
    netKey: NetKey,
    appKey: AppKey,
): Either<SendAckMessageError, Unit> = either {

    val getCompositionData = GetCompositionData(nodeAddress.value)
    val compositionStatus = bearer.sendConfigMessage(getCompositionData).bind()
    Timber.d("getConfigCompositionData Status $compositionStatus")

    val getDefaultTtl = GetDefaultTtl(nodeAddress.value)
    val defaultTtlStatus = bearer.sendConfigMessage(getDefaultTtl).bind()
    Timber.d("getConfigDefaultTtl Status $defaultTtlStatus")

    val setDefaultTtl = SetDefaultTtl(nodeAddress.value, 10)
    val ttlStatus = bearer.sendConfigMessage(setDefaultTtl).bind()
    Timber.d("setConfigDefaultTtl Status $ttlStatus")

    val networkTransmitSet = SetConfigNetworkTransmit(nodeAddress.value, 2, 1)
    val networkTransmit = bearer.sendConfigMessage(networkTransmitSet).bind()
    Timber.d("setConfigNetworkTransmit Status $networkTransmit")

    val relayConfigSet = SetRelayConfig(nodeAddress.value, retransmit = RelayRetransmit(1, 5))
    val relayConfig = bearer.sendConfigMessage(relayConfigSet).bind()
    Timber.d("setRelayConfig Status $relayConfig")

    val configAppKeyAdd = AddConfigAppKey(nodeAddress.value, netKey, appKey)

    val appKeyAddStatus = bearer.sendConfigMessage(configAppKeyAdd).bind()
    Timber.d("addConfigAppKey Status $appKeyAddStatus")
}

fun <T> Pair<Int, SendAckMessageError>.convert(): T = TODO()

suspend fun <T : ConfigStatusMessage> Connected.execute(messages: List<ConfigMessage<T>>): Either<Pair<Int, SendAckMessageError>, Unit> {
    return messages.traverseStep(RepeatRetry(3)) { sendConfigMessage(it) }.map {}
}

private suspend inline fun <E, A, B> List<A>.traverseStep(
    retry: Retry,
    crossinline f: suspend (A) -> Either<E, B>,
): Either<Pair<Int, E>, List<B>> {
    val destination = ArrayList<B>(size)
    forEachIndexed { index, item ->
        Timber.d("Execute Step ${index + 1} - $item")
        when (val res = retry { f(item) }) {
            is Either.Right -> destination.add(res.value)
            is Either.Left -> return res.mapLeft { index to it }
        }
    }
    return destination.right()
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
    private val filteredSubject: MutableSharedFlow<ProxyFilterMessage>,
) : AbstractMessageStatusCallbacks(meshApi) {

    private val provisionerAddress = meshApi.meshNetwork().provisionerAddress!!
    private val sequenceNumberMap = mutableMapOf<Int, Int>()

    private fun verifySequenceNumber(src: Int, sequenceNumber: Int): Boolean =
        if (sequenceNumber <= sequenceNumberMap[src] ?: -1) false
        else {
            sequenceNumberMap[src] = sequenceNumber
            true
        }

    override fun onMeshMessageReceived(src: Int, message: MeshMessage) {
        super.onMeshMessageReceived(src, message)
        Timber.d("onMeshMessageReceived ${message.info()}")
        if (verifySequenceNumber(src, message.sequenceNumber() ?: 0)) {
            runBlocking {
                sendToProcessor(message)
            }
        } else {
            Timber.w("onMeshMessageReceived Duplicated sequence number ${message.info()}")
        }

    }

    private suspend fun sendFilteredMessage(src: Int, message: MeshMessage) {

    }

    private suspend fun sendToProcessor(message: MeshMessage) {
        val dst = message.dst
        if (dst == provisionerAddress || dst == UnassignedAddress.value) {
            processor.messageReceived(message)
        } else {
            Timber.w("onMeshMessageReceived, filtered message from processor with dst: $dst")
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
        Timber.d("onUnknownPduReceived - src: $src, accessPayload ${accessPayload?.toHex()}")
        accessPayload?.let {
            val filteredMessage = ProxyFilterMessage(UnicastAddress(src), AccessPayload.parse(it))
            GlobalScope.launch {
                filteredSubject.emit(filteredMessage)
            }
        }

    }

    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        Timber.d("onUnknownPduReceived - meshLayer: $meshLayer, errorMessage $errorMessage")
    }
}
