package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ConfigModelPublicationGet
import no.nordicsemi.android.mesh.transport.ConfigModelPublicationSet

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
): Either<SendAckMessageError, ConfigMessageStatus> {
    val meshMessage = ConfigModelPublicationSet(
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

    return bearer.setConfigModelPublication(nodeAddress.value, meshMessage)
        .map { it.transform() }
}

suspend fun Connected.removeConfigModelPublication(
    nodeAddress: UnicastAddress,
    elementAddress: UnicastAddress,
    modelId: ModelId
): Either<SendAckMessageError, ConfigMessageStatus> {

    val meshMessage = ConfigModelPublicationSet(
        elementAddress.value,
        modelId.value,
    )

    return bearer.setConfigModelPublication(nodeAddress.value, meshMessage)
        .map { it.transform() }
}

suspend fun Connected.getConfigModelPublication(
    unicast: UnicastAddress,
    elementAddress: UnicastAddress,
    publishAddress: UnicastAddress,
): Either<SendAckMessageError, ConfigMessageStatus> {

    val meshMessage = ConfigModelPublicationGet(
        elementAddress.value,
        publishAddress.value,
    )

    return bearer.getConfigModelPublication(unicast.value, meshMessage)
        .map { it.transform() }
}