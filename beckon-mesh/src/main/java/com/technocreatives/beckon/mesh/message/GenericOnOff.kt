package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.AppKeyIndex
import com.technocreatives.beckon.mesh.data.PublishableAddress
import com.technocreatives.beckon.mesh.data.findAppKey
import com.technocreatives.beckon.mesh.data.value
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.opcodes.ApplicationMessageOpCodes
import no.nordicsemi.android.mesh.transport.GenericOnOffSetUnacknowledged
import no.nordicsemi.android.mesh.transport.GenericOnOffSet as NrfGenericOnOffSet
import no.nordicsemi.android.mesh.transport.GenericOnOffStatus as NrfGenericOnOffStatus

data class GenericOnOffSet(
    val appKeyIndex: AppKeyIndex,
    val state: Boolean,
    val transactionId: Int,
    val transitionSteps: Int? = null,
    val transitionResolution: Int? = null,
    val delay: Int? = null,
)

data class GenericOnOffStatus(
    val presentState: Boolean,
    val targetState: Boolean?,
    val transitionResolution: Int?,
    val transitionSteps: Int?,
)

suspend fun Connected.sendGenericOnOffSetAck(
    publishableAddress: PublishableAddress,
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
        publishableAddress.value(),
        meshMessage,
        ApplicationMessageOpCodes.GENERIC_ON_OFF_STATUS
    )
        .map { it as NrfGenericOnOffStatus }
        .map { it.transform() }
}

suspend fun Connected.sendGenericOnOffSet(
    publishableAddress: PublishableAddress,
    message: GenericOnOffSet
): Either<SendAckMessageError, Unit> {
    val meshMessage = with(meshApi.meshNetwork()) {
        GenericOnOffSetUnacknowledged(
            findAppKey(message.appKeyIndex)!!,
            message.state,
            message.transactionId,
            message.transitionSteps,
            message.transitionResolution,
            message.delay
        )
    }
    return bearer.sendMessage(
        publishableAddress.value(),
        meshMessage
    )
}

private fun NrfGenericOnOffStatus.transform() =
    GenericOnOffStatus(
        presentState,
        targetState,
        transitionResolution,
        transitionSteps
    )