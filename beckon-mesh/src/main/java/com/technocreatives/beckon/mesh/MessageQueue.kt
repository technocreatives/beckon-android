package com.technocreatives.beckon.mesh

import arrow.core.Either
import no.nordicsemi.android.mesh.transport.ConfigModelSubscriptionAdd
import no.nordicsemi.android.mesh.transport.ConfigVendorModelSubscriptionList
import no.nordicsemi.android.mesh.transport.MeshMessage

sealed class BeckonMessage {
    abstract fun meshMessage(): MeshMessage
    class ConfigModelAppBind() : BeckonMessage() {
        override fun meshMessage(): MeshMessage {
            TODO("Not yet implemented")
        }
    }
}

interface MessageQueue {
    val messages: List<MeshMessage>
    fun sendMessage(message: MeshMessage): Either<SendMeshMessageError, Unit>
    fun onReceiveStatus(message: MeshMessage) {
       if(message is ConfigVendorModelSubscriptionList) {
            messages.filterIsInstance<ConfigModelSubscriptionAdd>()
                .forEach {
                }
       }
    }

}