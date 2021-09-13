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


private data class PduSenderResult(val id: Long, val result: Either<BeckonActionError, Unit>)

data class AckEmitter(
    val id: Long,
    val dst: Int,
    val opCode: Int,
    val mesh: MeshMessage,
    val emitter: CompletableDeferred<Either<SendAckMessageError, MeshMessage>>
) {
    override fun toString(): String {
        return "AckEmitter: Id=$id, OpCode=$opCode, dst=$dst, ${mesh.javaClass.simpleName}"
    }

    fun isMatching(dst: Int, opCode: Int) =
        (this.dst == dst || this.dst == MeshAddress.UNASSIGNED_ADDRESS) && this.opCode == opCode
}

internal class MessageSender(
    private val dst: Int,
    private val message: MeshMessage,
    private val sender: PduSender,
) {

    private val emitter = CompletableDeferred<Either<SendMessageError, Unit>>()

    suspend fun sendMessage(): Either<SendMessageError, Unit> {
        Timber.d("MessageProcessor MessageItem sendMessage")
        return sender.createPdu(dst, message)
            .flatMap { emitter.await() }
    }

    fun sendMessageCompleted() {
        emitter.complete(Unit.right())
    }

}

class MessageProcessor(private val pduSender: PduSender, private val timeout: Long) {

    private val incomingAckMessageChannel = Channel<AckEmitter>()
    private val timeoutAckMessageChannel = Channel<Long>()
    private val receivedAckMessageChannel = Channel<MeshMessage>()
    private val onMessageBeingSentAckMessageChannel = Channel<Long>()

    private val incomingMessageChannel = Channel<CompletableMessage>()
    private val pduSenderResultChannel = Channel<PduSenderResult>()
    private val pduChannel = Channel<Pdu>()
    private val processedMessageChannel = Channel<MeshMessage>()
    private val onAckMessageFinishChannel = Channel<Long>()

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
                        launch {
                            onAckMessageFinishChannel.send(ackEmitter.id)
                        }
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
                        launch {
                            onAckMessageFinishChannel.send(ackEmitter.id)
                        }
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

    private class MessageQueue(
        private var id: Long = 0,
        private val processingMap: MutableMap<Long, BeckonMessage> = mutableMapOf(),
        private val messagesQueue: MutableList<BeckonMessage> = mutableListOf(),
        private val pdus: MutableList<Pdu> = mutableListOf(),
        private var isSendingPdu: Boolean = false,
    ) {

        fun id() = id
        fun isProcessing() = isSendingPdu

        fun removeProcessingMessage(messageId: Long): BeckonMessage? {
            return processingMap.remove(messageId)
        }

        fun getProcessingMessage(messageId: Long): BeckonMessage? {
            return processingMap[messageId]
        }

        fun getProcessingMessageByAckId(ackId: Long): BeckonMessage? {
            return processingMap.values.find { it.message.id() == ackId }
        }

        fun getCurrentProcessingMessage(): BeckonMessage? {
            return processingMap[id]
        }

        fun nextMessage(): BeckonMessage? {
            val foundIndex = messagesQueue.indexOfFirst { shouldGoNext(it) }
            return if (foundIndex != -1) messagesQueue.removeAt(foundIndex)
            else null
        }

        fun clearProcessing() {
            isSendingPdu = false
            pdus.clear()
        }

        fun addPdu(pdu: Pdu) {
            pdus.add(pdu)
        }

        fun pdus() = pdus.toList()

        fun shouldSendMessageImmediately(beckonMessage: BeckonMessage): Boolean {
            return !isSendingPdu && (messagesQueue.isEmpty() || shouldGoNext(beckonMessage))
        }

        fun process(message: BeckonMessage) {
            isSendingPdu = true
            id += 1
            processingMap[id] = message
        }

        fun queue(message: BeckonMessage): Boolean {
            return messagesQueue.add(message)
        }

        private fun isDuplicatedAck(dst: Int, opCode: Int): Boolean {
            return processingMap.filterValues { it.message.dst == dst && it.message.opCode() == opCode }
                .isNotEmpty()
        }

        private fun shouldGoNext(message: BeckonMessage): Boolean {
            val opCode = message.message.opCode()
            return if (opCode == null) {
                true
            } else {
                !isDuplicatedAck(message.message.dst, opCode)
            }
        }

        override fun toString(): String {
            return "MessageQueue(id=$id, processingMap=$processingMap, messagesQueue=$messagesQueue, pdus=$pdus, isProcessing=$isSendingPdu)"
        }

    }

    private fun CoroutineScope.process() = launch {
        val queue = MessageQueue()
        while (true) {
            select<Unit> {
                pduSenderResultChannel.onReceive { result ->
                    Timber.d("resultChannel.onReceive- travel result $result")
                    // todo remove if message is unack
                    // ack message needs to wait to timeout or get the response
                    val message = queue.getProcessingMessage(result.id)
                    if (message != null) {
                        if (!message.message.isAck()) {
                            queue.removeProcessingMessage(result.id)
                        }
                        message.complete(result.result)
                    } else {
                        Timber.w("there is no message with id ${result.id}")
                    }
                    queue.nextMessage()?.let { bm ->
                        queue.process(bm)
                        sendBeckonMessage(bm)
                    }
                }

                processedMessageChannel.onReceive { message ->
                    Timber.d("processedMessageChannel.onReceive ${message.info()}")
                    val bm = queue.getCurrentProcessingMessage()
                    bm?.let { bk ->
                        bk.sender.sendMessageCompleted()
                        val result = queue.pdus().traverseEither { pduSender.sendPdu(it) }.map { }
                        queue.clearProcessing()
                        launch {
                            pduSenderResultChannel.send(PduSenderResult(queue.id(), result))
                        }
                    }
                }

                incomingMessageChannel.onReceive {
                    Timber.d("incomingMessageChannel.onReceive $queue")
                    val bm = processMessage(it, queue.id())
                    if (queue.shouldSendMessageImmediately(bm)) {
                        Timber.d("incomingMessageChannel Sending message")
                        queue.process(bm)
                        sendBeckonMessage(bm)
                    } else {
                        Timber.d(" incomingMessageChannel add to queue")
                        queue.queue(bm)
                    }
                }

                onAckMessageFinishChannel.onReceive { ackId ->
                    val message = queue.getProcessingMessageByAckId(ackId)
                    if (message != null) {
                        Timber.d("onAckMessageFinishChannel $message")
                        queue.removeProcessingMessage(message.id)
                    }
                    queue.nextMessage()?.let { bm ->
                        queue.process(bm)
                        sendBeckonMessage(bm)
                    }
                }

                pduChannel.onReceive {
                    Timber.d("pduChannel.onReceive")
                    if (queue.isProcessing()) {
                        queue.addPdu(it)
                    } else {
                        pduSender.sendPdu(it)
                    }
                }

            }
        }
    }

    private fun CoroutineScope.sendBeckonMessage(beckonMessage: BeckonMessage) =
        launch {
            beckonMessage.sender.sendMessage()
            beckonMessage.message.id()?.let {
                onMessageBeingSentAckMessageChannel.send(it)
            }
        }

    private fun processMessage(
        message: CompletableMessage,
        messageId: Long,
    ): BeckonMessage {
        Timber.d("processMessage $messageId $message")
        val processor = MessageSender(
            message.dst,
            message.message,
            pduSender,
        )
        return BeckonMessage(messageId, message, processor)
    }
}