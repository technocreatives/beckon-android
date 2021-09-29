package com.technocreatives.beckon.mesh.message

import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes
import no.nordicsemi.android.mesh.opcodes.ProxyConfigMessageOpCodes
import no.nordicsemi.android.mesh.transport.*

enum class StatusOpCode(val value: Int) {
    ConfigComposition(2), // ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt()
    ConfigDefaultTtl(ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS),
    ConfigNetworkSet(ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS),
    ConfigAppKey(ConfigMessageOpCodes.CONFIG_APPKEY_STATUS),
    ConfigModelApp(ConfigMessageOpCodes.CONFIG_MODEL_APP_STATUS),
    ConfigModelSubscription(ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS),
    ConfigModelPublication(ConfigMessageOpCodes.CONFIG_MODEL_PUBLICATION_STATUS),
    ConfigNodeReset(ConfigMessageOpCodes.CONFIG_NODE_RESET_STATUS),
    ProxyFilterType(ProxyConfigMessageOpCodes.FILTER_STATUS)
    ;

    companion object {
        fun from(value: Int): StatusOpCode = values().first { it.value == value }
    }

    fun convert(message: MeshMessage): ConfigStatusMessage =
        when (from(message.opCode)) {
            ConfigComposition -> (message as ConfigCompositionDataStatus).transform()
            ConfigDefaultTtl -> (message as ConfigDefaultTtlStatus).transform()
            ConfigNetworkSet -> (message as ConfigNetworkTransmitStatus).transform()
            ConfigAppKey -> (message as ConfigAppKeyStatus).transform()
            ConfigModelApp -> (message as ConfigModelAppStatus).transform()
            ConfigModelSubscription -> (message as ConfigModelSubscriptionStatus).transform()
            ConfigModelPublication -> (message as ConfigModelPublicationStatus).transform()
            ConfigNodeReset -> (message as ConfigNodeResetStatus).transform()
            ProxyFilterType ->  (message as ProxyConfigFilterStatus).transform()
        }
}