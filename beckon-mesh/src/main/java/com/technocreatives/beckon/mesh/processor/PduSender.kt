package com.technocreatives.beckon.mesh.processor

import arrow.core.Either
import com.technocreatives.beckon.BeckonActionError
import com.technocreatives.beckon.mesh.SendMessageError
import no.nordicsemi.android.mesh.transport.MeshMessage as NrfMeshMessage

interface PduSender {
    fun createPdu(dst: Int, meshMessage: NrfMeshMessage): Either<SendMessageError, Unit>
    suspend fun sendPdu(pdu: Pdu): Either<BeckonActionError, Unit>
}

@JvmInline
value class Pdu(val data: ByteArray)