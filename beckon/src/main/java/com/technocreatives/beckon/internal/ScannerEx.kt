package com.technocreatives.beckon.internal

import android.os.ParcelUuid
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScanFailedException
import io.reactivex.Observable
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber
import java.util.UUID

internal fun BluetoothLeScannerCompat.startScan(setting: ScanSettings, filters: List<DeviceFilter>): Observable<com.technocreatives.beckon.ScanResult> {

    return Observable.create { observer ->
        val callback: ScanCallback = object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                Timber.d("onScanFailed $errorCode")
                observer.onError(ScanFailedException(errorCode))
            }

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Timber.d("onScanResult $callbackType $result")
                observer.onNext(com.technocreatives.beckon.ScanResult(result.device, result.rssi))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Timber.d("onBatchScanResults $results")
                results.forEach { result ->
                    observer.onNext(com.technocreatives.beckon.ScanResult(result.device, result.rssi))
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

internal fun BluetoothLeScannerCompat.scanList(setting: ScanSettings, filters: List<DeviceFilter>): Observable<List<com.technocreatives.beckon.ScanResult>> {
    // need to filter connected devices or saved devices
    return this.startScan(setting, filters)
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
    val serviceUuid = serviceUuid?.toUuid()?.let { ParcelUuid(it) }
    return ScanFilter.Builder()
            .setDeviceName(deviceName)
            // .setServiceUuid(serviceUuid)
            .build()
}
fun String.toUuid(): UUID = UUID.fromString(this)
