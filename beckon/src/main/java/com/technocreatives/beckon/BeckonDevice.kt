package com.technocreatives.beckon

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data

interface BeckonDevice {

    fun connectionStates(): Observable<ConnectionState>
    fun bondStates(): Observable<BondState>

    fun changes(): Observable<Change>

    fun currentState(): ConnectionState

    fun disconnect(): Completable

    fun metadata(): DeviceMetadata

    fun createBond(): Completable
    fun removeBond(): Completable

    fun read(characteristic: CharacteristicSuccess.Read): Single<Change>

    fun write(data: Data, characteristic: CharacteristicSuccess.Write): Single<Change>

    fun subscribe(notify: CharacteristicSuccess.Notify): Completable
    fun subscribe(list: List<CharacteristicSuccess.Notify>): Completable
    fun unsubscribe(notify: CharacteristicSuccess.Notify): Completable
    fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Completable
}
