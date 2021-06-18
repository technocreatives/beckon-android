package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import com.technocreatives.beckon.BleConnectionState
import com.technocreatives.beckon.util.debugInfo
import no.nordicsemi.android.ble.observer.ConnectionObserver
import timber.log.Timber

internal class BeckonConnectionObserver(
    private val stateCallback: (BleConnectionState) -> Unit
) : ConnectionObserver {
    override fun onDeviceConnecting(device: BluetoothDevice) {
        Timber.i("onDeviceConnecting ${device.debugInfo()}")
        stateCallback(BleConnectionState.Connecting)
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        Timber.i("onDeviceConnected $device, debugInfo: ${device.debugInfo()}")
        stateCallback(BleConnectionState.Connected)
    }

    override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
        Timber.w("onError Device: ${device.debugInfo()} Reason: $reason")
        stateCallback(BleConnectionState.Failed("NO Message", reason))
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        Timber.i("onDeviceReady ${device.debugInfo()}")
        stateCallback(BleConnectionState.Ready)
    }

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        Timber.i("onDeviceDisconnecting $device")
        stateCallback(BleConnectionState.Disconnecting)
    }

    override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
        Timber.i("onDeviceDisconnected $device")
        stateCallback(BleConnectionState.Disconnected)
    }
}
