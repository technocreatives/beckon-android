package com.technocreatives.beckon.mesh.callbacks

import com.technocreatives.beckon.mesh.blockingEmit
import com.technocreatives.beckon.mesh.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.mesh.MeshStatusCallbacks
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber

class MessageStatusCallbacks : MeshStatusCallbacks {

    private val subject by lazy {
        MutableSharedFlow<MessageStatus>(1)
    }

    fun status(): Flow<MessageStatus> =
        subject.asSharedFlow()

    override fun onTransactionFailed(dst: Int, hasIncompleteTimerExpired: Boolean) {
        Timber.w("onTransactionFailed dst: $dst, hasIncompleteTimerExpired $hasIncompleteTimerExpired")
        subject.blockingEmit(MessageStatus.TransactionFailed(dst, hasIncompleteTimerExpired))
    }

    override fun onUnknownPduReceived(src: Int, accessPayload: ByteArray?) {
        Timber.w("onUnknownPduReceived src: $src, payload: ${accessPayload?.debug()}")
        subject.blockingEmit(MessageStatus.UnknownPduReceived(src, accessPayload))
    }

    override fun onBlockAcknowledgementProcessed(dst: Int, message: ControlMessage) {
        Timber.d("onBlockAcknowledgementProcessed dst: $dst, ${message.debug()}")
        subject.blockingEmit(MessageStatus.BlockAcknowledgementProcessed(dst, message))
    }

    override fun onBlockAcknowledgementReceived(src: Int, message: ControlMessage) {
        Timber.d("onBlockAcknowledgementReceived src: $src, ${message.debug()}")
        subject.blockingEmit(MessageStatus.BlockAcknowledgementReceived(src, message))
    }

    override fun onMeshMessageProcessed(dst: Int, meshMessage: MeshMessage) {
        Timber.d("onMeshMessageProcessed dst: $dst, ${meshMessage.debug()}")
        subject.blockingEmit(MessageStatus.MeshMessageProcessed(dst, meshMessage))
    }

    override fun onMeshMessageReceived(src: Int, meshMessage: MeshMessage) {
        Timber.d("onMeshMessageReceived src: $src, ${meshMessage.debug()}")
        subject.blockingEmit(MessageStatus.MeshMessageReceived(src, meshMessage))
    }

    override fun onMessageDecryptionFailed(meshLayer: String?, errorMessage: String?) {
        Timber.d("onMessageDecryptionFailed meshLayer: $meshLayer, error: $errorMessage")
        subject.blockingEmit(MessageStatus.MessageDecryptionFailed(meshLayer, errorMessage))
    }
}

sealed class MessageStatus {
    data class TransactionFailed(val dst: Int, val hasIncompleteTimerExpired: Boolean) :
        MessageStatus()

    data class UnknownPduReceived(val src: Int, val accessPayload: ByteArray?) : MessageStatus()
    data class BlockAcknowledgementProcessed(val dst: Int, val message: ControlMessage) :
        MessageStatus()

    data class BlockAcknowledgementReceived(val src: Int, val message: ControlMessage) :
        MessageStatus()

    data class MeshMessageProcessed(val dst: Int, val meshMessage: MeshMessage) : MessageStatus()
    data class MeshMessageReceived(val src: Int, val meshMessage: MeshMessage) : MessageStatus()
    data class MessageDecryptionFailed(val meshLayer: String?, val errorMessage: String?) :
        MessageStatus()
}