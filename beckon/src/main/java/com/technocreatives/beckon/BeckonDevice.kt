package com.technocreatives.beckon

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import no.nordicsemi.android.ble.data.Data
import java.util.*

interface BeckonDevice {

    fun connectionStates(): Flow<ConnectionState>
    fun bondStates(): Flow<BondState>

    fun changes(): Flow<Change>
    fun states(): Flow<State>

    suspend fun disconnect(): Either<Throwable, Unit>

    fun metadata(): Metadata

    suspend fun createBond(): Either<ConnectionError.CreateBondFailed, Unit>
    suspend fun removeBond(): Either<ConnectionError.RemoveBondFailed, Unit>

    suspend fun read(characteristic: CharacteristicSuccess.Read): Either<ReadDataException, Change>

    suspend fun write(data: Data, characteristic: CharacteristicSuccess.Write): Either<WriteDataException, Change>

    fun sendPdu(data: ByteArray, characteristic: CharacteristicSuccess.Write): Flow<Either<WriteDataException, PduPackage>>

    suspend fun subscribe(notify: CharacteristicSuccess.Notify): Either<Throwable, Unit>
    suspend fun subscribe(list: List<CharacteristicSuccess.Notify>): Either<Throwable, Unit>
    suspend fun unsubscribe(notify: CharacteristicSuccess.Notify): Either<Throwable, Unit>
    suspend fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Either<Throwable, Unit>
}
