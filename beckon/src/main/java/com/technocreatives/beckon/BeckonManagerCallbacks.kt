package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import com.technocreatives.beckon.util.debugInfo
import no.nordicsemi.android.ble.BleManagerCallbacks
import timber.log.Timber

class BeckonManagerCallbacks : BleManagerCallbacks {
    override fun onDeviceDisconnected(device: BluetoothDevice) {
        Timber.d("onDeviceDisconnected $device")
        // stateSubject.onNext(PeripheralState.Disconnected(peripheral))
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        Timber.d("onDeviceDisconnecting $device")
        // stateSubject.onNext(PeripheralState.Disconnecting(peripheral))
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        Timber.d("onDeviceConnected $device")
        // stateSubject.onNext(PeripheralState.Connected(peripheral, Unit))
        Timber.d("Connect to ${device.debugInfo()}")
    }

    override fun onDeviceNotSupported(device: BluetoothDevice) {
        Timber.d("onDeviceNotSupported ${device.debugInfo()}")
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        Timber.d("onBondingFailed ${device.debugInfo()}")
        // stateSubject.onNext(PeripheralState.Failed(peripheral, ScanFailureException(0)))
    }

    override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) {
        Timber.d("onServicesDiscovered ${device.debugInfo()}")
    }

    override fun onBondingRequired(device: BluetoothDevice) {
        Timber.d("onBondingRequired ${device.debugInfo()}")
    }

    override fun onLinkLossOccurred(device: BluetoothDevice) {
        Timber.d("onLinkLossOccurred ${device.debugInfo()}")
    }

    override fun onBonded(device: BluetoothDevice) {
        Timber.d("onBonded ${device.debugInfo()}")
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        Timber.d("onDeviceReady ${device.debugInfo()}")
    }

    override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
        Timber.d("onError ${device.debugInfo()} $message $errorCode")
        // stateSubject.onNext(PeripheralState.Failed(peripheral, ScanFailureException(errorCode)))
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        Timber.d("onDeviceConnecting ${device.debugInfo()}")
        // stateSubject.onNext(PeripheralState.Connecting(peripheral))
    }
}