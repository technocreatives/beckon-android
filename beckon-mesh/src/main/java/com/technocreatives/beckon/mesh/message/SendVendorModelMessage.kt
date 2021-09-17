package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
import com.technocreatives.beckon.mesh.toInt
import no.nordicsemi.android.mesh.transport.VendorModelMessageAcked
import no.nordicsemi.android.mesh.transport.VendorModelMessageStatus
import no.nordicsemi.android.mesh.transport.VendorModelMessageUnacked

data class SendVendorModelMessage(
    val appKeyIndex: AppKeyIndex,
    val modelId: ModelId,
    val companyIdentifier: Int,
    val opCode: Int,
    val parameters: ByteArray?,
)

suspend fun Connected.sendVendorModelMessage(
    nodeAddress: PublishableAddress,
    message: SendVendorModelMessage
): Either<SendAckMessageError, Unit> =
    sendVendorModelMessage(nodeAddress.value(), message)

private suspend fun Connected.sendVendorModelMessage(
    address: Int,
    message: SendVendorModelMessage
): Either<SendAckMessageError, Unit> {

    val meshMessage = VendorModelMessageUnacked(
        meshApi.meshNetwork().findAppKey(message.appKeyIndex)!!,
        message.modelId.value,
        message.companyIdentifier,
        message.opCode,
        message.parameters
    )

    return bearer.sendVendorModelMessage(address, meshMessage)
}

suspend fun Connected.sendVendorModelMessageAck(
    nodeAddress: PublishableAddress,
    message: SendVendorModelMessage,
    responseOpCode: Int
): Either<SendAckMessageError, VendorModelMessageStatus> =
    sendVendorModelMessageAck(nodeAddress.value(), message, responseOpCode)

private suspend fun Connected.sendVendorModelMessageAck(
    address: Int,
    message: SendVendorModelMessage,
    responseOpCode: Int
): Either<SendAckMessageError, VendorModelMessageStatus> {

    val meshMessage = VendorModelMessageAcked(
        meshApi.meshNetwork().findAppKey(message.appKeyIndex)!!,
        message.modelId.value,
        message.companyIdentifier,
        message.opCode,
        message.parameters!!
    )

    return bearer.sendVendorModelMessageAck(address, meshMessage, fullOpCode(message.companyIdentifier, responseOpCode))
}

fun fullOpCode(companyIdentifier: Int, opCode: Int): Int {
    val byte2 = companyIdentifier.toByte()
    val byte1 = (companyIdentifier shr 8).toByte()
    val byte0 = opCode.toByte()
    return listOf(byte0, byte2, byte1).toByteArray().toInt()
}