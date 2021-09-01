package com.technocreatives.beckon.mesh.callbacks

import androidx.annotation.CallSuper
import com.technocreatives.beckon.mesh.BeckonMeshManagerApi
import com.technocreatives.beckon.mesh.extensions.toHex
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.mesh.MeshStatusCallbacks
import no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_NODE_RESET_STATUS
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage

abstract class AbstractMessageStatusCallbacks(private val meshApi: BeckonMeshManagerApi) : MeshStatusCallbacks{
    override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) {
        TODO("Callback is not implemented: onTransactionFailed $dst $hasIncompleteTimerExpired")
    }

    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray?) {
        TODO("Callback is not implemented: onUnknownPduReceived $src ${accessPayload?.toHex()}")
    }

    override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
        TODO("Callback is not implemented: onBlockAcknowledgementProcessed $dst")
    }

    override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
        TODO("Callback is not implemented: onBlockAcknowledgementReceived $src")
    }

    override fun onMeshMessageProcessed(dst: Int, message: MeshMessage) {
        TODO("Callback is not implemented: onMeshMessageProcessed $dst")
    }

    @CallSuper
    override fun onMeshMessageReceived(src: Int, message: MeshMessage) {
        if(message.opCode == CONFIG_NODE_RESET_STATUS) {
            runBlocking {
                meshApi.updateNetwork()
            }
        }
    }

    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        TODO("Callback is not implemented: onMessageDecryptionFailed $meshLayer")
    }
}