package com.technocreatives.example.bond.domain

import arrow.core.Either
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.extension.scanAndSave
import com.technocreatives.example.bond.DeviceState
import com.technocreatives.example.bond.deviceReducer
import com.technocreatives.example.bond.mapper
import io.reactivex.Observable
import timber.log.Timber

class ScanDeviceUseCase(
        private val beckonClient: BeckonClient,
        private val scanConditionUseCase: ScanConditionUseCase
) {
    operator fun invoke(setting: ScannerSetting, descriptor: Descriptor): Observable<Either<Throwable, String>> {
        val defaultState = DeviceState("blabla")
        return beckonClient
                .scanAndSave(
                        scanConditionUseCase()
                                .doOnNext { Timber.d("scan condition $it") }
                                .map { it == ScanCondition.OK },
                        setting,
                        descriptor,
                        mapper,
                        deviceReducer,
                        defaultState,
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
