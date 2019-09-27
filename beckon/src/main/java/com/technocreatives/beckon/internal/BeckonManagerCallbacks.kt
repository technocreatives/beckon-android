package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import com.technocreatives.beckon.BleConnectionState
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.util.debugInfo
import io.reactivex.subjects.BehaviorSubject
import no.nordicsemi.android.ble.BleManagerCallbacks
import timber.log.Timber

internal class BeckonManagerCallbacks(
    private val bondStateSubject: BehaviorSubject<BondState>,
    private val stateCallback: (BleConnectionState) -> Unit
) : BleManagerCallbacks {

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        Timber.i("onDeviceDisconnected $device")
        stateCallback(BleConnectionState.Disconnected)
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        Timber.i("onDeviceDisconnecting $device")
        stateCallback(BleConnectionState.Disconnecting)
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        Timber.i("onDeviceConnected $device")
        Timber.i("Connect to ${device.debugInfo()}")
        stateCallback(BleConnectionState.Connected)
    }

    override fun onDeviceNotSupported(device: BluetoothDevice) {
        Timber.i("onDeviceNotSupported ${device.debugInfo()}")
        stateCallback(BleConnectionState.NotSupported)
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        Timber.i("onBondingFailed ${device.debugInfo()}")
        bondStateSubject.onNext(BondState.NotBonded)
    }

    override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) {
        Timber.i("onServicesDiscovered ${device.debugInfo()} $optionalServicesFound")
    }

    override fun onBondingRequired(device: BluetoothDevice) {
        Timber.i("onBondingRequired ${device.debugInfo()}")
    }

    override fun onLinkLossOccurred(device: BluetoothDevice) {
        Timber.i("onLinkLossOccurred ${device.debugInfo()}")
        stateCallback(BleConnectionState.Disconnected)
    }

    override fun onBonded(device: BluetoothDevice) {
        Timber.i("onBonded ${device.debugInfo()}")
        bondStateSubject.onNext(BondState.Bonded)
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        Timber.i("onDeviceReady ${device.debugInfo()}")
        stateCallback(BleConnectionState.Ready)
    }

    override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
        Timber.w("onError Device: ${device.debugInfo()} Message: $message ErrorCode: $errorCode")
        stateCallback(BleConnectionState.Failed(message, errorCode))
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        Timber.i("onDeviceConnecting ${device.debugInfo()}")
        stateCallback(BleConnectionState.Connecting)
    }
}
