package com.technocreatives.beckon.noop

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.State
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data

class NoopBeckonDevice(val metadata: Metadata) : BeckonDevice {
    override fun states(): Observable<State> {
        return Observable.never()
    }

    override fun connectionStates(): Observable<ConnectionState> {
        return Observable.never()
    }

    override fun currentState(): ConnectionState {
        return ConnectionState.NotConnected
    }

    override fun subscribe(characteristic: CharacteristicSuccess.Notify): Completable {
        return Completable.never()
    }

    override fun subscribe(list: List<CharacteristicSuccess.Notify>): Completable {
        return Completable.never()
    }

    override fun unsubscribe(characteristic: CharacteristicSuccess.Notify): Completable {
        return Completable.never()
    }

    override fun unsubscribe(characteristic: List<CharacteristicSuccess.Notify>): Completable {
        return Completable.never()
    }

    override fun createBond(): Completable {
        return Completable.never()
    }

    override fun removeBond(): Completable {
        return Completable.never()
    }

    override fun read(characteristic: CharacteristicSuccess.Read): Single<Change> {
        return Single.never()
    }

    override fun bondStates(): Observable<BondState> {
        return Observable.never()
    }

    override fun changes(): Observable<Change> {
        return Observable.never()
    }

    override fun disconnect(): Completable {
        return Completable.never()
    }

    override fun metadata(): Metadata {
        return metadata
    }

    override fun write(data: Data, characteristic: CharacteristicSuccess.Write): Single<Change> {
        return Single.never()
    }
}
