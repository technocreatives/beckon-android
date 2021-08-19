package com.technocreatives.beckon.mesh.extensions

import com.technocreatives.beckon.mesh.littleEndianConversion
import no.nordicsemi.android.mesh.transport.AccessMessage
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage

fun MeshMessage.sequenceNumber(): String {
    val mess = message
    return (mess as? AccessMessage)?.let {
        "${mess.sequenceNumber.littleEndianConversion()}"
    } ?: run {
        "${(mess as? ControlMessage)?.sequenceNumber?.littleEndianConversion()}"
    }
}
