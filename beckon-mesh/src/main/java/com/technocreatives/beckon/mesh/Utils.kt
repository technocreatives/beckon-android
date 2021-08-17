package com.technocreatives.beckon.mesh

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode

fun <T> MutableSharedFlow<T>.blockingEmit(value: T) {
    runBlocking {
        emit(value)
    }
}

fun UnprovisionedMeshNode.debug(): String =
   "name: $nodeName, uuid: $deviceUuid"

fun ProvisionedMeshNode.debug(): String =
    "name: $nodeName, uuid: $uuid"

fun MeshNetwork.debug(): String =
    "name: $meshName, uuid: $meshUUID"

fun ByteArray.debug(): String =
    "size: $size, data: ${toList()}"

fun ControlMessage.debug(): String =
    "ControlMessage ctl: $ctl"

fun MeshMessage.debug(): String =
    "MeshMessage opCode: $opCode"