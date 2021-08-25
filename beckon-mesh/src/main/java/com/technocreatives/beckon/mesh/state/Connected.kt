package com.technocreatives.beckon.mesh.state

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.model.AppKey
import com.technocreatives.beckon.mesh.model.NetworkKey
import com.technocreatives.beckon.mesh.model.Node
import com.technocreatives.beckon.mesh.processor.MessageQueue
import com.technocreatives.beckon.mesh.processor.Pdu
import com.technocreatives.beckon.mesh.processor.PduSender
import com.technocreatives.beckon.mesh.processor.PduSenderResult
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.*
import timber.log.Timber

class Connected(
    beckonMesh: BeckonMesh,
    meshApi: BeckonMeshManagerApi,
    private val beckonDevice: BeckonDevice
) : MeshState(beckonMesh, meshApi) {

    private var queue: MessageQueue = MessageQueue(object : PduSender {
        override fun createPdu(
            dst: Int,
            meshMessage: MeshMessage
        ) = meshApi.createPdu(dst, meshMessage)

        override suspend fun sendPdu(pdu: Pdu): PduSenderResult =
            with(meshApi) {
                beckonDevice.sendPdu(pdu.data, MeshConstants.proxyDataInCharacteristic)
            }
    })

    init {
        meshApi.setMeshManagerCallbacks(
            ConnectedMeshManagerCallbacks(
                beckonDevice,
                meshApi,
                queue
            )
        )
        meshApi.setMeshStatusCallbacks(
            ConnectedMessageStatusCallbacks(
                meshApi,
                queue
            )
        )
        with(queue) {
            beckonMesh.execute()
        }
    }

    suspend fun disconnect(): Either<Any, Loaded> = either {
        beckonDevice.disconnect().bind()
        val loaded = Loaded(beckonMesh, meshApi)
        beckonMesh.updateState(loaded)
        loaded
    }

    suspend fun bindConfigModelApp(
        unicastAddress: Int,
        message: ConfigModelAppBind
    ): Either<SendAckMessageError, ConfigModelAppStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_APP_STATUS
        )
            .map { it as ConfigModelAppStatus }

    suspend fun sendVendorModelMessageAck(
        unicastAddress: Int,
        message: VendorModelMessageAcked
    ): Either<SendAckMessageError, VendorModelMessageStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            message.opCode
        )
            .map { it as VendorModelMessageStatus }

    suspend fun addConfigModelSubscriptionVirtualAddress(
        unicastAddress: Int,
        message: ConfigModelSubscriptionVirtualAddressAdd
    ): Either<SendAckMessageError, ConfigModelSubscriptionStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS
        )
            .map { it as ConfigModelSubscriptionStatus }

    suspend fun addConfigModelSubscription(
        unicastAddress: Int,
        message: ConfigModelSubscriptionAdd
    ): Either<SendAckMessageError, ConfigModelSubscriptionStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS
        )
            .map { it as ConfigModelSubscriptionStatus }

    suspend fun getConfigCompositionData(address: Int): Either<SendAckMessageError, ConfigCompositionDataStatus> =
        sendAckMessage(
            address,
            ConfigCompositionDataGet(),
            ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt()
        )
            .map { it as ConfigCompositionDataStatus }

    suspend fun getConfigDefaultTtl(address: Int): Either<SendAckMessageError, ConfigDefaultTtlStatus> =
        sendAckMessage(
            address,
            ConfigDefaultTtlGet(),
            ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS
        )
            .map { it as ConfigDefaultTtlStatus }

    suspend fun setConfigNetworkTransmit(
        address: Int,
        message: ConfigNetworkTransmitSet
    ): Either<SendAckMessageError, ConfigNetworkTransmitStatus> =
        sendAckMessage(
            address,
            message,
            ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS
        )
            .map { it as ConfigNetworkTransmitStatus }

    suspend fun addConfigAppKey(
        address: Int,
        message: ConfigAppKeyAdd
    ): Either<SendAckMessageError, ConfigAppKeyStatus> =
        sendAckMessage(
            address,
            message,
            ConfigMessageOpCodes.CONFIG_APPKEY_STATUS
        ).map { it as ConfigAppKeyStatus }

    private suspend fun sendAckMessage(
        address: Int,
        message: MeshMessage,
        opCode: Int
    ): Either<SendAckMessageError, MeshMessage> =
        withTimeout(30000) {
            queue.sendAckMessage(
                address,
                message,
                opCode
            )
        }

}

class ConnectedMeshManagerCallbacks(
    private val beckonDevice: BeckonDevice,
    private val meshApi: BeckonMeshManagerApi,
    private val queue: MessageQueue,
) : AbstractMeshManagerCallbacks() {

    override fun onMeshPduCreated(pdu: ByteArray) {
        Timber.d("sendPdu - onMeshPduCreated - ${pdu.size}")
        runBlocking {
            queue.sendPdu(Pdu(pdu))
        }
    }

    override fun onNetworkUpdated(meshNetwork: MeshNetwork) {
        Timber.d("onNetworkUpdated")
        meshApi.loadNodes()
    }

    override fun getMtu(): Int {
        return beckonDevice.getMaximumPacketSize()
    }
}

suspend fun Connected.setUpAppKey(
    node: Node,
    netKey: NetworkKey,
    appKey: AppKey,
): Either<Any, Unit> = either {

    getConfigCompositionData(node.unicastAddress).bind()
    getConfigDefaultTtl(node.unicastAddress).bind()
    val networkTransmitSet = ConfigNetworkTransmitSet(2, 1)
    setConfigNetworkTransmit(node.unicastAddress, networkTransmitSet).bind()

    val configAppKeyAdd = ConfigAppKeyAdd(netKey.actualKey, appKey.applicationKey)
    addConfigAppKey(node.unicastAddress, configAppKeyAdd).bind()
}

class ConnectedMessageStatusCallbacks(
    meshApi: BeckonMeshManagerApi,
    private val queue: MessageQueue
) : AbstractMessageStatusCallbacks(meshApi) {
    // verify message onMeshMessageReceived
    override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
        super.onMeshMessageReceived(src, meshMessage)
        Timber.w("onMeshMessageReceived - src: $src, dst: ${meshMessage.dst}, meshMessage: ${meshMessage.sequenceNumber()}")
        runBlocking {
            queue.messageReceived(meshMessage)
        }
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
        Timber.w("onMeshMessageProcessed - src: $dst, dst: ${meshMessage.dst},  sequenceNumber: ${meshMessage.sequenceNumber()}")
        runBlocking {
            queue.messageProcessed(meshMessage)
        }
    }

    override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
        Timber.w("onBlockAcknowledgementProcessed - dst: $dst, src: ${message.src}, sequenceNumber: ${message.sequenceNumber.toInt()}")
    }

    override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
        Timber.w("onBlockAcknowledgementReceived - src: $src, dst: ${message.dst}, sequenceNumber: ${message.sequenceNumber.toInt()}")
    }

    override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) {
        super.onTransactionFailed(dst, hasIncompleteTimerExpired)
    }

    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray?) {
        super.onUnknownPduReceived(src, accessPayload)
    }

    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        super.onMessageDecryptionFailed(meshLayer, errorMessage)
    }
}