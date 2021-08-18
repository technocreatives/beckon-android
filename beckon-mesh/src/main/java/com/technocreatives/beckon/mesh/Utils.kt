package com.technocreatives.beckon.mesh

import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScannerSetting
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.*

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


fun scanSetting(serviceUUID: UUID): ScannerSetting {

    val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(0)
        .setUseHardwareFilteringIfSupported(false)
        .build()

    return ScannerSetting(
        settings,
        filters = listOf(
            DeviceFilter(
                serviceUuid = serviceUUID.toString()
            )
        ),
        useFilter = false
    )
}