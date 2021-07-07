package com.technocreatives.beckon.rx2

import com.technocreatives.beckon.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data

interface BeckonDeviceRx {

    fun connectionStates(): Observable<ConnectionState>
    fun bondStates(): Observable<BondState>

    fun changes(): Observable<Change>
    fun states(): Observable<State>

    fun disconnect(): Completable

    fun metadata(): Metadata

    fun createBond(): Completable
    fun removeBond(): Completable

    fun read(characteristic: FoundCharacteristic.Read): Single<Change>

    fun write(data: Data, characteristic: FoundCharacteristic.Write): Single<Change>

    fun subscribe(notify: FoundCharacteristic.Notify): Completable
    fun subscribe(list: List<FoundCharacteristic.Notify>): Completable
    fun unsubscribe(notify: FoundCharacteristic.Notify): Completable
    fun unsubscribe(list: List<FoundCharacteristic.Notify>): Completable
}
