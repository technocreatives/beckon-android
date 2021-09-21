package com.technocreatives.beckon.mesh.processor

import arrow.core.Either
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.mesh.BleError
import com.technocreatives.beckon.mesh.SendMessageError
import com.technocreatives.beckon.mesh.extensions.info
import kotlinx.coroutines.CompletableDeferred
import no.nordicsemi.android.mesh.transport.MeshMessage

internal data class BeckonMessage(
    val id: Long,
    val message: CompletableMessage,
    val sender: MessageSender
) {

    fun complete(result: Either<BeckonActionError, Unit>) {
        message.emitter.complete(result.mapLeft { BleError(it) })
    }

    override fun toString(): String {
        return "BeckonMessage: isAck=${message.isAck()}, id=$id info=${message.message.info()}"
    }
}

internal sealed class CompletableMessage {
    abstract val dst: Int
    abstract val message: MeshMessage
    abstract val emitter: CompletableDeferred<Either<SendMessageError, Unit>>
    abstract fun isAck(): Boolean
    abstract fun opCode(): Int?
    abstract fun ackId(): Long?
}

internal data class UnAck(
    override val dst: Int,
    override val message: MeshMessage,
    override val emitter: CompletableDeferred<Either<SendMessageError, Unit>>
) : CompletableMessage() {
    override fun toString(): String {
        return "UnAck: ${message.info()}"
    }

    override fun ackId(): Long? = null
    override fun isAck() = false
    override fun opCode(): Int? = null
}

internal data class Ack(
    val ackId: Long,
    override val dst: Int,
    override val message: MeshMessage,
    override val emitter: CompletableDeferred<Either<SendMessageError, Unit>>,
    val opCode: Int,
) : CompletableMessage() {
    override fun toString(): String {
        return "Ack $opCode - ${message.info()}"
    }

    override fun isAck() = true
    override fun opCode(): Int = opCode
    override fun ackId() = ackId
}
