package com.technocreatives.beckon.mesh.processor

import arrow.core.*
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.data.OpCode
import com.technocreatives.beckon.mesh.data.UnassignedAddress
import com.technocreatives.beckon.mesh.data.util.toHex
import com.technocreatives.beckon.mesh.extensions.info
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import no.nordicsemi.android.mesh.transport.AccessMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber

private data class PduSenderResult(
    val messageId: Long,
    val result: Either<BeckonActionError, Unit>
) {
    override fun toString(): String {
        return "PduSenderResult(messageId=$messageId, result=${result.isRight()})"
    }
}

/**
 * All data about an Ack message
 */
data class AckEmitter(
    val ackId: Long,
    val dst: Int,
    val opCode: Int,
    val mesh: MeshMessage,
    val emitter: CompletableDeferred<Either<SendAckMessageError, MeshMessage>>
) {
    override fun toString(): String {
        return "AckEmitter: ackId=$ackId, OpCode=$opCode, dst=$dst, ${mesh.info()}"
    }

    fun isMatching(dst: Int, opCode: Int) =
        this.opCode == opCode && (this.dst == dst || this.dst == UnassignedAddress.value)
}

internal class MessageSender(
    private val dst: Int,
    private val message: MeshMessage,
    private val sender: PduSender,
) {

    private val emitter = CompletableDeferred<Either<SendMessageError, Unit>>()

    suspend fun sendMessage(): Either<SendMessageError, Unit> {
        Timber.d("MessageSender sendMessage $dst ${message.info()}")
        return sender.createPdu(dst, message)
            .flatMap {
                Timber.d("MeshPdu created")
                emitter.await()
            }
    }

    fun sendMessageCompleted() {
        emitter.complete(Unit.right())
    }

}

class MessageProcessor(private val pduSender: PduSender, private val timeout: Long) {
    private val buffer = 10
    private val incomingAckMessageChannel = Channel<AckEmitter>(buffer)
    private val timeoutAckMessageChannel = Channel<Long>(buffer)
    private val receivedAckMessageChannel = Channel<MeshMessage>(buffer)
    private val onMessageBeingSentAckMessageChannel = Channel<Long>(buffer)

    private val incomingMessageChannel = Channel<CompletableMessage>(buffer)
    private val pduSenderResultChannel = Channel<PduSenderResult>(buffer)
    private val pduChannel = Channel<Pdu>(buffer)
    private val processedMessageChannel = Channel<MeshMessage>(buffer)
    private val onAckMessageFinishChannel = Channel<Long>(buffer)

    private var ackId: Long = 0

    internal suspend inline fun sendAckMessage(
        dst: Int,
        mesh: MeshMessage,
        responseOpCode: Int,
    ): Either<SendAckMessageError, MeshMessage> {
        Timber.d("sendAckMessage $dst, $responseOpCode, ${mesh.info()}")
        val emitter = CompletableDeferred<Either<SendAckMessageError, MeshMessage>>()
        val ackEmitter = AckEmitter(ackId, dst, responseOpCode, mesh, emitter)
        ackId += 1
        incomingAckMessageChannel.send(ackEmitter)
        return sendMessage(ackEmitter.ackId, dst, mesh, responseOpCode).flatMap { emitter.await() }
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
//        coroutineScope {
//            launch {
//                pduChannel.send(pdu)
//            }
//        }
        val r = pduChannel.send(pdu)
        Timber.d("pduChannel.send(pdu) $r")
    }

    suspend fun messageReceived(message: MeshMessage) {
        Timber.d("messageReceived ${message.info()}")
        val r = receivedAckMessageChannel.send(message)
        Timber.d("receivedAckMessageChannel.send(message): $r")
    }

    suspend fun messageProcessed(message: MeshMessage) {
        Timber.d("messageProcessed ${message.info()}")
        val r = processedMessageChannel.send(message)
        Timber.d("processedMessageChannel.send(message): $r")
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
                    val opCode = (message.message as? AccessMessage)?.accessPdu?.let {
                        OpCode.parse(it).value
                    } ?: message.opCode
                    Timber.d("receivedAckMessageChannel.onReceive Map: $ackMessageQueue, meshMessageOpCode= ${message.opCode}, parsedOpCode = $opCode, src = ${message.src}")
                    val foundIndex =
                        ackMessageQueue.indexOfFirst { it.isMatching(message.src, opCode) }
                    Timber.d("receivedAckMessageChannel.onReceive foundIndex = $foundIndex")
                    if (foundIndex != -1) {
                        val ackEmitter = ackMessageQueue.removeAt(foundIndex)
                        Timber.d("receivedAckMessageChannel found message $ackEmitter, queue size after: ${ackMessageQueue.size}")
                        timeOutMap[ackEmitter.ackId]?.cancel()
                        ackEmitter.emitter.complete(message.right())
                        launch {
                            onAckMessageFinishChannel.send(ackEmitter.ackId)
                        }
                    } else {
                        Timber.w("No message found")
                    }
                    Timber.d("receivedMessageChannel after $ackMessageQueue")
                }

