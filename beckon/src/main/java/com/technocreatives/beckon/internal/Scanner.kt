package com.technocreatives.beckon.internal

import com.technocreatives.beckon.ScanError
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import timber.log.Timber
import no.nordicsemi.android.support.v18.scanner.ScanResult as BleScanResult

interface Scanner {
    fun startScan(setting: ScannerSetting): Flow<ScanResult>
    fun stopScan()
}
//
// internal class ScannerImpl : Scanner {
//
//     private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }
//
//     private var callback: ScanCallback? = null
//     private var scanSubject: MutableSharedFlow<ScanResult>? = null
//     override fun startScan(setting: ScannerSetting): Flow<ScanResult> {
//         stopScan()
//         Timber.d("Scanner start scanning with setting $setting")
//         scanSubject = MutableSharedFlow(1)
//         callback = object : ScanCallback() {
//             override fun onScanFailed(errorCode: Int) {
//                 Timber.w("onScanFailed $errorCode")
//                 scanSubject
//                 scanSubject?.onError(ScanError.ScanFailed(errorCode).toException())
//             }
//
//             override fun onScanResult(callbackType: Int, result: BleScanResult) {
//                 // Timber.d("onScanResult $callbackType $result")
//                 scanSubject?.let {
//                     if (!it.hasComplete()) {
//                         it.onNext(ScanResult(result.device, result.rssi))
//                     }
//                 }
//             }
//
//             override fun onBatchScanResults(results: MutableList<BleScanResult>) {
//                 Timber.d("onBatchScanResults $results")
//                 results.forEach { result ->
//                     scanSubject?.let {
//                         if (!it.hasComplete()) {
//                             it.onNext(ScanResult(result.device, result.rssi))
//                         }
//                     }
//                 }
//             }
//         }
//
//         val scanFilters = setting.filters.map { it.toScanFilter() }
//         scanner.startScan(scanFilters, setting.settings, callback!!)
//
//         return scanSubject!!.hide()
//     }
//
//     override fun stopScan() {
//         scanSubject?.let {
//             it.onComplete()
//             scanSubject = null
//         }
//         callback?.let {
//             Timber.d("Scanner stop scanning")
//             scanner.stopScan(it)
//             callback = null
//         }
//     }
// }