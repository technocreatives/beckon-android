package com.technocreatives.beckon.mesh.message

import no.nordicsemi.android.mesh.transport.ConfigNodeReset

data class ResetNode(override val dst: Int) : ConfigMessage {
    override val responseOpCode = StatusOpCode.ConfigNodeReset
    override fun toMeshMessage() = ConfigNodeReset()
}