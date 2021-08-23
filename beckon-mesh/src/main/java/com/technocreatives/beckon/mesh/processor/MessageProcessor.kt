package com.technocreatives.beckon.mesh.processor

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import arrow.core.traverseEither
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.mesh.CreateMeshPduError
import com.technocreatives.beckon.mesh.extensions.info
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import no.nordicsemi.android.mesh.transport.MeshMessage
import timber.log.Timber

data class MeshAndSubject(
    val dst: Int,
    val message: MeshMessage,
    val emitter: CompletableDeferred<Either<CreateMeshPduError, Unit>>
)

data class IdMessage(val id: Int, val message: MeshMessage)
data class IdResult(val id: Int, val result: PduSenderResult)

class MessageQueue(private val pduSender: PduSender) {

    private val messageChannel = Channel<MeshAndSubject>()
    private val resultChannel = Channel<IdResult>()
    private val pduChannel = Channel<Pdu>()
    private val processedMessageChannel = Channel<MeshMessage>()

    suspend fun sendMessage(dst: Int, mesh: MeshMessage): Either<CreateMeshPduError, Unit> {
        Timber.d("sendMessage $dst, ${mesh.info()}")
        val emitter = CompletableDeferred<Either<CreateMeshPduError, Unit>>()
        messageChannel.send(MeshAndSubject(dst, mesh, emitter))
        return emitter.await()
    }

    suspend fun sendPdu(pdu: Pdu) {
        pduChannel.send(pdu)
    }

    suspend fun messageProcessed(message: MeshMessage) {
        processedMessageChannel.send(message)
    }

    fun CoroutineScope.execute() = launch {
        var id = 0
        val map: MutableMap<Int, BeckonMessage> = mutableMapOf()
        var pdus = mutableListOf<Pdu>()
        var isProcessing = false
        while (true) {
            select<Unit> {
                resultChannel.onReceive { result ->
                    Timber.d("resultChannel.onReceive")
                    val message = map.remove(result.id)
                    message?.emitter?.complete(result.result.mapLeft { CreateMeshPduError.BleError(it) }) ?: run {
                        Timber.d("there is no message with id ${result.id}")
                    }
                }
                processedMessageChannel.onReceive { message ->
                    Timber.d("processedMessageChannel.onReceive")
                    val bm = map[id]
                    bm?.let {
                        it.processor.onMessageProcessed(message)
                        val result = pdus.traverseEither { pduSender.sendPdu(it) }.map { }
                        Timber.d("travel result $result")
                        pdus = mutableListOf()
                        isProcessing = false
                        launch {
                            resultChannel.send(IdResult(id, result))
                        }
                    }
                }
                messageChannel.onReceive {
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
        val processor = MessageProcessor(
            message.dst,
            IdMessage(messageId, message.message),
            pduSender,
        )
        val beckonMessage =
            BeckonMessage(messageId, message.message, processor, message.emitter)
        map[messageId] = beckonMessage
        processor.sendMessage()
    }

//    private fun CoroutineScope.handleResponse(channel: ReceiveChannel<IdResult>) = launch {
//        for (result in channel) {
//            Timber.d("handleResponse result $result, map size: ${map.size}")
//        }
//    }

//    private fun CoroutineScope.handlePdu(channel: ReceiveChannel<Pdu>) = launch {
//        for (pdu in channel) {
//            val result = pduSender.sendPdu(pdu)
//            Timber.d("result $result")
//        }
//    }

//    fun CoroutineScope.receivePdu(pdu: Pdu) = launch {
//        Timber.d("Receive pdu ${currentMessage?.id}, ${pdu.data.size}")
//        currentMessage?.processor?.receivePdu(pdu) ?: run {
//            val result = pduSender.sendPdu(pdu)
//            Timber.d("result $result")
//        }
//    }

    suspend fun onReceiveProcessedMessage(
        message: MeshMessage,
        currentId: Int,
        map: Map<Int, BeckonMessage>
    ) {
        Timber.d("onReceiveProcessedMessage ${message.javaClass.simpleName}")
        val currentMessage = map[currentId]
        Timber.d("currentMessage id: ${currentMessage?.id}")
        currentMessage?.let {
            it.processor.onMessageProcessed(message)
        }
    }
}

typealias PduSenderResult = Either<BeckonActionError, Unit>

interface PduSender {
    fun createPdu(dst: Int, meshMessage: MeshMessage): Either<CreateMeshPduError, Unit>
    suspend fun sendPdu(pdu: Pdu): PduSenderResult
}

data class BeckonMessage(
    val id: Int,
    val messsage: MeshMessage,
    val processor: MessageProcessor,
    val emitter: CompletableDeferred<Either<CreateMeshPduError, Unit>>
)

class MessageProcessor(
    val dst: Int,
    val idMesasage: IdMessage,
    val sender: PduSender,
) {

    private val emitter = CompletableDeferred<Either<CreateMeshPduError, IdMessage>>()

    // send
    suspend fun sendMessage(): Either<CreateMeshPduError, IdMessage> {
        Timber.d("sendMessage")
        return sender.createPdu(dst, idMesasage.message)
            .flatMap { emitter.await() }
    }

    fun onMessageProcessed(message: MeshMessage) {
        emitter.complete(idMesasage.copy(message = message).right())
    }

}


data class Pdu(val data: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false
        other as Pdu
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}