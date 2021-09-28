package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ConfigModelSubscriptionDelete

data class ConfigModelSubscription(
    val elementAddress: UnicastAddress,
    val groupAddress: GroupAddress,
    val modelId: ModelId,
)

suspend fun Connected.configSubscribeModelToGroup(
    unicast: UnicastAddress,
    message: ConfigModelSubscription,
    subscribe: Boolean
): Either<SendAckMessageError, ConfigModelSubscriptionResponse> {

    val configMessage = if(subscribe) {
        AddConfigModelSubscription(
            unicast.value,
            message.elementAddress.value,
            message.groupAddress.value,
            message.modelId.value)
    } else {
        RemoveConfigModelSubscription(
            unicast.value,
            message.elementAddress.value,
            message.groupAddress.value,
            message.modelId.value
        )
    }

    return bearer.sendConfigMessage(configMessage).map { it as ConfigModelSubscriptionResponse }
}