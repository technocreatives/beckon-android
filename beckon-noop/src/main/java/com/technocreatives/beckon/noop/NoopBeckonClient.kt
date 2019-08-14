package com.technocreatives.beckon.noop

import android.content.Context
import arrow.core.Either
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonDeviceError
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.SavedMetadata
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data

class NoopBeckonClient() : BeckonClient {
    override fun connect(metadata: SavedMetadata): Single<BeckonDevice> {
        return Single.never()
    }

    override fun findConnectedDeviceO(metadata: SavedMetadata): Observable<Either<BeckonDeviceError.ConnectedDeviceNotFound, BeckonDevice>> {
        return Observable.empty()
    }

    override fun startScan(setting: ScannerSetting): Observable<ScanResult> {
        return Observable.empty()
    }

    override fun connect(result: ScanResult, descriptor: Descriptor): Single<BeckonDevice> {
        return Single.never()
    }

    override fun findSavedDevice(macAddress: MacAddress): Single<SavedMetadata> {
        return Single.never()
    }

    companion object {
        private var beckonClient: NoopBeckonClient? = null
        fun create(): NoopBeckonClient {
            if (beckonClient == null) {
                beckonClient = NoopBeckonClient()
            }
            return beckonClient!!
        }
    }

    override fun save(macAddress: String): Single<String> {
        return Single.never()
    }

    override fun remove(macAddress: String): Single<String> {
        return Single.never()
    }

    override fun findConnectedDevice(macAddress: MacAddress): Single<BeckonDevice> {
        return Single.never()
    }

    override fun connectedDevices(): Observable<List<Metadata>> {
        return Observable.empty()
    }

    override fun write(macAddress: MacAddress, characteristic: CharacteristicSuccess.Write, data: Data): Single<Change> {
        return Single.never()
    }

    override fun read(macAddress: MacAddress, characteristic: CharacteristicSuccess.Read): Single<Change> {
        return Single.never()
    }

    override fun stopScan() {
    }

    override fun disconnectAllConnectedButNotSavedDevices(): Completable {
        return Completable.never()
    }

    override fun savedDevices(): Observable<List<SavedMetadata>> {
        throw UnsupportedOperationException("not implemented")
    }

    override fun disconnect(macAddress: String): Completable {
        return Completable.never()
    }

    override fun register(context: Context) {
    }

    override fun unregister(context: Context) {
    }

    override fun bluetoothState(): Observable<BluetoothState> {
        return Observable.just(BluetoothState.ON)
    }
}
