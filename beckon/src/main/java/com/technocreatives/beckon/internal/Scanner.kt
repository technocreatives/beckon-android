package com.technocreatives.beckon.internal

import android.os.ParcelUuid
import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.*
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import timber.log.Timber
import java.util.*
import no.nordicsemi.android.support.v18.scanner.ScanResult as BleScanResult

interface Scanner {
    suspend fun startScan(setting: ScannerSetting): Flow<Either<ScanError, ScanResult>>
    suspend fun stopScan()
}

internal class ScannerImpl : Scanner {
    private val scanner = BluetoothLeScannerCompat.getScanner()

    private var callback: ScanCallback? = null

    override suspend fun startScan(setting: ScannerSetting): Flow<Either<ScanError, ScanResult>> {
        stopScan()
        Timber.d("Scanner start scanning with setting $setting")
//        scanSubject = MutableSharedFlow()
        return callbackFlow {

            callback = object : ScanCallback() {
                override fun onScanFailed(errorCode: Int) {
                    Timber.w("onScanFailed $errorCode")
                    val result = trySend(ScanError.ScanFailed(errorCode).left())
                    Timber.w("trySend onScanFailed $result")
                }

                override fun onScanResult(callbackType: Int, result: BleScanResult) {
                    Timber.d("onScanResult $callbackType $result")
                    val result = trySend(
                        ScanResult(
                            result.device,
                            result.rssi,
                            result.scanRecord
                        ).right()
                    )
                    Timber.w("trySend onScanResult $result")
                }

                override fun onBatchScanResults(results: MutableList<BleScanResult>) {
                    Timber.d("onBatchScanResults $results")
                    results.forEach { result ->
                        val result = trySend(
                            ScanResult(
                                result.device,
                                result.rssi,
                                result.scanRecord
                            ).right()

                        )
                        Timber.w("trySend onBatchScanResults $result")
                    }
                }
            }

            val scanFilters = setting.filters.map { it.toScanFilter() }
            scanner.startScan(scanFilters, setting.settings, callback!!)

            awaitClose {
                callback?.let {
                    scanner.stopScan(it)
                }
            }
        }
    }

    override suspend fun stopScan() {
        Timber.d("ScannerImpl stopScan")

        callback?.let {
            Timber.d("Scanner stop scanning cleaning callback")
            scanner.stopScan(it)
        }
        callback = null
    }

}

fun DeviceFilter.toScanFilter(): ScanFilter {
    val serviceUuid = serviceUuid?.toUuid()?.let { ParcelUuid(it) }
    return ScanFilter.Builder()
        .setDeviceName(name)
        .setDeviceAddress(address)
        .setServiceUuid(serviceUuid)
        .build()
}

fun String.toUuid(): UUID = UUID.fromString(this)
