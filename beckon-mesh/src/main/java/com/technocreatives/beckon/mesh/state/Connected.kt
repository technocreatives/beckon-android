package com.technocreatives.beckon.mesh.state

import arrow.core.*
import arrow.core.computations.either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.extensions.getMaximumPacketSize
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import com.technocreatives.beckon.mesh.callbacks.AbstractMessageStatusCallbacks
import com.technocreatives.beckon.mesh.extensions.info
import com.technocreatives.beckon.mesh.extensions.onDisconnect
import com.technocreatives.beckon.mesh.extensions.sequenceNumber
import com.technocreatives.beckon.mesh.extensions.toHex
import com.technocreatives.beckon.mesh.model.AppKey
import com.technocreatives.beckon.mesh.model.NetworkKey
import com.technocreatives.beckon.mesh.model.Node
import com.technocreatives.beckon.mesh.processor.MessageQueue
import com.technocreatives.beckon.mesh.processor.Pdu
import com.technocreatives.beckon.mesh.processor.PduSender
import com.technocreatives.beckon.mesh.processor.PduSenderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.NetworkKey as NrfNetworkKey
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.opcodes.ProxyConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.*
import no.nordicsemi.android.mesh.utils.MeshParserUtils
import no.nordicsemi.android.mesh.utils.SecureUtils
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

    private var disconnectJob: Job? = null

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

    suspend fun unbindConfigModelApp(
        unicastAddress: Int,
        message: ConfigModelAppUnbind
    ): Either<SendAckMessageError, ConfigModelAppStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_APP_STATUS
        ).map { it as ConfigModelAppStatus }

    suspend fun bindConfigModelApp(
        unicastAddress: Int,
        message: ConfigModelAppBind
    ): Either<SendAckMessageError, ConfigModelAppStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_APP_STATUS
        ).map { it as ConfigModelAppStatus }

    suspend fun sendVendorModelMessageAck(
        unicastAddress: Int,
        message: VendorModelMessageAcked
    ): Either<SendAckMessageError, VendorModelMessageStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            message.opCode
        ).map { it as VendorModelMessageStatus }

    suspend fun addConfigModelSubscriptionVirtualAddress(
        unicastAddress: Int,
        message: ConfigModelSubscriptionVirtualAddressAdd
    ): Either<SendAckMessageError, ConfigModelSubscriptionStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS
        ).map { it as ConfigModelSubscriptionStatus }

    suspend fun addConfigModelSubscription(
        unicastAddress: Int,
        message: ConfigModelSubscriptionAdd
    ): Either<SendAckMessageError, ConfigModelSubscriptionStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS
        ).map { it as ConfigModelSubscriptionStatus }

    suspend fun getConfigCompositionData(address: Int): Either<SendAckMessageError, ConfigCompositionDataStatus> =
        sendAckMessage(
            address,
            ConfigCompositionDataGet(),
            ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt()
        ).map { it as ConfigCompositionDataStatus }

    suspend fun getConfigDefaultTtl(address: Int): Either<SendAckMessageError, ConfigDefaultTtlStatus> =
        sendAckMessage(
            address,
            ConfigDefaultTtlGet(),
            ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS
        ).map { it as ConfigDefaultTtlStatus }

    suspend fun setConfigNetworkTransmit(
        address: Int,
        message: ConfigNetworkTransmitSet
    ): Either<SendAckMessageError, ConfigNetworkTransmitStatus> =
        sendAckMessage(
            address,
            message,
            ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS
        ).map { it as ConfigNetworkTransmitStatus }

    suspend fun addConfigAppKey(
        address: Int,
        message: ConfigAppKeyAdd
    ): Either<SendAckMessageError, ConfigAppKeyStatus> =
        sendAckMessage(
            address,
            message,
            ConfigMessageOpCodes.CONFIG_APPKEY_STATUS
        ).map { it as ConfigAppKeyStatus }

    suspend fun addProxyConfigAddressToFilter(
        address: Int,
        message: ProxyConfigAddAddressToFilter
    ) =
        sendAckMessage(
            address,
            message,
            ProxyConfigMessageOpCodes.FILTER_STATUS
        ).map { it as ProxyConfigFilterStatus }

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

    suspend fun distributeNetKey(
        key: NetworkKey,
        newKey: ByteArray
    ): Either<InvalidNetKey, NetworkKey> =
        withContext(Dispatchers.IO) {
            Either.catch {
                meshApi.meshNetwork().distributeNetKey(key.actualKey, newKey)
                    .let { NetworkKey(it) } // todo DistributedFailed
            }.mapLeft {
                Timber.w("========== Invalid Net Key $it")
                InvalidNetKey(newKey)
            }
        }

    suspend fun updateConfigNetKey(
        address: Int,
        message: ConfigNetKeyUpdate
    ): Either<SendAckMessageError, ConfigNetKeyStatus> =
        sendAckMessage(
            address, message, ConfigMessageOpCodes.CONFIG_NETKEY_STATUS
        ).map { it as ConfigNetKeyStatus }

    suspend fun switchToNewKey(key: NetworkKey): Either<SwitchKeysFailed, NrfNetworkKey> {
        val actualKey = key.actualKey
        return withContext(Dispatchers.IO) {
            Either.catch {
                meshApi.meshNetwork().switchToNewKey(actualKey)
            }.mapLeft { SwitchKeysFailed(key.actualKey) }
                .flatMap {
                    if (it) actualKey.right()
                    else SwitchKeysFailed(actualKey).left()
                }
        }
    }

    suspend fun revokeOldKey(key: NrfNetworkKey): Either<RevokeOldKeyFailed, NrfNetworkKey> =
        withContext(Dispatchers.IO) {
            if (meshApi.meshNetwork().revokeOldKey(key)) {
                key.right()
            } else {
                RevokeOldKeyFailed(key).left()
            }
        }

    suspend fun setConfigKeyRefreshPhase(
        address: Int,
        message: ConfigKeyRefreshPhaseSet
    ): Either<SendAckMessageError, ConfigKeyRefreshPhaseStatus> =
        sendAckMessage(
            address, message, ConfigMessageOpCodes.CONFIG_KEY_REFRESH_PHASE_STATUS
        ).map { it as ConfigKeyRefreshPhaseStatus }

    suspend fun distributeAppKey(
        key: AppKey,
        newKey: ByteArray
    ): Either<InvalidAppKey, AppKey> =
        withContext(Dispatchers.IO) {
            Either.catch {
                meshApi.meshNetwork().distributeAppKey(key.applicationKey, newKey)
                    .let { AppKey(it) }
            }.mapLeft { InvalidAppKey(newKey) }
        }

    suspend fun updateConfigAppKey(
        address: Int,
        message: ConfigAppKeyUpdate
    ): Either<SendAckMessageError, ConfigAppKeyStatus> =
        sendAckMessage(
            address, message, ConfigMessageOpCodes.CONFIG_APPKEY_STATUS
        ).map { it as ConfigAppKeyStatus }

    suspend fun resetConfigNode(
        address: Int,
    ): Either<SendAckMessageError, ConfigNodeResetStatus> =
        sendAckMessage(
            address, ConfigNodeReset(), ConfigMessageOpCodes.CONFIG_NODE_RESET_STATUS
        ).map { it as ConfigNodeResetStatus }

}


