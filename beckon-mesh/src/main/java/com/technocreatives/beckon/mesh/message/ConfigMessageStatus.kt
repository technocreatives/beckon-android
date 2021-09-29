package com.technocreatives.beckon.mesh.message

import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.transport.ConfigStatusMessage as NrfConfigStatusMessage

@Serializable
data class ConfigMessageStatus(
    override val dst: Int,
    override val src: Int,
    val isSuccess: Boolean,
    val statusCode: Int,
) : ConfigStatusMessage()

internal fun NrfConfigStatusMessage.transform() =
    ConfigMessageStatus(dst, src, statusCode == 0x00, statusCode)