package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.util.debugInfo
import io.reactivex.subjects.BehaviorSubject
import no.nordicsemi.android.ble.observer.BondingObserver
import timber.log.Timber

internal class BeckonBondingObserver(
    private val bondStateSubject: BehaviorSubject<BondState>,
) : BondingObserver {
    override fun onBondingRequired(device: BluetoothDevice) {
        // todo do something
        Timber.i("onBondingRequired ${device.debugInfo()}")
    }

    override fun onBonded(device: BluetoothDevice) {
        Timber.i("onBonded ${device.debugInfo()}")
        bondStateSubject.onNext(BondState.Bonded)
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        Timber.i("onBondingFailed ${device.debugInfo()}")
        bondStateSubject.onNext(BondState.NotBonded)
    }
}
