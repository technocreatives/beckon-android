package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.util.debugInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.ble.observer.BondingObserver
import timber.log.Timber

internal class BeckonBondingObserver(
    private val bondStateSubject: MutableSharedFlow<BondState>,
) : BondingObserver {
    override fun onBondingRequired(device: BluetoothDevice) {
        // todo do something
        Timber.i("onBondingRequired ${device.debugInfo()}")
    }

    override fun onBonded(device: BluetoothDevice) {
        Timber.i("onBonded ${device.debugInfo()}")
        runBlocking {
            bondStateSubject.emit(BondState.Bonded)
        }
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        Timber.i("onBondingFailed ${device.debugInfo()}")
        runBlocking {
            bondStateSubject.emit(BondState.NotBonded)
        }
    }
}
