package com.technocreatives.beckon.internal

import android.os.ParcelUuid
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.util.UUID
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult as BleScanResult
import timber.log.Timber

interface Scanner {
    fun startScan(setting: ScannerSetting): Observable<ScanResult>
    fun stopScan()
}

internal class ScannerImpl : Scanner {

    private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }

    private var callback: ScanCallback? = null
    private var scanSubject: PublishSubject<ScanResult>? = null
    override fun startScan(setting: ScannerSetting): Observable<ScanResult> {
        stopScan()
        Timber.d("Scanner start scanning with setting $setting")
        scanSubject = PublishSubject.create()
        callback = object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                Timber.w("onScanFailed $errorCode")
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
