package com.technocreatives.beckon.noop

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicResult
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceMetadata
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

class NoopBeckonDevice(val metadata: DeviceMetadata) : BeckonDevice {

    override fun createBond(): Completable {
        return Completable.never()
    }

    override fun removeBond(): Completable {
        return Completable.never()
    }

    override fun read(characteristic: CharacteristicResult.Read): Single<Change> {
        return Single.never()
    }

    override fun read(characteristicUUID: UUID): Single<Change> {
        return Single.never()
    }

    override fun write(data: Data, characteristicUUID: UUID): Single<Change> {
        return Single.never()
    }

    override fun connectionStates(): Observable<ConnectionState> {
        return Observable.never()
    }

    override fun bondStates(): Observable<BondState> {
        return Observable.never()
    }

    override fun changes(): Observable<Change> {
        return Observable.never()
    }

    override fun currentState(): ConnectionState {
        return ConnectionState.Connected
    }

    override fun disconnect() {
    }

    override fun metadata(): DeviceMetadata {
        return metadata
    }

    override fun write(data: Data, characteristic: CharacteristicResult.Write): Single<Change> {
        return Single.never()
    }
}
