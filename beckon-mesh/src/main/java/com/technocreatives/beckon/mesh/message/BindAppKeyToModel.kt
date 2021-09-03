package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.AppKeyIndex
import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ConfigModelAppBind
import no.nordicsemi.android.mesh.transport.ConfigModelAppUnbind

data class BindAppKeyToModel(
    val elementAddress: UnicastAddress,
    val modelId: ModelId,
    val appKeyIndex: AppKeyIndex,
)

suspend fun Connected.bindAppKeyToModel(
    unicast: UnicastAddress,
    message: BindAppKeyToModel
): Either<SendAckMessageError, ConfigMessageStatus> {

    val meshMessage = ConfigModelAppBind(
        message.elementAddress.value,
        message.modelId.value,
        message.appKeyIndex.value
    )

    return bearer.bindConfigModelApp(unicast.value, meshMessage)
        .map { it.transform() }

}

suspend fun Connected.unbindAppKeyToModel(
    unicast: UnicastAddress,
    message: BindAppKeyToModel
): Either<SendAckMessageError, ConfigMessageStatus> {

    val meshMessage = ConfigModelAppUnbind(
        message.elementAddress.value,
        message.modelId.value,
        message.appKeyIndex.value
    )

    return bearer.unbindConfigModelApp(unicast.value, meshMessage)
        .map { it.transform() }

}