                timeoutAckMessageChannel.onReceive { data ->
                    val foundIndex =
                        ackMessageQueue.indexOfFirst { it.ackId == data }
                    Timber.d("timeoutAckMessageChannel.onReceive foundIndex = $foundIndex")
                    if (foundIndex != -1) {
                        val ackEmitter = ackMessageQueue.removeAt(foundIndex)
                        Timber.d("timeoutAckMessageChannel found message $ackEmitter, queue size after: ${ackMessageQueue.size}")
                        ackEmitter.emitter.complete(TimeoutError.left())
                        launch {
                            onAckMessageFinishChannel.send(ackEmitter.ackId)
                        }
                    } else {
                        Timber.w("No message found for $data")
                    }
                }

                onMessageBeingSentAckMessageChannel.onReceive { id ->
                    val ackEmitterOrNull =
                        ackMessageQueue.find { it.ackId == id }
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
        val queue = MessageQueue()
        while (true) {
            select<Unit> {
                pduSenderResultChannel.onReceive { result ->
                    Timber.d("resultChannel.onReceive: result=$result $queue")
                    val message = queue.getProcessingMessage(result.messageId)
                    if (message != null) {
                        // remove if message is unack
                        // ack message needs to wait to timeout or get the response
                        if (!message.message.isAck()) {
                            Timber.d("Message ${message.id} is unAck, removing it from processing map")
                            queue.removeProcessingMessage(result.messageId)
                        }
                        message.complete(result.result)
                    } else {
                        Timber.w("there is no message with id ${result.messageId}")
                    }
                    queue.nextMessage()?.let { bm ->
                        queue.process(bm)
                        sendBeckonMessage(bm)
                    }
                    Timber.d("resultChannel.onReceive after: $queue")
                }

                onAckMessageFinishChannel.onReceive { ackId ->
                    Timber.d("onAckMessageFinishChannel.onReceive $queue")
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

                processedMessageChannel.onReceive { message ->
                    Timber.d("processedMessageChannel.onReceive $queue")
                    val bm = queue.getCurrentProcessingMessage()
                    Timber.d("Current processing message $bm")
                    bm?.let { bk ->
                        bk.sender.sendMessageCompleted()
                        val result = queue.pdus().traverseEither { pduSender.sendPdu(it) }.map { }
                        queue.clearProcessing()
                        launch {
                            pduSenderResultChannel.send(
                                PduSenderResult(
                                    bk.id,
                                    result
                                )
                            )
                        }
                    }
                }

                pduChannel.onReceive {
                    Timber.d("pduChannel.onReceive ${it.data.toHex()}")
                    if (queue.isSendingPdu()) {
                        queue.addPdu(it)
                    } else {
                        pduSender.sendPdu(it)
                    }
                }

                incomingMessageChannel.onReceive {
                    Timber.d("incomingMessageChannel.onReceive $queue")
                    val bm = createBeckonMessage(it, queue.nextId())
                    if (queue.shouldSendMessageImmediately(bm)) {
                        Timber.d("incomingMessageChannel Sending message $bm")
                        queue.process(bm)
                        sendBeckonMessage(bm)
                    } else {
                        Timber.d(" incomingMessageChannel add to queue")
                        queue.queue(bm)
                    }
                }

            }
        }
    }

    private fun CoroutineScope.sendBeckonMessage(beckonMessage: BeckonMessage) =
        launch {
            // todo what if this failed?
            val r = beckonMessage.sender.sendMessage()
            Timber.d("sendBeckonMessage done $r $beckonMessage")
            beckonMessage.message.ackId()?.let {
                onMessageBeingSentAckMessageChannel.send(it)
            }
        }

    private fun createBeckonMessage(
        message: CompletableMessage,
        messageId: Long,
    ): BeckonMessage {
        Timber.d("processMessage $messageId $message")
        val sender = MessageSender(
            message.dst,
            message.message,
            pduSender,
        )
        return BeckonMessage(messageId, message, sender)
    }

}