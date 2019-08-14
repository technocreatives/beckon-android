package com.technocreatives.beckon.internal

import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import timber.log.Timber
import no.nordicsemi.android.support.v18.scanner.ScanResult as BleScanResult

interface Scanner {
    fun startScan(setting: ScannerSetting): Observable<ScanResult>
    fun stopScan()
    // fun results(): Observable<ScanResult>
}

internal class ScannerImpl : Scanner {

    private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }

    private var callback: ScanCallback? = null
    private var scanSubject: PublishSubject<ScanResult>? = null
    override fun startScan(setting: ScannerSetting): Observable<ScanResult> {
        stopScan()
        Timber.d("Bluetooth start scanning ")
        scanSubject = PublishSubject.create()
        callback = object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                Timber.d("onScanFailed $errorCode")
                scanSubject?.onError(ScanError.ScanFailed(errorCode).toException())
            }

            override fun onScanResult(callbackType: Int, result: BleScanResult) {
                Timber.d("onScanResult $callbackType $result")
                scanSubject?.let {
                    if (!it.hasComplete()) {
                        it.onNext(ScanResult(result.device, result.rssi))
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<BleScanResult>) {
                Timber.d("onBatchScanResults $results")
                results.forEach { result ->
                    scanSubject?.let {
                        if (!it.hasComplete()) {
                            it.onNext(ScanResult(result.device, result.rssi))
                        }
                    }
                }
            }
        }

        val scanFilters = setting.filters.map { it.toScanFilter() }
        scanner.startScan(scanFilters, setting.settings, callback!!)

        return scanSubject!!.hide()
    }

    override fun stopScan() {
        scanSubject?.let {
            it.onComplete()
            scanSubject = null
        }
        callback?.let {
            Timber.d("Bluetooth stop scanning")
            scanner.stopScan(it)
            callback = null
        }
    }
}
