package com.technocreatives.beckon

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

interface BeckonDevice {

    fun connectionStates(): Observable<ConnectionState>
    fun bondStates(): Observable<BondState>

    fun changes(): Observable<Change>

    fun currentState(): ConnectionState

    fun disconnect()

    fun metadata(): DeviceMetadata

    fun createBond(): Completable
    fun removeBond(): Completable

    fun read(characteristic: CharacteristicResult.Read): Single<Change>
    fun read(characteristicUUID: UUID): Single<Change>

    fun write(data: Data, characteristic: CharacteristicResult.Write): Single<Change>
    fun write(data: Data, characteristicUUID: UUID): Single<Change>
}
