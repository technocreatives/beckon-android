package com.technocreatives.beckon.mesh.callbacks

import androidx.annotation.CallSuper
import com.technocreatives.beckon.mesh.BeckonMeshManagerApi
import no.nordicsemi.android.mesh.MeshStatusCallbacks
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_NODE_RESET_STATUS
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage

abstract class AbstractMessageStatusCallbacks(val meshApi: BeckonMeshManagerApi) : MeshStatusCallbacks{
    override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
        TODO("Not yet implemented")
    }

    override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
        TODO("Not yet implemented")
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
        TODO("Not yet implemented")
    }

    @CallSuper
    override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
        if(meshMessage.opCode == CONFIG_NODE_RESET_STATUS) {
            meshApi.loadNodes()
        }
    }

    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        TODO("Not yet implemented")
    }
}