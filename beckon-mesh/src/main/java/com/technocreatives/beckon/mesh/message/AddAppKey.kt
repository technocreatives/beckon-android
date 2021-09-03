package com.technocreatives.beckon.mesh.message

import arrow.core.Either
import com.technocreatives.beckon.mesh.SendAckMessageError
import com.technocreatives.beckon.mesh.data.*
import com.technocreatives.beckon.mesh.state.Connected
import no.nordicsemi.android.mesh.transport.ConfigAppKeyAdd

data class AddAppKey(
    val netKey: NetKey,
    val appKey: AppKey,
)

suspend fun Connected.addAppKey(
    unicast: UnicastAddress,
    message: AddAppKey
): Either<SendAckMessageError, ConfigMessageStatus> {

    val meshMessage = with(meshApi.meshNetwork()) {
        ConfigAppKeyAdd(
            findNetKey(message.netKey.index)!!,
            findAppKey(message.appKey.index)!!
        )
    }

    return bearer.addConfigAppKey(unicast.value, meshMessage)
        .map { it.transform() }

}