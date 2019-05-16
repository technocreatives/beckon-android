package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.util.debugInfo
import io.reactivex.subjects.BehaviorSubject
import no.nordicsemi.android.ble.BleManagerCallbacks
import timber.log.Timber

class BeckonManagerCallbacks(
    private val stateSubject: BehaviorSubject<ConnectionState>,
    private val bondStateSubject: BehaviorSubject<BondState>
) : BleManagerCallbacks {

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        Timber.d("onDeviceDisconnected $device")
        stateSubject.onNext(ConnectionState.Disconnected)
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        Timber.d("onDeviceDisconnecting $device")
        stateSubject.onNext(ConnectionState.Disconnecting)
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        Timber.d("onDeviceConnected $device")
        Timber.d("Connect to ${device.debugInfo()}")
        stateSubject.onNext(ConnectionState.Connected)
    }

    override fun onDeviceNotSupported(device: BluetoothDevice) {
        Timber.d("onDeviceNotSupported ${device.debugInfo()}")
        stateSubject.onNext(ConnectionState.NotSupported)
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        Timber.d("onBondingFailed ${device.debugInfo()}")
        bondStateSubject.onNext(BondState.BondingFailed)
    }

    override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) {
        Timber.d("onServicesDiscovered ${device.debugInfo()} $optionalServicesFound")
    }

    override fun onBondingRequired(device: BluetoothDevice) {
        Timber.d("onBondingRequired ${device.debugInfo()}")
    }

    override fun onLinkLossOccurred(device: BluetoothDevice) {
        Timber.d("onLinkLossOccurred ${device.debugInfo()}")
    }

    override fun onBonded(device: BluetoothDevice) {
        Timber.d("onBonded ${device.debugInfo()}")
        bondStateSubject.onNext(BondState.Bonded)
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        Timber.d("onDeviceReady ${device.debugInfo()}")
        stateSubject.onNext(ConnectionState.Ready)
    }

    override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
        Timber.d("onError Device: ${device.debugInfo()} Message: $message ErrorCode: $errorCode")
        stateSubject.onNext(ConnectionState.Failed(message, errorCode))
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        Timber.d("onDeviceConnecting ${device.debugInfo()}")
        stateSubject.onNext(ConnectionState.Connecting)
    }
}