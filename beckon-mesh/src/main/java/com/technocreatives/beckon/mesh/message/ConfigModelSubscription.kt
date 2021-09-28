package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.GroupAddress
import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.state.Connected

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

    val configMessage = AddConfigModelSubscription(
        unicast.value,
        message.elementAddress.value,
        message.groupAddress.value,
        message.modelId.value
    )

    return if (subscribe) {
        bearer.sendConfigMessage(configMessage).map { it as ConfigModelSubscriptionResponse }
    } else {
        bearer.sendConfigMessage(!configMessage).map { it as ConfigModelSubscriptionResponse }
    }
}