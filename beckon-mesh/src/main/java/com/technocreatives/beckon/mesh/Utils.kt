package com.technocreatives.beckon.mesh

import arrow.core.*
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.BeckonTimeOutError
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
import kotlin.collections.foldRight

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

suspend fun <E, T> withTimeout(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> Either<E, T>,
    error: () -> E
): Either<E, T> =
    withTimeoutOrNull(timeMillis, block) ?: error().left()

suspend fun <T> beckonTimeout(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> Either<BeckonError, T>
): Either<BeckonError, T> =
    withTimeoutOrNull(timeMillis, block) ?: BeckonTimeOutError.left()