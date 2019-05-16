package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicResult
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DiscoveredDevice
import io.reactivex.Observable
import timber.log.Timber

internal class BeckonDeviceImpl(
    private val context: Context,
    private val metadata: DeviceMetadata,
    private val bluetoothDevice: BluetoothDevice
) : BeckonDevice {

    private val manager by lazy {
        BeckonBleManager(context, metadata.characteristics)
    }

    companion object {
        fun create(
            context: Context,
            bluetoothDevice: BluetoothDevice,
            characteristics: List<Characteristic>
        ): BeckonDevice {
            val metadata =
                    DeviceMetadata(bluetoothDevice.address, bluetoothDevice.name, characteristics)
            return BeckonDeviceImpl(context, metadata, bluetoothDevice)
        }
    }

    override fun connectionStates(): Observable<ConnectionState> {
        return manager.connectionState()
    }

    override fun changes(): Observable<Change> {
        return manager.changes().map {
            Change(metadata, it.first, it.second)
        }
    }

    override fun currentState(): ConnectionState {
        return manager.currentState()
    }

    override fun connect(): Observable<DiscoveredDevice> {
        Timber.d("connect $bluetoothDevice")
        val request = manager.connect(bluetoothDevice)
                .retry(3, 100)
                .useAutoConnect(true)

        return manager.connect(request).map { toDiscoveredDevice(it, metadata) }
    }

    override fun disconnect() {
        Timber.d("disconnect")
        manager.disconnect().enqueue()
    }

    internal fun bluetoothDevice(): BluetoothDevice {
        return bluetoothDevice
    }

    override fun metadata(): DeviceMetadata {
        return metadata
    }

    override fun bondStates(): Observable<BondState> {
        return manager.bondStates()
    }

    override fun createBond() {
        manager.doCreateBond()
    }

    override fun removeBond() {
        manager.doRemoveBond()
    }

    override fun toString(): String {
        return metadata.toString()
    }

    private fun toDiscoveredDevice(results: List<CharacteristicResult>, info: DeviceMetadata): DiscoveredDevice {
        val success = results.any { it is CharacteristicResult.Failed && it.characteristic.required }
        return if (success) {
            DiscoveredDevice.FailureDevice(info, results)
        } else {
            DiscoveredDevice.SuccessDevice(info, results)
        }
    }
}
