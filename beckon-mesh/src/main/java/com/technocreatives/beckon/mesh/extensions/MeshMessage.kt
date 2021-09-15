package com.technocreatives.beckon.mesh.extensions

import com.technocreatives.beckon.mesh.toInt
import no.nordicsemi.android.mesh.transport.AccessMessage
import no.nordicsemi.android.mesh.transport.ConfigCompositionDataStatus
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage

fun MeshMessage.sequenceNumber(): Int? {
    val mess = message
    return (mess as? AccessMessage)?.let {
        mess.sequenceNumber.toInt()
    } ?: run {
        (mess as? ControlMessage)?.sequenceNumber?.toInt()
    }
}

fun MeshMessage.info(): String {
    val mess = message
    val messInfo = (mess as? AccessMessage)?.let {
        mess.info()
    } ?: run {
        (mess as? ControlMessage)?.info()
    } ?: "no mess info"
    return "${javaClass.simpleName}: $messInfo"
}

fun ControlMessage.info(): String =
    "src: $src, dst: $dst, sequenceNumber: ${sequenceNumber.toInt()}, pdu size: ${networkLayerPdu.size()}, segmented: $isSegmented, opcode: $opCode, parameters: $parameters"

fun AccessMessage.info(): String =
    "src: $src, dst: $dst, sequenceNumber: ${sequenceNumber.toInt()}, pdu size: ${networkLayerPdu.size()}, segmented: $isSegmented, opcode: $opCode, parameters: $parameters"
