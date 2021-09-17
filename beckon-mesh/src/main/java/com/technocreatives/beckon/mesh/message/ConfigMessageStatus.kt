package com.technocreatives.beckon.mesh.message

import no.nordicsemi.android.mesh.transport.ConfigStatusMessage

data class ConfigMessageStatus(val isSuccess: Boolean, val statusCode: Int, val statusMessage: String)

internal fun ConfigStatusMessage.transform() =
    ConfigMessageStatus(statusCode == 0x00, statusCode, statusCodeName)