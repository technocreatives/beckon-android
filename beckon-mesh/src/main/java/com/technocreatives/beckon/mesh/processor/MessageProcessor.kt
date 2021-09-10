package com.technocreatives.beckon.mesh.processor

import arrow.core.*
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.extensions.info
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
    abstract fun id(): Long?
}

private data class UnAck(
    override val dst: Int,
    override val message: MeshMessage,
    override val emitter: CompletableDeferred<Either<SendMessageError, Unit>>
) : CompletableMessage() {
    override fun toString(): String {
        return "UnAck: ${message.info()}"
    }

    override fun id(): Long? = null
    override fun isAck() = false
    override fun opCode(): Int? = null
}

private data class TimeOutData(
    val id: Long,
    val dst: Int,
    val opCode: Int,
)

private data class Ack(
    val id: Long,
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
    override fun id() = id
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
    val id: Long,
    val dst: Int,
    val opCode: Int,
    val mesh: MeshMessage,
    val emitter: CompletableDeferred<Either<SendAckMessageError, MeshMessage>>
) {
    override fun toString(): String {
        return "AckEmitter: OpCode=$opCode, dst=$dst, ${mesh.javaClass.simpleName}"
    }

    fun isMatching(dst: Int, opCode: Int) =
        (this.dst == dst || this.dst == MeshAddress.UNASSIGNED_ADDRESS) && this.opCode == opCode
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

class MessageProcessor(private val pduSender: PduSender, private val timeout: Long) {

    private val incomingAckMessageChannel = Channel<AckEmitter>()
    private val timeoutAckMessageChannel = Channel<Long>()
    private val receivedAckMessageChannel = Channel<MeshMessage>()
    private val onMessageBeingSentAckMessageChannel = Channel<Long>()

    private val incomingMessageChannel = Channel<CompletableMessage>()
    private val pduSenderResultChannel = Channel<IdResult>()
    private val pduChannel = Channel<Pdu>()
    private val processedMessageChannel = Channel<MeshMessage>()

    private var ackId: Long = 0

    internal suspend inline fun sendAckMessage(
        dst: Int,
        mesh: MeshMessage,
        opCode: Int,
    ): Either<SendAckMessageError, MeshMessage> {
        val emitter = CompletableDeferred<Either<SendAckMessageError, MeshMessage>>()
        val ackEmitter = AckEmitter(ackId, dst, opCode, mesh, emitter)
        ackId += 1
        incomingAckMessageChannel.send(ackEmitter)
        return sendMessage(ackEmitter.id, dst, mesh, opCode).flatMap { emitter.await() }
    }

    suspend fun sendMessage(dst: Int, mesh: MeshMessage): Either<SendMessageError, Unit> {
        Timber.d("sendMessage $dst, ${mesh.info()}")
        val emitter = CompletableDeferred<Either<SendMessageError, Unit>>()
        incomingMessageChannel.send(UnAck(dst, mesh, emitter))
        return emitter.await()
    }

    private suspend fun sendMessage(
        id: Long,
        dst: Int,
        mesh: MeshMessage,
        opCode: Int
    ): Either<SendMessageError, Unit> {
        Timber.d("sendMessage $dst, ${mesh.info()}")
        val emitter = CompletableDeferred<Either<SendMessageError, Unit>>()
        incomingMessageChannel.send(Ack(id, dst, mesh, emitter, opCode))
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
        val ackMessageQueue = mutableListOf<AckEmitter>()
        val timeOutMap = mutableMapOf<Long, Job>()
        while (true) {
            select<Unit> {
                receivedAckMessageChannel.onReceive { message ->
                    Timber.d("receivedAckMessageChannel.onReceive Map: ${ackMessageQueue.size}, opCode: ${message.opCode}, src = ${message.src}")
                    val foundIndex =
                        ackMessageQueue.indexOfFirst { it.isMatching(message.src, message.opCode) }
                    Timber.d("receivedAckMessageChannel.onReceive foundIndex = $foundIndex")
                    if (foundIndex != -1) {
                        val ackEmitter = ackMessageQueue.removeAt(foundIndex)
                        Timber.d("receivedAckMessageChannel found message $ackEmitter, queue size after: ${ackMessageQueue.size}")
                        timeOutMap[ackEmitter.id]?.cancel()
                        ackEmitter.emitter.complete(message.right())
                    } else {
                        Timber.w("No message found")
                    }
                    Timber.d("receivedMessageChannel after $ackMessageQueue")
                }

                timeoutAckMessageChannel.onReceive { data ->
                    val foundIndex =
                        ackMessageQueue.indexOfFirst { it.id == data }
                    Timber.d("timeoutAckMessageChannel.onReceive foundIndex = $foundIndex")
                    if (foundIndex != -1) {
                        val ackEmitter = ackMessageQueue.removeAt(foundIndex)
                        Timber.d("timeoutAckMessageChannel found message $ackEmitter, queue size after: ${ackMessageQueue.size}")
                        ackEmitter.emitter.complete(TimeoutError.left())
                    } else {
                        Timber.w("No message found for $data")
                    }
                }

                onMessageBeingSentAckMessageChannel.onReceive { id ->
                    val ackEmitterOrNull =
                        ackMessageQueue.find { it.id == id }
                    Timber.d("onMessageBeingSentAckMessageChannel.onReceive foundIndex = $ackEmitterOrNull")
                    if (ackEmitterOrNull != null) {
                        val job = launch {
                            delay(timeout)
                            timeoutAckMessageChannel.send(id)
                        }
                        timeOutMap[id] = job
                    } else {
                        Timber.w("No message found for $id")
                    }
                }

                incomingAckMessageChannel.onReceive {
                    Timber.d("incomingAckMessageChannel.onReceive queue size = ${ackMessageQueue.size}, opCode = ${it.opCode}, dst = ${it.dst}")
                    ackMessageQueue.add(it)
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
                        BleError(it)
                    }) ?: run {
                        Timber.d("there is no message with id ${result.id}")
                    }
                    Timber.d("travel result $result")
                    fun isDuplicatedAck(dst: Int, opCode: Int): Boolean {
                        return processingMap.filterValues { it.message.dst == dst && it.message.opCode() == opCode }
                            .isNotEmpty()
                    }

                    fun shouldGoNext(message: BeckonMessage): Boolean {
                        val opCode = message.message.opCode()
                        return if (opCode == null) {
                            true
                        } else {
                            !isDuplicatedAck(message.message.dst, opCode)
                        }
                    }

                    val foundIndex = messagesQueue.indexOfFirst { shouldGoNext(it) }

                    if (foundIndex != -1) {
                        val bm1 = messagesQueue.removeAt(foundIndex)
                        isProcessing = true
                        id += 1
                        processingMap[id] = bm1
                        sendBeckonMessage(bm1)
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

                        launch {
                            pduSenderResultChannel.send(IdResult(id, result))
                        }
                    }
                }

                incomingMessageChannel.onReceive {
                    Timber.d("incomingMessageChannel.onReceive isProcessing = $isProcessing, messageQueue.size = ${messagesQueue.size}")
                    val bm = processMessage(it, id)
                    if (!isProcessing && messagesQueue.isEmpty()) {
                        Timber.d("incomingMessageChannel Sending message")
                        isProcessing = true
                        id += 1
                        processingMap[id] = bm
                        sendBeckonMessage(bm)
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

    private fun CoroutineScope.sendBeckonMessage(beckonMessage: BeckonMessage) =
        launch {
            beckonMessage.item.sendMessage()
            beckonMessage.message.id()?.let {
                onMessageBeingSentAckMessageChannel.send(it)
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

