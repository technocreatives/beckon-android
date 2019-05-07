package com.technocreatives.beckon.internal

import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.ScannerSetting
import io.reactivex.Observable
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import timber.log.Timber

class Scanner {

    private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }

    fun scan(setting: ScannerSetting): Observable<BeckonScanResult> {
        return scanner.scan(setting.settings, setting.filters)
                .filter { it.filter(setting.filters) }
    }

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
}
