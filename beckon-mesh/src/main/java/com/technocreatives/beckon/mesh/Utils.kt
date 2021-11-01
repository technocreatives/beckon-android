package com.technocreatives.beckon.mesh

import arrow.core.*
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

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

fun ByteArray.toHex(): String {
    val result = StringBuffer()
    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARS[firstIndex])
        result.append(HEX_CHARS[secondIndex])
    }

    return result.toString()
}

suspend fun <E, T> withTimeout(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> Either<E, T>,
    error: () -> E
): Either<E, T> =
    withTimeoutOrNull(timeMillis, block) ?: error().left()