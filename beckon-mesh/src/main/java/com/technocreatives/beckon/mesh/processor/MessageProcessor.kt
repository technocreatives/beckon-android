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

private sealed class CompletableMessage {
    abstract val dst: Int
    abstract val message: MeshMessage
    abstract val emitter: CompletableDeferred<Either<SendMessageError, Unit>>
    abstract fun isAck(): Boolean
    abstract fun opCode(): Int?
}

private data class UnAck(
    override val dst: Int,
    override val message: MeshMessage,
    override val emitter: CompletableDeferred<Either<SendMessageError, Unit>>
) : CompletableMessage() {
    override fun toString(): String {
        return "UnAck: ${message.info()}"
    }

    override fun isAck() = false
    override fun opCode(): Int? = null
}

private data class Ack(
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
}


private data class BeckonMessage(
    val id: Int,
    val message: CompletableMessage,
    val item: MessageItem
) {
    override fun toString(): String {
        return "BeckonMessage: isAck=${message.isAck()} $id ${message.message.info()}"
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

class MessageProcessor(private val pduSender: PduSender) {

    internal val incomingAckMessageChannel = Channel<AckEmitter>()
    private val receivedAckMessageChannel = Channel<MeshMessage>()

    private val incomingMessageChannel = Channel<CompletableMessage>()
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
        return sendMessage(dst, mesh, opCode).flatMap { emitter.await() }
    }

    suspend fun sendMessage(dst: Int, mesh: MeshMessage): Either<SendMessageError, Unit> {
        Timber.d("sendMessage $dst, ${mesh.info()}")
        val emitter = CompletableDeferred<Either<SendMessageError, Unit>>()
        incomingMessageChannel.send(UnAck(dst, mesh, emitter))
        return emitter.await()
    }

    private suspend fun sendMessage(
        dst: Int,
        mesh: MeshMessage,
        opCode: Int
    ): Either<SendMessageError, Unit> {
        Timber.d("sendMessage $dst, ${mesh.info()}")
        val emitter = CompletableDeferred<Either<SendMessageError, Unit>>()
        incomingMessageChannel.send(Ack(dst, mesh, emitter, opCode))
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
        val map = mutableListOf<AckEmitter>()
        while (true) {
            select<Unit> {
                receivedAckMessageChannel.onReceive { message ->
                    Timber.d("receivedAckMessageChannel.onReceive Map: ${map.size}, opCode: ${message.opCode}")
                    val foundIndex =
                        map.indexOfFirst { (it.dst == message.src || it.dst == MeshAddress.UNASSIGNED_ADDRESS) && it.opCode == message.opCode }
                    if (foundIndex != -1) {
                        val ackEmitter = map.removeAt(foundIndex)
                        Timber.d("receivedAckMessageChannel found message $ackEmitter")
                        ackEmitter.emitter.complete(message.right())
                    } else {
                        Timber.d("No message found")
                    }
                    Timber.d("receivedMessageChannel after $map")
                }

                incomingAckMessageChannel.onReceive {
                    Timber.d("incomingAckMessageChannel.onReceive")
//                    if(map[id] != null) {
                    map.add(it)
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
                    message?.message?.emitter?.complete(result.result.mapLeft {
                        BleError(
                            it
                        )
                    }) ?: run {
                        Timber.d("there is no message with id ${result.id}")
                    }
                }
                processedMessageChannel.onReceive { message ->
                    Timber.d("processedMessageChannel.onReceive ${message.info()}")
                    val bm = processingMap[id]
                    bm?.let { bk ->
                        bk.item.onMessageProcessed(message)
                        val result = pdus.traverseEither { pduSender.sendPdu(it) }.map { }
                        pdus.clear()
                        isProcessing = false
                        Timber.d("travel result $result")
                        fun isDuplicatedAck(dst: Int, opCode: Int): Boolean {
                            return processingMap.filterValues { it.message.dst == dst && it.message.opCode() == opCode }
                                .isNotEmpty()
                        }

                        fun isOk(message: BeckonMessage): Boolean {
                            val opCode = message.message.opCode()
                            return if (opCode == null) {
                                true
                            } else {
                                !isDuplicatedAck(message.message.dst, opCode)
                            }
                        }

                        val foundIndex = messagesQueue.indexOfFirst { isOk(it) }

                        if (foundIndex != -1) {
                            val bm1 = messagesQueue.removeAt(foundIndex)
//                             send next message on the queue
                            isProcessing = true
                            id += 1
                            processingMap[id] = bm1
                            launch {
                                bm1.item.sendMessage()
                            }
                        }


//                        if (messagesQueue.isNotEmpty()) {
//                            isProcessing = true
//                            id += 1
//                            val bm1 = messagesQueue[0]
//                            processingMap[id] = bm1
//                            launch {
//                                bm1.item.sendMessage()
//                            }
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
        message: CompletableMessage,
        messageId: Int,
    ): BeckonMessage {
        Timber.d("processMessage $messageId $message")
        val processor = MessageItem(
            message.dst,
            IdMessage(messageId, message.message),
            pduSender,
        )
        return BeckonMessage(messageId, message, processor)
    }
}

