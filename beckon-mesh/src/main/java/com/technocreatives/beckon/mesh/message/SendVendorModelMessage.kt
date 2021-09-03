package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.AppKeyIndex
import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.data.findAppKey
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.VendorModelMessageUnacked

data class SendVendorModelMessage(
    val appKeyIndex: AppKeyIndex,
    val modelId: ModelId,
    val companyIdentifier: Int,
    val opCode: Int,
    val parameters: ByteArray?,
)

suspend fun Connected.sendVendorModelMessage(
    unicast: UnicastAddress,
    message: SendVendorModelMessage
): Either<SendAckMessageError, Unit> {

    val meshMessage = VendorModelMessageUnacked(
        meshApi.meshNetwork().findAppKey(message.appKeyIndex)!!,
        message.modelId.value,
        message.companyIdentifier,
        message.opCode,
        message.parameters
    )

    return bearer.sendVendorModelMessage(unicast.value, meshMessage)

}