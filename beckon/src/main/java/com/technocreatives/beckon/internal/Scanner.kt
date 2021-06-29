package com.technocreatives.beckon.internal

import android.os.ParcelUuid
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import timber.log.Timber
import java.util.UUID
import no.nordicsemi.android.support.v18.scanner.ScanResult as BleScanResult

interface Scanner {
    suspend fun startScan(setting: ScannerSetting): Flow<Either<ScanError, ScanResult>>
    suspend fun stopScan()
}

internal class ScannerImpl : Scanner {
    private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }

    private var callback: ScanCallback? = null
    private var scanSubject: MutableSharedFlow<Either<ScanError, ScanResult>>? = null
    override suspend fun startScan(setting: ScannerSetting): Flow<Either<ScanError, ScanResult>> {
        stopScan()
        Timber.d("Scanner start scanning with setting $setting")
        scanSubject = MutableSharedFlow()
        callback = object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                Timber.w("onScanFailed $errorCode")
                // scanSubject?.onNext(ScanError.ScanFailed(errorCode).left())
                runBlocking {
                    scanSubject?.emit(ScanError.ScanFailed(errorCode).left())
                }
            }

            override fun onScanResult(callbackType: Int, result: BleScanResult) {
                Timber.d("onScanResult $callbackType $result")
                runBlocking {
                    scanSubject?.emit(ScanResult(result.device, result.rssi).right())
                }
            }

            override fun onBatchScanResults(results: MutableList<BleScanResult>) {
                Timber.d("onBatchScanResults $results")
                results.forEach { result ->
                    runBlocking {
                        scanSubject?.let {
                            it.emit(ScanResult(result.device, result.rssi).right())
                        }
                    }
                }
            }
        }

        val scanFilters = setting.filters.map { it.toScanFilter() }
        scanner.startScan(scanFilters, setting.settings, callback!!)

        return scanSubject!!.asSharedFlow()
    }

    override suspend fun stopScan() {
        scanSubject?.let {
            scanSubject = null
        }
        callback?.let {
            Timber.d("Scanner stop scanning")
            scanner.stopScan(it)
            callback = null
        }
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