suspend fun Connected.nodeRemoval(node: Node): Either<Any, Any> = either {
    val status = resetConfigNode(node.unicastAddress).bind()
    node.netKeys.traverseEither { netKeyRefresh(it) }.bind()
}

suspend fun Connected.netKeyRefresh(netKey: NetworkKey): Either<Any, Any> = either {
    val newKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomNetworkKey())
    Timber.d("======== NewKey: ${newKey.toHex()}")
    Timber.d("======== NetKey: ${netKey.actualKey.info()}")

    val nodes = meshApi.nodes(netKey)
    Timber.d("======== nodes: ${nodes.size}")

    val updatedKey = distributeNetKey(netKey, newKey).bind()
    Timber.d("======== Updated Key: ${updatedKey.actualKey.info()}")

    val updateMessage = ConfigNetKeyUpdate(updatedKey.actualKey)
    val e = nodes.traverseEither { updateConfigNetKey(it.unicastAddress, updateMessage) }.bind()
    Timber.d("======== updateConfigNetKey: ${e.map { it.isSuccessful }}")

    val k1 = switchToNewKey(updatedKey).bind()
    Timber.d("======== switchToNewKey: ${k1.info()}")

    val useNewKeyMessage = ConfigKeyRefreshPhaseSet(k1, NrfNetworkKey.USE_NEW_KEYS)
    val e2 =
        nodes.traverseEither { setConfigKeyRefreshPhase(it.unicastAddress, useNewKeyMessage) }.bind()
    Timber.d("======== useNewKeyMessage: ${e2.map { it.isSuccessful }}")

    val k2 = revokeOldKey(k1).bind()
    Timber.d("======== revokeOldKey: ${k2.info()}")

    val revokeOldKeysMessage = ConfigKeyRefreshPhaseSet(k2, NrfNetworkKey.REVOKE_OLD_KEYS)
    val e3 =
        nodes.traverseEither { setConfigKeyRefreshPhase(it.unicastAddress, revokeOldKeysMessage) }.bind()
    Timber.d("======== revokeOldKeysMessage: ${e3.map { it.isSuccessful }}")

    meshApi.networkKeys().map { Timber.d("New Net Key ${it.key.info()}") }
    e2
}

