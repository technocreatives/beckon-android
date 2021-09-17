package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
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
    opCode: Int
): Either<SendAckMessageError, VendorModelMessageStatus> =
    sendVendorModelMessageAck(nodeAddress.value(), message, opCode)

private suspend fun Connected.sendVendorModelMessageAck(
    address: Int,
    message: SendVendorModelMessage,
    opCode: Int
): Either<SendAckMessageError, VendorModelMessageStatus> {

    val meshMessage = VendorModelMessageAcked(
        meshApi.meshNetwork().findAppKey(message.appKeyIndex)!!,
        message.modelId.value,
        message.companyIdentifier,
        message.opCode,
        message.parameters!!
    )

    return bearer.sendVendorModelMessageAck(address, meshMessage, opCode)
}
