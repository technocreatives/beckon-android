package com.technocreatives.beckon.extensions

import arrow.core.Either
import arrow.fx.coroutines.Resource
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.util.scanZ
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.CoroutineContext

/**
 * startScan
 * Stop scan when error happen
 */
suspend fun BeckonClient.scan(
    setting: ScannerSetting,
    builder: suspend (accumulator: List<ScanResult>, value: ScanResult) -> List<ScanResult>
): Flow<Either<ScanError, List<ScanResult>>> {
    return Resource({ startScan(setting) }, { f, e -> this.stopScan() })
        .use {
            it.scanZ(emptyList(), builder)
                .onEach {
                    if (it.isLeft()) {
                        stopScan()
                    }
                }
        }
//    return startScan(setting)
//        .scanZ(emptyList(), builder)
//        .onEach {
//            if (it.isLeft()) {
//                stopScan()
//            }
//        }
}

suspend fun BeckonClient.scan(setting: ScannerSetting): Flow<Either<ScanError, List<ScanResult>>> {
    return startScan(setting).scanZ(emptyList(), ::buildScanResult)
}

fun buildScanResult(list: List<ScanResult>, result: ScanResult): List<ScanResult> {
    return list.filter { it.macAddress == result.macAddress } + result
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
