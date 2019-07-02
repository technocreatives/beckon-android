package com.technocreatives.beckon.internal

import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.ScanFailedException
import com.technocreatives.beckon.ScannerSetting
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import timber.log.Timber

class Scanner {

    private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }
    private val scanSubject by lazy {
        PublishSubject.create<BeckonScanResult>()
    }

    private val callback by lazy {
        object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                Timber.d("onScanFailed $errorCode")
                scanSubject.onError(ScanFailedException(errorCode))
            }

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Timber.d("onScanResult $callbackType $result")
                scanSubject.onNext(BeckonScanResult(result.device, result.rssi))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Timber.d("onBatchScanResults $results")
                results.forEach { result ->
                    scanSubject.onNext(BeckonScanResult(result.device, result.rssi))
                }
            }
        }
    }

    fun scan(setting: ScannerSetting) {
        Timber.d("Bluetooth scanning starting")
        scanner.stopScan(callback)

        val scanFilters = setting.filters.map { it.toScanFilter() }
        scanner.startScan(scanFilters, setting.settings, callback)
    }

    fun stopScan() {
        Timber.d("Bluetooth stop scanning")
        scanner.stopScan(callback)
    }

    fun scan(): Observable<BeckonScanResult> {
        return scanSubject.hide()
    }
}

/*
   fun scanList(setting: ScannerSetting): Observable<List<BeckonScanResult>> {
        return scan(setting)
            .doOnNext { Timber.d("New Device $it") }
            .scan(emptyList(),
                { t1, t2 ->
                    if (t1.any { it.device.address == t2.device.address }) {
                        t1
                    } else {
                        t1.plus(t2)
                    }
                }
            )
    }
 */