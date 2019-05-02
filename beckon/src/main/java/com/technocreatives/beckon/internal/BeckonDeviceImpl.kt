package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceInfo
import io.reactivex.Observable
import timber.log.Timber

internal class BeckonDeviceImpl(
    val info: DeviceInfo,
    private val context: Context,
    private val bluetoothDevice: BluetoothDevice
) : BeckonDevice {

    private var manager: BeckonBleManager = BeckonBleManager(context, info.characteristics)

    companion object {
        fun create(context: Context, bluetoothDevice: BluetoothDevice, characteristics: List<Characteristic>, keepConnection: Boolean): BeckonDevice {
            val info = DeviceInfo(bluetoothDevice.address, bluetoothDevice.name, characteristics, keepConnection)
            return BeckonDeviceImpl(info, context, bluetoothDevice)
        }
    }

    override fun connectionStates(): Observable<ConnectionState> {
        return manager.connectionState()
    }

    override fun changes(): Observable<Change> {
        return manager.changes()
    }

    override fun currentState(): ConnectionState {
        return manager.currentState()
    }

    override fun doConnect(): Observable<ConnectionState> {
        Timber.d("doConnect $bluetoothDevice")
        manager.connect(bluetoothDevice)
                .retry(3, 100)
                .useAutoConnect(info.keepConnection)
                .enqueue()
        return connectionStates()
    }

    override fun doDisconnect(): Observable<ConnectionState> {
        this.manager.disconnect().enqueue()
        return connectionStates()
    }

    internal fun bluetoothDevice(): BluetoothDevice {
        return bluetoothDevice
    }

    override fun deviceData(): DeviceInfo {
        return info
    }

    override fun toString(): String {
        return info.toString()
    }
}
