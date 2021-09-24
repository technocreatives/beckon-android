package com.technocreatives.beckon.mesh.processor

import com.technocreatives.beckon.mesh.extensions.info

internal class MessageQueue(
    private var id: Long = 0,
    private val processingMap: MutableMap<Long, BeckonMessage> = mutableMapOf(),
    private val messagesQueue: MutableList<BeckonMessage> = mutableListOf(),
    private val pdus: MutableList<Pdu> = mutableListOf(),
    private var sendingId: Long? = null,
) {

    fun nextId(): Long {
        id += 1
        return id
    }

    fun isSendingPdu() = sendingId != null

    fun removeProcessingMessage(messageId: Long): BeckonMessage? {
        return processingMap.remove(messageId)
    }

    fun getProcessingMessage(messageId: Long): BeckonMessage? {
        return processingMap[messageId]
    }

    fun getProcessingMessageByAckId(ackId: Long): BeckonMessage? {
        return processingMap.values.find { it.message.ackId() == ackId }
    }

    fun getCurrentProcessingMessage(): BeckonMessage? {
        return sendingId?.let { processingMap[it] }
    }

    fun nextMessage(): BeckonMessage? {
        val foundIndex = messagesQueue.indexOfFirst { shouldGoNext(it) }
        return if (foundIndex != -1) messagesQueue.removeAt(foundIndex)
        else null
    }

    fun clearProcessing() {
        sendingId = null
        pdus.clear()
    }

    fun addPdu(pdu: Pdu) {
        pdus.add(pdu)
    }

    fun pdus() = pdus.toList()

    fun shouldSendMessageImmediately(beckonMessage: BeckonMessage): Boolean {
        return sendingId == null && (messagesQueue.isEmpty() || shouldGoNext(beckonMessage))
    }

    fun process(message: BeckonMessage) {
        sendingId = message.id
        processingMap[message.id] = message
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
        val pm = processingMap.map { "[${it.key}] => ${it.value.message.message.info()}" }
            .joinToString(";")
        return "MessageQueue(id=$id, processingMapSize=${processingMap.size}, messagesQueueSize=${messagesQueue.size}, isSendingPdu=$sendingId, {$pm})"
    }


}