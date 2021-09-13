package com.technocreatives.beckon.mesh.processor

internal class MessageQueue(
    private var id: Long = 0,
    private val processingMap: MutableMap<Long, BeckonMessage> = mutableMapOf(),
    private val messagesQueue: MutableList<BeckonMessage> = mutableListOf(),
    private val pdus: MutableList<Pdu> = mutableListOf(),
    private var isSendingPdu: Boolean = false,
) {

    fun id() = id
    fun isSendingPdu() = isSendingPdu

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
        return "MessageQueue(id=$id, processingMap=$processingMap, messagesQueue=$messagesQueue, isSendingPdu=$isSendingPdu)"
    }


}