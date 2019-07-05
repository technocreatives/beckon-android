package com.technocreatives.example.domain

import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.DeviceState
import com.technocreatives.beckon.deviceStates
import com.technocreatives.example.AxkidState
import com.technocreatives.example.SeatedState
import com.technocreatives.example.mapper
import com.technocreatives.example.reducer
import io.reactivex.Observable


typealias ScanResult = DeviceState<AxkidState>

class ScanAndConnectDeviceUseCase(private val beckonClient: BeckonClient) {

    fun execute(characteristics: List<Characteristic>): Observable<ScanResult> {
        return beckonClient.scanAndConnect(characteristics)
                .filter { it.success() }
                .flatMapSingle { beckonClient.findDevice(it.macAddress) }
                .flatMap { it.deviceStates(mapper, reducer, AxkidState(SeatedState.Unseated, -1, 0)) }
                .filter { it.state.seatedState is SeatedState.Seated }
                .take(1)
    }
}