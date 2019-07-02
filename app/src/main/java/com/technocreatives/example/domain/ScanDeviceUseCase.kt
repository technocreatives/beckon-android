package com.technocreatives.example.domain

import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Characteristic
import io.reactivex.Observable


class ScanDeviceUseCase(val beckonClient: BeckonClient) {

    fun execute(characteristics: List<Characteristic>): Observable<BeckonScanResult> {
        return beckonClient.scan()
    }
}