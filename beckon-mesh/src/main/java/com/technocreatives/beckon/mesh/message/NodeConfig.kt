package com.technocreatives.beckon.mesh.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nordicsemi.android.mesh.transport.ConfigModelAppStatus
import no.nordicsemi.android.mesh.transport.ConfigNodeReset
import no.nordicsemi.android.mesh.transport.ConfigNodeResetStatus
import no.nordicsemi.android.mesh.transport.MeshMessage

@Serializable
@SerialName("ResetNode")
data class ResetNode(override val dst: Int) : ConfigMessage<ConfigMessageStatus>() {
    override val responseOpCode = StatusOpCode.ConfigNodeReset
    override fun toMeshMessage() = ConfigNodeReset()

    override fun fromResponse(message: MeshMessage): ConfigMessageStatus =
        (message as ConfigNodeResetStatus).transform()
}