package com.technocreatives.example.domain

import arrow.core.Either
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.extension.scanAndSave
import com.technocreatives.example.AxkidState
import com.technocreatives.example.SeatedState
import com.technocreatives.example.mapper
import com.technocreatives.example.reducer
import io.reactivex.Observable
import timber.log.Timber

class ScanDeviceUseCase(
        private val beckonClient: BeckonClient
) {
    operator fun invoke(setting: ScannerSetting, descriptor: Descriptor): Observable<Either<Throwable, String>> {
        val defaultState = AxkidState(SeatedState.Unseated, -1, 0)
        return beckonClient
                .scanAndSave(
                        Observable.just(true),
                        setting,
                        descriptor,
                        mapper,
                        reducer,
                        defaultState,
                        { it.seatedState is SeatedState.Seated })
                .doOnNext { Timber.d("onNext $it") }
                .doOnDispose { cleanup() }
                .take(1)
    }

    private fun cleanup() {
        Timber.d("Clean up")
        beckonClient.disconnectAllConnectedButNotSavedDevices().subscribe({
            Timber.d("Disconnect all devices")
        }, {
            Timber.e("Error when disconnect all devices")
        })
    }
}
