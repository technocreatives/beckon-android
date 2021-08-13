package com.technocreatives.beckon.mesh.callbacks

import no.nordicsemi.android.mesh.MeshStatusCallbacks
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage

abstract class AbstractMessageStatusCallbacks : MeshStatusCallbacks{
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

    override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
        TODO("Not yet implemented")
    }

    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        TODO("Not yet implemented")
    }
}