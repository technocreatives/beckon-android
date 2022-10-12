package com.technocreatives.beckon.mesh.processor

import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nordicsemi.android.mesh.transport.MeshMessage

class MessageProcessorTest : StringSpec({
    val pduSender = mockk<PduSender>()
    val timeout = 1000L
    val processor = MessageProcessor(pduSender, timeout)

    fun createMockMeshMessage(): MeshMessage {
        val meshMessage = mockk<MeshMessage>()
        every { meshMessage.message } returns null
        return meshMessage
    }

    fun createMockMeshMessage(opCode: Int): MeshMessage {
        val meshMessage = mockk<MeshMessage>()
        every { meshMessage.message } returns null
        every { meshMessage.opCode } returns opCode
        return meshMessage
    }

    "Sending simple unack message" {
        val message = createMockMeshMessage()
        val byteArray = ByteArray(0)
        val dst = 2
        every { pduSender.createPdu(dst, message) } returns Unit.right()
        coEvery { pduSender.sendPdu(Pdu(byteArray)) } returns Unit.right()
        val processorJob = launch {
            with(processor) {
                execute()
            }
        }
        val inputJob = launch {
            delay(10)
            processor.sendPdu(Pdu(byteArray))
            processor.messageProcessed(message)
        }
        val result = processor.sendMessage(dst, message)
        println("processor sendMessage result: $result")
        result.isRight() shouldBe true
        inputJob.cancel()
        processorJob.cancel()
    }

    "Sending simple ack message" {
        val message = createMockMeshMessage()
        val byteArray = ByteArray(0)
        val dst = 2
        every { pduSender.createPdu(dst, message) } returns Unit.right()
        coEvery { pduSender.sendPdu(Pdu(byteArray)) } returns Unit.right()
        val processorJob = launch {
            with(processor) {
                execute()
            }
        }
        val inputJob = launch {
            delay(10)
            processor.sendPdu(Pdu(byteArray))
            processor.messageProcessed(message)
            val messageReceived = createMockMeshMessage()
            every { messageReceived.opCode } returns 73
            every { messageReceived.src } returns dst
            processor.messageReceived(messageReceived)
        }
        val result = processor.sendAckMessage(dst, message, 73)
        println("processor sendMessage result: $result")
        result.isRight() shouldBe true
        inputJob.cancel()
        processorJob.cancel()
    }

    "simple ack message timeout" {
        val message = createMockMeshMessage()
        val byteArray = ByteArray(0)
        val dst = 2
        every { pduSender.createPdu(dst, message) } returns Unit.right()
        coEvery { pduSender.sendPdu(Pdu(byteArray)) } returns Unit.right()
        val processorJob = launch {
            with(processor) {
                execute()
            }
        }
        val inputJob = launch {
            delay(10)
            processor.sendPdu(Pdu(byteArray))
            processor.messageProcessed(message)
        }
        val result = processor.sendAckMessage(dst, message, 73)
        println("processor sendMessage result: $result")
        result.isLeft() shouldBe true
        inputJob.cancel()
        processorJob.cancel()
    }

    "Should send immediately returns false while same responseOpCode is being processed" {
        val queue = MessageQueue()
        val destination = 0x0001
        val responseOpCode = 0xFF
        val otherResponseOpCode = 0xFE
        val message = createMockMeshMessage(0x01)
        val message2 = createMockMeshMessage(0x02)
        val message3 = createMockMeshMessage(0x03)

        val ackedMessage = BeckonMessage(1, Ack(1, destination, message, mockk(), responseOpCode), mockk())
        val ackedMessage2 = BeckonMessage(2, Ack(2, destination, message2, mockk(), responseOpCode), mockk())
        val ackedMessage3 = BeckonMessage(3, Ack(3, destination, message3, mockk(), otherResponseOpCode), mockk())

        // Fake sending of message
        queue.process(ackedMessage)
        queue.clearProcessing()
        // ackedMessage is now in mesh and we are waiting for response

        // We should not send another message with same responseOpCode
        queue.shouldSendMessageImmediately(ackedMessage2) shouldBe false

        // Sending a message with other responseOpCode is fine
        queue.shouldSendMessageImmediately(ackedMessage3) shouldBe true
    }
})
