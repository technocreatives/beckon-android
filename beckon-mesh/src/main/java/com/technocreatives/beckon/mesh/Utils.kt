package com.technocreatives.beckon.mesh

import arrow.core.Either
import arrow.core.left
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScannerSetting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import no.nordicsemi.android.mesh.MeshNetwork
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode
import no.nordicsemi.android.mesh.transport.ControlMessage
import no.nordicsemi.android.mesh.transport.MeshMessage
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode
import no.nordicsemi.android.mesh.utils.MeshParserUtils
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.*
import kotlinx.coroutines.withTimeout as withTimeoutWithException

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

fun ByteArray.toInt(): Int {
    return MeshParserUtils.convert24BitsToInt(this)
}

fun ByteArray.info(): String {
    return map { it.toString() }.foldRight("") { a, b -> "$a $b" }
}

suspend fun withTimeout(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> Either<SendMessageError, MeshMessage>
): Either<SendAckMessageError, MeshMessage> =
    Either.catch { withTimeoutWithException(timeMillis, block) }
        .mapLeft { TimeoutError }
        .fold({ it.left() }, { it })