suspend fun Connected.netKeyRefresh(netKey: NetworkKey, appKey: AppKey): Either<Any, Any> = either {
    val newKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomNetworkKey())
    Timber.d("======== NewKey: ${newKey.toHex()}")
    Timber.d("======== NetKey: ${netKey.actualKey.info()}")

    val nodes = meshApi.nodes(netKey)
    Timber.d("======== nodes: ${nodes.size}")

    val updatedKey = distributeNetKey(netKey, newKey).bind()
    Timber.d("======== Updated Key: ${updatedKey.actualKey.info()}")

    val updateMessage = ConfigNetKeyUpdate(updatedKey.actualKey)
    val e = nodes.traverseEither { updateConfigNetKey(it.unicastAddress, updateMessage) }.bind()
    Timber.d("======== updateConfigNetKey: ${e.map { it.isSuccessful }}")


    val appKeyRefreshResult = appKeyRefresh(appKey, nodes).bind()
    Timber.d("===== appKeyRefreshResult $appKeyRefreshResult")
    // revert

    val k1 = switchToNewKey(updatedKey).bind()
    Timber.d("======== switchToNewKey: ${k1.info()}")

    val useNewKeyMessage = ConfigKeyRefreshPhaseSet(k1, NrfNetworkKey.USE_NEW_KEYS)
    val e2 =
        nodes.traverseEither { setConfigKeyRefreshPhase(it.unicastAddress, useNewKeyMessage) }.bind()
    Timber.d("======== useNewKeyMessage: ${e2.map { it.isSuccessful }}")

    val k2 = revokeOldKey(k1).bind()
    Timber.d("======== revokeOldKey: ${k2.info()}")

    val revokeOldKeysMessage = ConfigKeyRefreshPhaseSet(k2, NrfNetworkKey.REVOKE_OLD_KEYS)
    val e3 =
        nodes.traverseEither { setConfigKeyRefreshPhase(it.unicastAddress, revokeOldKeysMessage) }.bind()
    Timber.d("======== revokeOldKeysMessage: ${e3.map { it.isSuccessful }}")

    meshApi.networkKeys().map { Timber.d("New Net Key ${it.key.info()}") }
    e2
}

suspend fun Connected.appKeyRefresh(appKey: AppKey, nodes: List<Node>): Either<Any, Any> = either {

    val newAppKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomApplicationKey())
    Timber.d("======== Current App Key: ${appKey.applicationKey.info()}")
    Timber.d("======== New App Key: ${newAppKey.toHex()}")

    val updatedAppKey = distributeAppKey(appKey, newAppKey).bind()
    Timber.d("======== updatedAppKey: ${updatedAppKey.applicationKey.info()}")

    val updateAppKeyMessage = ConfigAppKeyUpdate(updatedAppKey.applicationKey)
    val e1 =
        nodes.traverseEither { updateConfigAppKey(it.unicastAddress, updateAppKeyMessage) }.bind()
    meshApi.appKeys().map { Timber.d("New App Key ${it.key.info()}") }
    e1
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
    private val queue: MessageQueue
) : AbstractMessageStatusCallbacks(meshApi) {
    override fun onMeshMessageReceived(src: Int, message: MeshMessage) {
        super.onMeshMessageReceived(src, message)
        Timber.d("onMeshMessageReceived - src: $src, dst: ${message.dst}, meshMessage: ${message.sequenceNumber()}")
        runBlocking {
            queue.messageReceived(message)
        }
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
        Timber.d("onMeshMessageProcessed - src: $dst, dst: ${meshMessage.dst},  sequenceNumber: ${meshMessage.sequenceNumber()}")
        runBlocking {
            queue.messageProcessed(meshMessage)
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