package com.technocreatives.example.domain

import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.DeviceState
import com.technocreatives.beckon.deviceStates
import com.technocreatives.example.AxkidState
import com.technocreatives.example.SeatedState
import com.technocreatives.example.mapper
import com.technocreatives.example.reducer
import io.reactivex.Observable
import timber.log.Timber

class FetchDevicesUseCase(private val beckonClient: BeckonClient) {
    fun execute(): Observable<List<DeviceState<AxkidState>>> {
        Timber.d("FetchDevicesUseCase execute!")
        return beckonClient.devices()
                .map { devices -> devices.map { it.macAddress } }
                .switchMap { addresses -> beckonClient.deviceStates(addresses, mapper, reducer, AxkidState(SeatedState.Unseated, -1, 0)) }
    }

}
