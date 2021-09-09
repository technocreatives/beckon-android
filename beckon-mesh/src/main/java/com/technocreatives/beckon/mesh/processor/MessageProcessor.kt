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
import no.nordicsemi.android.mesh.utils.MeshAddress
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
) {
    override fun toString(): String {
        return "MeshAndSubject: ${message.info()}"
    }
}

private data class BeckonMessage(
    val id: Int,
    val message: MeshMessage,
    val item: MessageItem,
    val emitter: CompletableDeferred<Either<SendMessageError, Unit>>
) {
    override fun toString(): String {
        return "BeckonMessage: $id ${message.info()}"
    }
}

data class AckEmitter(
    val dst: Int,
    val opCode: Int,
    val mesh: MeshMessage,
    val emitter: CompletableDeferred<Either<SendMessageError, MeshMessage>>
) {
    override fun toString(): String {
        return "OpCode $opCode, dst: $dst"
    }
}

private class MessageItem(
    private val dst: Int,
    private val idMessage: IdMessage,
    private val sender: PduSender,
) {

    private val emitter = CompletableDeferred<Either<SendMessageError, IdMessage>>()

    suspend fun sendMessage(): Either<SendMessageError, IdMessage> {
        Timber.d("MessageProcessor MessageItem sendMessage")
        return sender.createPdu(dst, idMessage.message)
            .flatMap { emitter.await() }
    }

    fun onMessageProcessed(message: MeshMessage) {
        emitter.complete(idMessage.copy(message = message).right())
    }

}

data class AckIdMessage(val dst: Int, val opCode: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AckIdMessage

        if (opCode != other.opCode) return false
        if (dst == 0 || other.dst == 0) return true
        if (dst != other.dst) return false
        return true
    }

    override fun hashCode(): Int {
        var result = dst
        result = 31 * result + opCode
        return result
    }
}

class MessageProcessor(private val pduSender: PduSender) {

    internal val incomingAckMessageChannel = Channel<AckEmitter>()
    private val receivedAckMessageChannel = Channel<MeshMessage>()

    private val incomingMessageChannel = Channel<MeshAndSubject>()
    private val pduSenderResultChannel = Channel<IdResult>()
    private val pduChannel = Channel<Pdu>()
    private val processedMessageChannel = Channel<MeshMessage>()

    internal suspend inline fun sendAckMessage(
        dst: Int,
        mesh: MeshMessage,
        opCode: Int
    ): Either<SendMessageError, MeshMessage> {
        val emitter = CompletableDeferred<Either<SendMessageError, MeshMessage>>()
        val ackEmitter = AckEmitter(dst, opCode, mesh, emitter)
        incomingAckMessageChannel.send(ackEmitter)
        // todo send later
//        return emitter.await()
        return sendMessage(dst, mesh).flatMap { emitter.await() }
    }

    suspend fun sendMessage(dst: Int, mesh: MeshMessage): Either<SendMessageError, Unit> {
        Timber.d("sendMessage $dst, ${mesh.info()}")
        val emitter = CompletableDeferred<Either<SendMessageError, Unit>>()
        incomingMessageChannel.send(MeshAndSubject(dst, mesh, emitter))
        return emitter.await()
    }

    suspend fun sendPdu(pdu: Pdu) {
        Timber.d("SendPdu ${pdu.data.size}")
        pduChannel.send(pdu)
    }

    suspend fun messageReceived(message: MeshMessage) {
        Timber.d("messageReceived ${message.info()}")
        receivedAckMessageChannel.send(message)
    }

    suspend fun messageProcessed(message: MeshMessage) {
        Timber.d("messageProcessed ${message.info()}")
        processedMessageChannel.send(message)
    }

    fun CoroutineScope.execute() {
        ackProcess()
        process()
    }

    private fun CoroutineScope.ackProcess() = launch {
        val map: MutableMap<Int, AckEmitter> = mutableMapOf()
        val queue = mutableListOf<AckEmitter>()
        while (true) {
            select<Unit> {
                receivedAckMessageChannel.onReceive { message ->
                    Timber.d("receivedAckMessageChannel.onReceive Map: ${map.size}, opCode: ${message.opCode}")
                    val id = AckIdMessage(message.src, message.opCode)
                    Timber.d("receivedAckMessageChannel ${map[message.opCode]}")
                    map[message.opCode]?.let {
                        if (it.dst == message.src || it.dst == MeshAddress.UNASSIGNED_ADDRESS) {
                            map.remove(message.opCode)?.emitter?.complete(message.right())
                        }
                    }
                    Timber.d("receivedMessageChannel after $map")
                }

                incomingAckMessageChannel.onReceive {
                    Timber.d("incomingAckMessageChannel.onReceive")
                    val id = AckIdMessage(it.dst, it.opCode)
//                    if(map[id] != null) {
                    map[it.opCode] = it
//                        sendMessage(it.dst, it.mesh)
//                        it.mesh
//                    } else {
//
//                    }
                    Timber.d("incomingAckMessageChannel.onReceive end")
                }
            }
        }
    }

    private fun CoroutineScope.process() = launch {
        var id = 0
        val processingMap: MutableMap<Int, BeckonMessage> = mutableMapOf()
        val messagesQueue: MutableList<BeckonMessage> = mutableListOf()
        val pdus = mutableListOf<Pdu>()
        var isProcessing = false
        while (true) {
            select<Unit> {
                pduSenderResultChannel.onReceive { result ->
                    Timber.d("resultChannel.onReceive")
                    val message = processingMap.remove(result.id)
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
                    val bm = processingMap[id]
                    bm?.let {
                        it.item.onMessageProcessed(message)
                        val result = pdus.traverseEither { pduSender.sendPdu(it) }.map { }
                        pdus.clear()
                        isProcessing = false
                        Timber.d("travel result $result")
                        if (messagesQueue.isNotEmpty()) {
                            // send next message on the queue
                            isProcessing = true
                            id += 1
                            val bm1 = messagesQueue[0]
                            processingMap[id] = bm1
                            launch {
                                bm1.item.sendMessage()
                            }
                        }
                        launch {
                            pduSenderResultChannel.send(IdResult(id, result))
                        }
                    }
                }
                incomingMessageChannel.onReceive {
                    Timber.d("incomingMessageChannel.onReceive $isProcessing ${messagesQueue.size}")
                    val bm = processMessage(it, id)
                    if (!isProcessing && messagesQueue.isEmpty()) {
                        Timber.d("incomingMessageChannel Sending message")
                        isProcessing = true
                        id += 1
                        processingMap[id] = bm
                        launch {
                            bm.item.sendMessage()
                        }
                    } else {
                        Timber.d(" incomingMessageChannel add to queue")
                        messagesQueue.add(bm)
                    }
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

    private fun processMessage(
        message: MeshAndSubject,
        messageId: Int,
    ): BeckonMessage {
        Timber.d("processMessage $messageId $message")
        val processor = MessageItem(
            message.dst,
            IdMessage(messageId, message.message),
            pduSender,
        )
        return BeckonMessage(messageId, message.message, processor, message.emitter)
    }
}

