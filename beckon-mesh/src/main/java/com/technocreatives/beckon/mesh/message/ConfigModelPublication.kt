package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ConfigModelPublicationStatus

// TODO we have different message for virtual address:
// ConfigModelPublicationVirtualAddressSet
data class ConfigModelPublication(
    val publishAddress: PublishableAddress, // TODO Can also be unicast or virtual address?
    val elementAddress: UnicastAddress,
    val appKeyIndex: AppKeyIndex,
    val credentialFlag: Boolean,
    val publishTtl: Int,
    val publicationSteps: Int,
    val publicationResolution: Int,
    val retransmitCount: Int,
    val retransmitIntervalSteps: Int,
    val modelId: ModelId
)


suspend fun Connected.setConfigModelPublication(
    nodeAddress: UnicastAddress,
    message: ConfigModelPublication
): Either<SendAckMessageError, ConfigModelPublicationStatus> {
    val configMessage = SetConfigModelPublication(
        nodeAddress.value,
        message.elementAddress.value,
        message.publishAddress.value(),
        message.appKeyIndex.value,
        message.credentialFlag,
        message.publishTtl,
        message.publicationSteps,
        message.publicationResolution,
        message.retransmitCount,
        message.retransmitIntervalSteps,
        message.modelId.value
    )

    return bearer.sendConfigMessage(
        configMessage
    ).map {
        it as ConfigModelPublicationStatus
    }
}

suspend fun Connected.clearConfigModelPublication(
    nodeAddress: UnicastAddress,
    elementAddress: UnicastAddress,
    modelId: ModelId
): Either<SendAckMessageError, ConfigModelPublicationStatus> {

    val configMessage = ClearConfigModelPublication(
        nodeAddress.value,
        elementAddress.value,
        modelId.value,
    )

    return bearer.sendConfigMessage(
        configMessage
    ).map {
        it as ConfigModelPublicationStatus
    }
}

suspend fun Connected.getConfigModelPublication(
    nodeAddress: UnicastAddress,
    elementAddress: UnicastAddress,
    modelId: ModelId,
): Either<SendAckMessageError, ConfigModelPublicationStatus> {

    val configMessage = GetConfigModelPublication(
        nodeAddress.value,
        elementAddress.value,
        modelId.value
    )

    return bearer.sendConfigMessage(
        configMessage
    ).map {
        it as ConfigModelPublicationStatus
    }
}