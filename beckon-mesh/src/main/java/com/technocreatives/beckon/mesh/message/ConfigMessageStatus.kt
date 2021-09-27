package com.technocreatives.beckon.mesh.message

import no.nordicsemi.android.mesh.transport.ConfigStatusMessage

data class ConfigMessageStatus(
    override val dst: Int,
    override val src: Int,
    val isSuccess: Boolean,
    val statusCode: Int,
) : BeckonStatusMessage

internal fun ConfigStatusMessage.transform() =
    ConfigMessageStatus(dst, src, statusCode == 0x00, statusCode)