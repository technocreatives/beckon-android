package com.technocreatives.beckon.mock

import android.content.Context
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DiscoveredDevice
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.internal.BluetoothState
import com.technocreatives.beckon.justever
import io.reactivex.Observable

class MockBeckonClient(
    private val devices: List<MockDevice>
) : BeckonClient {

    override fun startScan(setting: ScannerSetting) {
    }

    override fun stopScan() {
    }

    override fun scan(): Observable<BeckonScanResult> {
        return Observable.empty()
    }

    override fun scanAndConnect(characteristics: List<Characteristic>): Observable<DiscoveredDevice> {
        return Observable.empty()
    }

    override fun disconnectAllConnectedDevicesButNotSavedDevices() {
    }

    override fun save(macAddress: String): Observable<Unit> {
        return Observable.empty()
    }

    override fun remove(macAddress: String): Observable<Unit> {
        return Observable.empty()
    }

    override fun findDevice(macAddress: String): Observable<BeckonDevice> {
        return Observable.defer { ->
            val device = devices.find { it.metadata().macAddress == macAddress }
            if (device == null) Observable.empty<BeckonDevice>()
            else Observable.just(device)
        }
    }

    override fun devices(): Observable<List<DeviceMetadata>> {
        return devices.map { it.metadata() }.justever()
    }

    override fun currentDevices(): List<DeviceMetadata> {
        return devices.map { it.metadata() }
    }

    override fun connect(result: BeckonScanResult, characteristics: List<Characteristic>): Observable<DiscoveredDevice> {
        return Observable.empty()
    }

    override fun disconnect(macAddress: String): Boolean {
        return false
    }

    override fun register(context: Context) {
    }

    override fun unregister(context: Context) {
    }

    override fun bluetoothState(): Observable<BluetoothState> {
        return BluetoothState.ON.justever()
    }
}
