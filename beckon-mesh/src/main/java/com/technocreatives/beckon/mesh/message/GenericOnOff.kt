package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.AppKeyIndex
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.data.findAppKey
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.opcodes.ApplicationMessageOpCodes
import no.nordicsemi.android.mesh.transport.GenericOnOffSet as NrfGenericOnOffSet
import no.nordicsemi.android.mesh.transport.GenericOnOffStatus

data class GenericOnOffSet(
    val appKeyIndex: AppKeyIndex,
    val state: Boolean,
    val transactionId: Int,
    val transitionSteps: Int? = null,
    val transitionResolution: Int? = null,
    val delay: Int? = null,
)

suspend fun Connected.sendGenericOnOffSet(
    elementAddress: UnicastAddress,
    message: GenericOnOffSet
): Either<SendAckMessageError, GenericOnOffStatus> {
    val meshMessage = with(meshApi.meshNetwork()) {
        NrfGenericOnOffSet(
            findAppKey(message.appKeyIndex)!!,
            message.state,
            message.transactionId,
            message.transitionSteps,
            message.transitionResolution,
            message.delay
        )
    }
    return bearer.sendAckMessage(
        elementAddress.value,
        meshMessage,
        ApplicationMessageOpCodes.GENERIC_ON_OFF_STATUS
    )
        .map { it as GenericOnOffStatus }
}