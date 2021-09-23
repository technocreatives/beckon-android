package com.technocreatives.beckon.mesh.message

import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes

enum class StatusOpCode(val value: Int) {
    ConfigComposition(2), // ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS.toInt()
    ConfigDefaultTtl(ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS),
    ConfigNetworkSet(ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS),
    ConfigAppKey(ConfigMessageOpCodes.CONFIG_APPKEY_STATUS),
    ;

    companion object {
        fun from(value: Int): StatusOpCode = values().first { it.value == value }
    }
}