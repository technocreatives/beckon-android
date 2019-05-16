package com.technocreatives.example.domain

import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.DeviceState
import com.technocreatives.beckon.DiscoveredDevice
import com.technocreatives.beckon.deviceStates
import com.technocreatives.example.AxkidState
import com.technocreatives.example.SeatedState
import com.technocreatives.example.mapper
import com.technocreatives.example.reducer
import io.reactivex.Observable


class ScanDeviceUseCase(val beckonClient: BeckonClient) {

    fun execute(characteristics: List<Characteristic>): Observable<BeckonScanResult> {
        return beckonClient.scan()
    }
}