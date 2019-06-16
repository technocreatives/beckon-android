package com.technocreatives.beckon.noop

import android.content.Context
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DiscoveredDevice
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.internal.BluetoothState
import io.reactivex.Observable

class NoopBeckonClient() : BeckonClient {

    companion object {
        private var beckonClient: NoopBeckonClient? = null
        fun create(): NoopBeckonClient {
            if (beckonClient == null) {
                beckonClient = NoopBeckonClient()
            }
            return beckonClient!!
        }
    }

    override fun startScan(setting: ScannerSetting) {
    }

    override fun stopScan() {
    }

    override fun scan(): Observable<BeckonScanResult> {
        return Observable.never()
    }

    override fun scanAndConnect(characteristics: List<Characteristic>): Observable<DiscoveredDevice> {
        return Observable.never()
    }

    override fun disconnectAllConnectedDevicesButNotSavedDevices() {
    }

    override fun save(macAddress: String): Observable<Unit> {
        return Observable.never()
    }

    override fun remove(macAddress: String): Observable<Unit> {
        return Observable.never()
    }

    override fun findDevice(macAddress: String): Observable<BeckonDevice> {
        return Observable.never()
    }

    override fun devices(): Observable<List<DeviceMetadata>> {
        return Observable.never()
    }

    override fun currentDevices(): List<DeviceMetadata> {
        return emptyList()
    }

    override fun connect(result: BeckonScanResult, characteristics: List<Characteristic>): Observable<DiscoveredDevice> {
        return Observable.never()
    }

    override fun disconnect(macAddress: String): Boolean {
        return true
    }

    override fun register(context: Context) {
    }

    override fun unregister(context: Context) {
    }

    override fun bluetoothState(): Observable<BluetoothState> {
        return Observable.just(BluetoothState.ON)
    }
}
