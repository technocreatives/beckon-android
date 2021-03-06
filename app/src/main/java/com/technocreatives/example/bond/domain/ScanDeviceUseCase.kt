package com.technocreatives.example.bond.domain

import arrow.core.Either
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.rx2.BeckonClientRx
import com.technocreatives.beckon.rx2.scanAndSave
import io.reactivex.Observable
import timber.log.Timber

class ScanDeviceUseCase(
    private val beckonClient: com.technocreatives.beckon.rx2.BeckonClientRx,
    private val scanConditionUseCase: ScanConditionUseCase
) {
    operator fun invoke(setting: ScannerSetting, descriptor: Descriptor): Observable<Either<Throwable, String>> {
        return beckonClient
                .scanAndSave(
                        scanConditionUseCase().map { it == ScanCondition.OK },
                        setting,
                        descriptor,
                        { true })
                .doOnNext { Timber.d("onNext $it") }
                .doOnDispose { cleanup() }
                .take(1)
    }

    private fun cleanup() {
        Timber.d("Clean up")

        val disposable = beckonClient.disconnectAllConnectedButNotSavedDevices().subscribe({
            Timber.d("Disconnect all devices")
        }, {
            Timber.e("Error when disconnect all devices")
        })
    }
}