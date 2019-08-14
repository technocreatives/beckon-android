package com.technocreatives.example.bond.domain

import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BluetoothState
import io.reactivex.Observable

class BluetoothStateUseCase(private val beckonClient: BeckonClient) {
    operator fun invoke(): Observable<BluetoothState> {
        return beckonClient.bluetoothState()
    }
}