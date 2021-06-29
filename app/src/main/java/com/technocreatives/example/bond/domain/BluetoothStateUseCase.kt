package com.technocreatives.example.bond.domain

import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.rx2.BeckonClientRx
import io.reactivex.Observable

class BluetoothStateUseCase(private val beckonClient: BeckonClientRx) {
    operator fun invoke(): Observable<BluetoothState> {
        return beckonClient.bluetoothState()
    }
}