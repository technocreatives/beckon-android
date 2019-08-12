package com.technocreatives.example.domain

import arrow.core.Either
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.extension.BeckonState
import com.technocreatives.beckon.extension.deviceStates
import com.technocreatives.example.AxkidState
import com.technocreatives.example.SeatedState
import com.technocreatives.example.mapper
import com.technocreatives.example.reducer
import io.reactivex.Observable
import timber.log.Timber

class FetchDevicesUseCase(private val beckonClient: BeckonClient) {
    fun execute(): Observable<List<Either<Throwable, BeckonState<AxkidState>>>> {
        Timber.d("FetchDevicesUseCase execute!")
        return beckonClient.connectedDevices()
                .map { devices -> devices.map { it.macAddress } }
                .switchMap { addresses -> beckonClient.deviceStates(addresses, mapper, reducer, AxkidState(SeatedState.Unseated, -1, 0)) }
    }

}
