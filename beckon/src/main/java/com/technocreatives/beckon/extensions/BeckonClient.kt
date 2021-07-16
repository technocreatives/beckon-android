package com.technocreatives.beckon.extensions

import arrow.core.Either
import arrow.fx.coroutines.Resource
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.util.scanZ
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * startScan
 * Stop scan when error happen or when job is completed
 */
suspend fun BeckonClient.scan(
    setting: ScannerSetting,
    builder: suspend (accumulator: List<ScanResult>, value: ScanResult) -> List<ScanResult>
): Flow<Either<ScanError, List<ScanResult>>> {
    coroutineContext[Job]?.invokeOnCompletion {
        runBlocking {
            stopScan()
        }
    }
    return startScan(setting)
        .scanZ(emptyList(), builder)
        .onEach {
            if (it.isLeft()) {
                stopScan()
            }
        }
}

suspend fun BeckonClient.scan(setting: ScannerSetting): Flow<Either<ScanError, List<ScanResult>>> {
    return startScan(setting).scanZ(emptyList(), ::buildScanResult)
}

fun buildScanResult(list: List<ScanResult>, result: ScanResult): List<ScanResult> {
    return list.filter { it.macAddress != result.macAddress } + result
}
suspend fun CoroutineContext.scan(
    client: BeckonClient,
    setting: ScannerSetting,
    builder: suspend (accumulator: List<ScanResult>, value: ScanResult) -> List<ScanResult>
): Flow<Either<ScanError, List<ScanResult>>> {
    this.cancel()
    return client.startScan(setting).scanZ(emptyList(), builder)
        .onEach {
            if (it.isLeft()) {
                client.stopScan()
                this.cancel()
            }
        }
}
