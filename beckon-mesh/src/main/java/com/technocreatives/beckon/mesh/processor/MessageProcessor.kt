package com.technocreatives.beckon.mesh.processor

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import arrow.core.traverseEither
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.mesh.BleError
import com.technocreatives.beckon.mesh.SendMessageError
import com.technocreatives.beckon.mesh.extensions.info
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber

@JvmInline
value class Pdu(val data: ByteArray)

typealias PduSenderResult = Either<BeckonActionError, Unit>

interface PduSender {
    fun createPdu(dst: Int, meshMessage: MeshMessage): Either<SendMessageError, Unit>
    suspend fun sendPdu(pdu: Pdu): PduSenderResult
}

private data class IdMessage(val id: Int, val message: MeshMessage)
private data class IdResult(val id: Int, val result: PduSenderResult)

private data class MeshAndSubject(
    val dst: Int,
    val message: MeshMessage,
    val emitter: CompletableDeferred<Either<SendMessageError, Unit>>
)

private data class BeckonMessage(
    val id: Int,
    val message: MeshMessage,
    val item: MessageItem,
    val emitter: CompletableDeferred<Either<SendMessageError, Unit>>
)

data class AckEmitter<T : MeshMessage>(
    val opCode: Int,
    val dst: Int,
    val emitter: CompletableDeferred<Either<SendMessageError, T>>
)

private class MessageItem(
    private val dst: Int,
    private val idMessage: IdMessage,
    private val sender: PduSender,
) {

    private val emitter = CompletableDeferred<Either<SendMessageError, IdMessage>>()

    // send
    suspend fun sendMessage(): Either<SendMessageError, IdMessage> {
        Timber.d("sendMessage")
        return sender.createPdu(dst, idMessage.message)
            .flatMap { emitter.await() }
    }

    fun onMessageProcessed(message: MeshMessage) {
        emitter.complete(idMessage.copy(message = message).right())
    }

}

class MessageProcessor(private val pduSender: PduSender) {

    private val incomingMessageChannel = Channel<MeshAndSubject>()
    val incomingAckMessageChannel = Channel<AckEmitter<MeshMessage>>()
    private val resultChannel = Channel<IdResult>()
    private val pduChannel = Channel<Pdu>()
    private val processedMessageChannel = Channel<MeshMessage>()
    private val receivedMessageChannel = Channel<MeshMessage>()

    suspend inline fun sendAckMessage(
        dst: Int,
        mesh: MeshMessage,
        opCode: Int
    ): Either<SendMessageError, MeshMessage> {
        val emitter = CompletableDeferred<Either<SendMessageError, MeshMessage>>()
        val ackEmitter = AckEmitter(opCode, dst, emitter)
        incomingAckMessageChannel.send(ackEmitter)
        return sendMessage(dst, mesh).flatMap { emitter.await() }
    }

    suspend fun sendMessage(dst: Int, mesh: MeshMessage): Either<SendMessageError, Unit> {
        Timber.d("sendMessage $dst, ${mesh.info()}")
        val emitter = CompletableDeferred<Either<SendMessageError, Unit>>()
        incomingMessageChannel.send(MeshAndSubject(dst, mesh, emitter))
        return emitter.await()
    }

    suspend fun sendPdu(pdu: Pdu) {
        pduChannel.send(pdu)
    }

    suspend fun messageReceived(message: MeshMessage) {
        receivedMessageChannel.send(message)
    }

    suspend fun messageProcessed(message: MeshMessage) {
        processedMessageChannel.send(message)
    }

    fun CoroutineScope.execute() {
        ackProcess()
        process()
    }

    private fun CoroutineScope.ackProcess() = launch {
        val map: MutableMap<Int, AckEmitter<MeshMessage>> = mutableMapOf()
        while (true) {
            select<Unit> {
                receivedMessageChannel.onReceive { message ->
                    Timber.d("receivedMessageChannel.onReceive")
                    map[message.opCode]?.let {
                        if (it.dst == message.src) {
                            map.remove(message.opCode)?.emitter?.complete(message.right())
                        }
                    }
                }

                incomingAckMessageChannel.onReceive {
                    Timber.d("incomingAckMessageChannel.onReceive")
                    map[it.opCode] = it
                }
            }
        }
    }

    private fun CoroutineScope.process() = launch {
        var id = 0
        val map: MutableMap<Int, BeckonMessage> = mutableMapOf()
        var pdus = mutableListOf<Pdu>()
        var isProcessing = false
        while (true) {
            select<Unit> {
                resultChannel.onReceive { result ->
                    Timber.d("resultChannel.onReceive")
                    val message = map.remove(result.id)
                    message?.emitter?.complete(result.result.mapLeft {
                        BleError(
                            it
                        )
                    }) ?: run {
                        Timber.d("there is no message with id ${result.id}")
                    }
                }
                processedMessageChannel.onReceive { message ->
                    Timber.d("processedMessageChannel.onReceive")
                    val bm = map[id]
                    bm?.let {
                        it.item.onMessageProcessed(message)
                        val result = pdus.traverseEither { pduSender.sendPdu(it) }.map { }
                        Timber.d("travel result $result")
                        pdus = mutableListOf()
                        isProcessing = false
                        launch {
                            resultChannel.send(IdResult(id, result))
                        }
                    }
                }
                incomingMessageChannel.onReceive {
                    Timber.d("messageChannel.onReceive")
                    isProcessing = true
                    id += 1
                    processMessage(it, id, map)
                }
                pduChannel.onReceive {
                    Timber.d("pduChannel.onReceive")
                    if (isProcessing) {
                        pdus.add(it)
                    } else {
                        pduSender.sendPdu(it)
                    }
                }

            }
        }
    }

    private fun CoroutineScope.processMessage(
        message: MeshAndSubject,
        messageId: Int,
        map: MutableMap<Int, BeckonMessage>
    ) = launch {
        Timber.d("processMessage $messageId $message")
        val processor = MessageItem(
            message.dst,
            IdMessage(messageId, message.message),
            pduSender,
        )
        val beckonMessage =
            BeckonMessage(messageId, message.message, processor, message.emitter)
        map[messageId] = beckonMessage
        processor.sendMessage()
    }
}

