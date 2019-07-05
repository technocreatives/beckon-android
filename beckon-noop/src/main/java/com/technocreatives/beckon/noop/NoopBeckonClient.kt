package com.technocreatives.beckon.noop

import android.content.Context
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicDetail
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.internal.BluetoothState
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

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

    override fun scanAndConnect(characteristics: List<Characteristic>): Observable<DeviceMetadata> {
        return Observable.empty()
    }

    override fun connect(result: BeckonScanResult, characteristics: List<Characteristic>): Single<DeviceMetadata> {
        return Single.never()
    }

    override fun save(macAddress: String): Completable {
        return Completable.never()
    }

    override fun remove(macAddress: String): Completable {
        return Completable.never()
    }

    override fun findDevice(macAddress: MacAddress): Single<BeckonDevice> {
        return Single.never()
    }

    override fun savedDevices(): Observable<List<DeviceMetadata>> {
        return Observable.empty()
    }

    override fun connectedDevices(): Observable<List<DeviceMetadata>> {
        return Observable.empty()
    }

    override fun write(macAddress: MacAddress, characteristic: CharacteristicDetail.Write, data: Data): Single<Change> {
        return Single.never()
    }

    override fun write(macAddress: MacAddress, characteristicUuid: UUID, data: Data): Single<Change> {
        return Single.never()
    }

    override fun read(macAddress: MacAddress, characteristic: CharacteristicDetail.Read): Single<Change> {
        return Single.never()
    }

    override fun startScan(setting: ScannerSetting) {
    }

    override fun stopScan() {
    }

    override fun scan(): Observable<BeckonScanResult> {
        return Observable.never()
    }

    override fun disconnectAllConnectedButNotSavedDevices() {
    }

    override fun disconnectAllExcept(addresses: List<String>) {
    }

    override fun devices(): Observable<List<DeviceMetadata>> {
        return Observable.never()
    }

    override fun currentDevices(): List<DeviceMetadata> {
        return emptyList()
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
