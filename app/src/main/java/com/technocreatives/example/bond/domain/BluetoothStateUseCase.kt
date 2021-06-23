package com.technocreatives.example.bond.domain

import com.technocreatives.beckon.BeckonClientRx
import com.technocreatives.beckon.BluetoothState
import io.reactivex.Observable

class BluetoothStateUseCase(private val beckonClient: BeckonClientRx) {
    operator fun invoke(): Observable<BluetoothState> {
        return beckonClient.bluetoothState()
    }
}