package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.AppKeyIndex
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.GenericOnOffStatus

data class GenericOnOffSet(
    val appKeyIndex: AppKeyIndex,
    val state: Boolean,
    val transactionId: Int,
    val transitionSteps: Int? = null,
    val transitionResolution: Int? = null,
    val delay: Int? = null,
)

suspend fun Connected.sendGenericOnOffSet(): Either<SendAckMessageError, GenericOnOffStatus> =
    TODO()