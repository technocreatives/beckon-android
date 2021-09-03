package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ConfigModelSubscriptionAdd
import no.nordicsemi.android.mesh.transport.ConfigModelSubscriptionDelete

data class ConfigModelSubscription(
    val elementAddress: UnicastAddress,
    val groupAddress: GroupAddress,
    val modelId: ModelId,
)

suspend fun Connected.subscribeModelToGroup(
    unicast: UnicastAddress,
    message: ConfigModelSubscription
): Either<SendAckMessageError, ConfigMessageStatus> {

    val meshMessage = ConfigModelSubscriptionAdd(
        message.elementAddress.value,
        message.groupAddress.value,
        message.modelId.value
    )

    return bearer.addConfigModelSubscription(unicast.value, meshMessage)
        .map { it.transform() }

}

suspend fun Connected.unsubscribeModelToGroup(
    unicast: UnicastAddress,
    message: ConfigModelSubscription
): Either<SendAckMessageError, ConfigMessageStatus> {

    val meshMessage = ConfigModelSubscriptionDelete(
        message.elementAddress.value,
        message.groupAddress.value,
        message.modelId.value
    )

    return bearer.deleteConfigModelSubscription(unicast.value, meshMessage)
        .map { it.transform() }

}