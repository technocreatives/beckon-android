package com.technocreatives.beckon

import arrow.core.Either
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow
import no.nordicsemi.android.ble.data.Data

interface BeckonDevice {

    fun connectionStates(): Flow<ConnectionState>
    fun bondStates(): Flow<BondState>

    fun changes(): Flow<Change>
    fun states(): Flow<State>

    fun currentState(): ConnectionState

    suspend fun disconnect(): Either<Throwable, Unit>

    fun metadata(): Metadata

    suspend fun createBond(): Either<Throwable, Unit>
    suspend fun removeBond(): Either<Throwable, Unit>

    suspend fun read(characteristic: CharacteristicSuccess.Read): Change

    suspend fun write(data: Data, characteristic: CharacteristicSuccess.Write): Change

    suspend fun subscribe(notify: CharacteristicSuccess.Notify): Either<Throwable, Unit>
    suspend fun subscribe(list: List<CharacteristicSuccess.Notify>): Either<Throwable, Unit>
    suspend fun unsubscribe(notify: CharacteristicSuccess.Notify): Either<Throwable, Unit>
    suspend fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Either<Throwable, Unit>
}
