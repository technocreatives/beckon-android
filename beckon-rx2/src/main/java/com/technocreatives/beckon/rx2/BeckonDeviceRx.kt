package com.technocreatives.beckon.rx2

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

interface BeckonDeviceRx {

    fun connectionStates(): Observable<ConnectionState>
    fun bondStates(): Observable<BondState>

    fun changes(): Observable<Change>
    fun states(): Observable<State>

    fun disconnect(): Completable

    fun metadata(): Metadata

    fun createBond(): Completable
    fun removeBond(): Completable

    fun read(characteristic: CharacteristicSuccess.Read): Single<Change>

    fun write(data: Data, characteristic: CharacteristicSuccess.Write): Single<Change>

    fun subscribe(notify: CharacteristicSuccess.Notify): Completable
    fun subscribe(list: List<CharacteristicSuccess.Notify>): Completable
    fun unsubscribe(notify: CharacteristicSuccess.Notify): Completable
    fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Completable
}
