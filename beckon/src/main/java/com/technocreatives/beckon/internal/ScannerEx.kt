package com.technocreatives.beckon.internal

import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScanFailureException
import io.reactivex.Observable
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber

internal fun BluetoothLeScannerCompat.scan(setting: ScanSettings, filters: List<DeviceFilter>): Observable<BeckonScanResult> {

    return Observable.create<BeckonScanResult> { observer ->
        val callback: ScanCallback = object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                Timber.d("onScanFailed $errorCode")
                observer.onError(ScanFailureException(errorCode))
            }

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Timber.d("onScanResult $callbackType $result")
                observer.onNext(BeckonScanResult(result.device, result.rssi))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Timber.d("onBatchScanResults $results")
                results.forEach { result ->
                    observer.onNext(BeckonScanResult(result.device, result.rssi))
                }
            }
        }

        val scanFilters = filters.map { it.toScanFilter() }

        Timber.d("Bluetooth scanning starting")
        this.startScan(scanFilters, setting, callback)
        observer.setCancellable {
            this.stopScan(callback)
            Timber.d("Bluetooth scanning stopped")
        }
    }
}

internal fun BluetoothLeScannerCompat.scanList(setting: ScanSettings, filters: List<DeviceFilter>): Observable<List<BeckonScanResult>> {
    // need to filter connected devices or saved devices
    return this.scan(setting, filters)
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

fun DeviceFilter.toScanFilter(): ScanFilter {
    return ScanFilter.Builder()
            .setDeviceName(deviceName)
            .build()
}
