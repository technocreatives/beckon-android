package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.ModelId
import com.technocreatives.beckon.mesh.data.Publish
import com.technocreatives.beckon.mesh.data.PublishableAddress
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.state.Connected

// TODO we have different message for virtual address:
// ConfigModelPublicationVirtualAddressSet
data class ConfigModelPublication(
    val elementAddress: UnicastAddress,
    val publish: Publish,
    val modelId: ModelId
)


suspend fun Connected.setConfigModelPublication(
    nodeAddress: UnicastAddress,
    message: ConfigModelPublication
): Either<SendAckMessageError, ConfigModelPublicationResponse> {
    val configMessage = SetConfigModelPublication(
        nodeAddress.value,
        message.elementAddress,
        message.publish,
        message.modelId
    )

    return bearer.sendConfigMessage(
        configMessage
    )
}

suspend fun Connected.clearConfigModelPublication(
    nodeAddress: UnicastAddress,
    elementAddress: UnicastAddress,
    modelId: ModelId
): Either<SendAckMessageError, ConfigModelPublicationResponse> {

    val configMessage = ClearConfigModelPublication(
        nodeAddress.value,
        elementAddress,
        modelId,
    )

    return bearer.sendConfigMessage(
        configMessage
    )
}

suspend fun Connected.getConfigModelPublication(
    nodeAddress: UnicastAddress,
    elementAddress: UnicastAddress,
    modelId: ModelId,
): Either<SendAckMessageError, ConfigModelPublicationResponse> {

    val configMessage = GetConfigModelPublication(
        nodeAddress.value,
        elementAddress,
        modelId
    )

    return bearer.sendConfigMessage(
        configMessage
    )
}