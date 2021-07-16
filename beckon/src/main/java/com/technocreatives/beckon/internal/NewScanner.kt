package com.technocreatives.beckon.internal

import android.os.ParcelUuid
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.Resource
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import timber.log.Timber
import java.util.*

interface ExScanner {
    suspend fun startScan(setting: ScannerSetting): Either<ScanError, Unit>
    suspend fun stopScan()
    suspend fun results(): Flow<ScanResult>

    companion object {
        fun instance(): ExScanner = TODO()
    }
}

class ExScannerImpl {

    suspend fun scan(setting: ScannerSetting): Either<ScanError, Flow<ScanResult>> = either {
        val scanner = ExScanner.instance()
        scanner.startScan(setting).bind()
        val resource = Resource({ scanner }, ExScanner::stopScan)
        resource.use {
            it.results()
        }
    }
}

internal class NewScannerImpl : ExScanner {

    private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }

    private var callback: ScanCallback? = null
    private val scanSubject by lazy { MutableSharedFlow<ScanResult>() }

    override suspend fun startScan(setting: ScannerSetting): Either<ScanError, Unit> {
        Timber.d("Scanner start scanning with setting $setting")

        val result = CompletableDeferred<Either<ScanError, Unit>>()

        callback = object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                Timber.w("onScanFailed $errorCode")
                result.complete(ScanError.ScanFailed(errorCode).left())
            }

            override fun onScanResult(
                callbackType: Int,
                result: no.nordicsemi.android.support.v18.scanner.ScanResult
            ) {
                Timber.d("onScanResult $callbackType $result")
                runBlocking {
                    scanSubject.emit(
                        ScanResult(
                            result.device,
                            result.rssi,
                            result.scanRecord
                        )
                    )
                }
            }

            override fun onBatchScanResults(results: MutableList<no.nordicsemi.android.support.v18.scanner.ScanResult>) {
                Timber.d("onBatchScanResults $results")
                results.forEach { result ->
                    runBlocking {
                            scanSubject.emit(
                                ScanResult(
                                    result.device,
                                    result.rssi,
                                    result.scanRecord
                                )
                            )
                    }
                }
            }
        }

        val scanFilters = setting.filters.map { it.toScanFilter() }
        scanner.startScan(scanFilters, setting.settings, callback!!)
        coroutineScope {
            launch {
                delay(100)
                result.complete(Unit.right())
            }
        }
        return result.await()
    }

    override suspend fun stopScan() {
        callback?.let {
            Timber.d("Scanner stop scanning")
            scanner.stopScan(it)
            callback = null
        }
    }

    override suspend fun results(): Flow<ScanResult> {
        TODO("Not yet implemented")
    }
}

