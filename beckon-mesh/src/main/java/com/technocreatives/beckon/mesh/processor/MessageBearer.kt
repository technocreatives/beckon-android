package com.technocreatives.beckon.mesh.processor

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.withTimeout
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.opcodes.ProxyConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.*

class MessageBearer(val processor: MessageProcessor) {

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

    suspend fun sendVendorModelMessage(
        unicastAddress: Int,
        message: VendorModelMessageUnacked
    ): Either<SendAckMessageError, Unit> =
        processor.sendMessage(
            unicastAddress,
            message
        )

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

    private suspend fun sendAckMessage(
        address: Int,
        message: MeshMessage,
        opCode: Int
    ): Either<SendAckMessageError, MeshMessage> =
        withTimeout(30000) {
            processor.sendAckMessage(
                address,
                message,
                opCode
            )
        }
}