package com.technocreatives.beckon.rx2

import android.content.Context
import arrow.core.Either
import com.lenguyenthanh.rxarrow.fix
import com.lenguyenthanh.rxarrow.mapZ
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonDeviceError
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.ConnectionError
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.SavedMetadata
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.State
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.asObservable
import kotlinx.coroutines.rx2.rxCompletable
import kotlinx.coroutines.rx2.rxSingle
import no.nordicsemi.android.ble.data.Data

fun BeckonClient.rx(): com.technocreatives.beckon.rx2.BeckonClientRx {
    return object : com.technocreatives.beckon.rx2.BeckonClientRx {
        override fun startScan(setting: ScannerSetting): Observable<ScanResult> =
            runBlocking(Dispatchers.IO) {
                this@rx.startScan(setting).asObservable()
                    .fix { it.toException() }
            }

        override fun stopScan() {
            runBlocking(Dispatchers.IO) {
                this@rx.stopScan()
            }
        }

        override fun disconnectAllConnectedButNotSavedDevices(): Completable =
            rxCompletable {
                this@rx.disconnectAllConnectedButNotSavedDevices()
            }

        override fun search(
            setting: ScannerSetting,
            descriptor: Descriptor
        ): Observable<Either<ConnectionError, com.technocreatives.beckon.rx2.BeckonDeviceRx>> =
            runBlocking(Dispatchers.IO) {
                this@rx.search(setting, descriptor).asObservable().mapZ { it.rx() }
            }

        override fun connect(result: ScanResult, descriptor: Descriptor): Single<com.technocreatives.beckon.rx2.BeckonDeviceRx> =
            runBlocking(Dispatchers.IO) {
                rxSingle { this@rx.connect(result, descriptor) }
                    .mapZ { it.rx() }
                    .fix { it.toException() }
            }

        override fun connect(metadata: SavedMetadata): Single<com.technocreatives.beckon.rx2.BeckonDeviceRx> =
            runBlocking(Dispatchers.IO) {
                rxSingle { this@rx.connect(metadata) }
                    .mapZ { it.rx() }
                    .fix { it.toException() }
            }

        override fun disconnect(macAddress: MacAddress): Completable =
            runBlocking(Dispatchers.IO) {
                rxCompletable { this@rx.disconnect(macAddress) }
            }

        override fun save(macAddress: MacAddress): Single<MacAddress> =
            runBlocking(Dispatchers.IO) {
                rxSingle { this@rx.save(macAddress) }
                    .fix()
            }

        override fun remove(macAddress: MacAddress): Single<MacAddress> =
            runBlocking(Dispatchers.IO) {
                rxSingle { this@rx.remove(macAddress) }
                    .fix()
            }

        override fun findConnectedDevice(macAddress: MacAddress): Single<com.technocreatives.beckon.rx2.BeckonDeviceRx> =
            runBlocking(Dispatchers.IO) {
                rxSingle { this@rx.findConnectedDevice(macAddress) }
                    .fix { it.toException() }
                    .map { it.rx() }
            }

        override fun findConnectedDeviceO(metadata: SavedMetadata): Observable<Either<BeckonDeviceError, com.technocreatives.beckon.rx2.BeckonDeviceRx>> =
            this@rx.findConnectedDevice(metadata)
                .asObservable()
                .mapZ { it.rx() }

        override fun connectedDevices(): Observable<List<Metadata>> =
            this@rx.connectedDevices().asObservable()

        override fun findSavedDevice(macAddress: MacAddress): Single<SavedMetadata> =
            runBlocking(Dispatchers.IO) {
                rxSingle { this@rx.findSavedDevice(macAddress) }
                    .fix { it.toException() }
            }

        override fun savedDevices(): Observable<List<SavedMetadata>> =
            this@rx.savedDevices().asObservable()

        override fun register(context: Context) {
            runBlocking(Dispatchers.IO) {
                this@rx.register(context)
            }
        }

        override fun unregister(context: Context) {
            runBlocking(Dispatchers.IO) {
                this@rx.unregister(context)
            }
        }

        override fun bluetoothState(): Observable<BluetoothState> =
            this@rx.bluetoothState().asObservable()

        override fun write(
            macAddress: MacAddress,
            characteristic: CharacteristicSuccess.Write,
            data: Data
        ): Single<Change> =
            runBlocking(Dispatchers.IO) {
                rxSingle { this@rx.write(macAddress, characteristic, data) }
                    .fix()
            }

        override fun read(
            macAddress: MacAddress,
            characteristic: CharacteristicSuccess.Read
        ): Single<Change> =
            runBlocking(Dispatchers.IO) {
                rxSingle {
                    this@rx.read(macAddress, characteristic)
                }.fix()
            }
    }
}

fun BeckonDevice.rx(): com.technocreatives.beckon.rx2.BeckonDeviceRx {
    return object : com.technocreatives.beckon.rx2.BeckonDeviceRx {
        override fun connectionStates(): Observable<ConnectionState> {
            return this@rx.connectionStates().asObservable()
        }

        override fun bondStates(): Observable<BondState> {
            return this@rx.bondStates().asObservable()
        }

        override fun changes(): Observable<Change> {
            return this@rx.changes().asObservable()
        }

        override fun states(): Observable<State> {
            return this@rx.states().asObservable()
        }

        // override fun currentState(): ConnectionState {
        //     return this@rx.currentState()
        // }

        override fun disconnect(): Completable {
            return rxCompletable {
                this@rx.disconnect()
            }
        }

        override fun metadata(): Metadata {
            return this@rx.metadata()
        }

        override fun createBond(): Completable {
            return rxCompletable {
                this@rx.createBond()
            }
        }

        override fun removeBond(): Completable {
            return rxCompletable {
                this@rx.removeBond()
            }
        }

        override fun read(characteristic: CharacteristicSuccess.Read): Single<Change> {
            return rxSingle {
                this@rx.read(characteristic)
            }.fix()
        }

        override fun write(
            data: Data,
            characteristic: CharacteristicSuccess.Write
        ): Single<Change> {
            return rxSingle {
                this@rx.write(data, characteristic)
            }.fix()
        }

        override fun subscribe(notify: CharacteristicSuccess.Notify): Completable {
            return rxCompletable {
                this@rx.subscribe(notify)
            }
        }

        override fun subscribe(list: List<CharacteristicSuccess.Notify>): Completable {
            return rxCompletable {
                this@rx.subscribe(list)
            }
        }

        override fun unsubscribe(notify: CharacteristicSuccess.Notify): Completable {
            return rxCompletable {
                this@rx.unsubscribe(notify)
            }
        }

        override fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Completable {
            return rxCompletable {
                this@rx.unsubscribe(list)
            }
        }
    }
}
