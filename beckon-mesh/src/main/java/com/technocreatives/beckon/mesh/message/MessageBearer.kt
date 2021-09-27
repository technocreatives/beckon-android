package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.processor.MessageProcessor
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.opcodes.ProxyConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.*
import no.nordicsemi.android.mesh.utils.MeshAddress
import timber.log.Timber

class MessageBearer(private val processor: MessageProcessor) {

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
        message: VendorModelMessageAcked,
        responseOpCode: Int,
    ): Either<SendAckMessageError, VendorModelMessageStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            responseOpCode,
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

    suspend fun deleteConfigModelSubscription(
        unicastAddress: Int,
        message: ConfigModelSubscriptionDelete
    ): Either<SendAckMessageError, ConfigModelSubscriptionStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS
        ).map { it as ConfigModelSubscriptionStatus }

    suspend fun getConfigModelPublication(
        unicastAddress: Int,
        message: ConfigModelPublicationGet
    ): Either<SendAckMessageError, ConfigModelPublicationStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_PUBLICATION_STATUS
        ).map { it as ConfigModelPublicationStatus }

    suspend fun setConfigModelPublication(
        unicastAddress: Int,
        message: ConfigModelPublicationSet
    ): Either<SendAckMessageError, ConfigModelPublicationStatus> =
        sendAckMessage(
            unicastAddress,
            message,
            ConfigMessageOpCodes.CONFIG_MODEL_PUBLICATION_STATUS
        ).map { it as ConfigModelPublicationStatus }

    suspend fun addProxyConfigAddressToFilter(message: ProxyConfigAddAddressToFilter) =
        sendAckMessage(
            MeshAddress.UNASSIGNED_ADDRESS,
            message,
            ProxyConfigMessageOpCodes.FILTER_STATUS
        ).map { it as ProxyConfigFilterStatus }

    suspend fun removeProxyConfigAddressFromFilter(
        message: ProxyConfigRemoveAddressFromFilter
    ) =
        sendAckMessage(
            MeshAddress.UNASSIGNED_ADDRESS,
            message,
            ProxyConfigMessageOpCodes.FILTER_STATUS
        ).map { it as ProxyConfigFilterStatus }

    suspend fun setProxyConfigFilterType(
        message: ProxyConfigSetFilterType
    ) =
        sendAckMessage(
            MeshAddress.UNASSIGNED_ADDRESS,
            message,
            ProxyConfigMessageOpCodes.FILTER_STATUS
        ).map { it as ProxyConfigFilterStatus }


    suspend fun updateConfigNetKey(
        address: Int,
        message: ConfigNetKeyUpdate
    ): Either<SendAckMessageError, ConfigNetKeyStatus> =
        sendAckMessage(
            address, message, ConfigMessageOpCodes.CONFIG_NETKEY_STATUS
        ).map { it as ConfigNetKeyStatus }


    suspend fun setConfigKeyRefreshPhase(
        address: Int,
        message: ConfigKeyRefreshPhaseSet
    ): Either<SendAckMessageError, ConfigKeyRefreshPhaseStatus> =
        sendAckMessage(
            address, message, ConfigMessageOpCodes.CONFIG_KEY_REFRESH_PHASE_STATUS
        ).map { it as ConfigKeyRefreshPhaseStatus }

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

    internal suspend fun sendAckMessage(
        address: Int,
        message: MeshMessage,
        responseOpCode: Int
    ): Either<SendAckMessageError, MeshMessage> =
        processor.sendAckMessage(
            address,
            message,
            responseOpCode
        )

    internal suspend fun sendMessage(
        address: Int,
        message: MeshMessage
    ): Either<SendAckMessageError, Unit> =
        processor.sendMessage(
            address,
            message
        )

    suspend fun sendConfigMessage(message: ConfigMessage): Either<SendAckMessageError, BeckonStatusMessage> {
        Timber.d("Send $message")
        val response =
            sendAckMessage(message.dst, message.toMeshMessage(), message.responseOpCode.value)
                .map { message.responseOpCode.convert(it) }
        Timber.d("Received $response")
        return response
    }

}