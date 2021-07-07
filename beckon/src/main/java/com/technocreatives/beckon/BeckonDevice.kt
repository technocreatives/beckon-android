package com.technocreatives.beckon

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import no.nordicsemi.android.ble.data.Data

interface BeckonDevice {

    fun connectionStates(): Flow<ConnectionState>
    fun bondStates(): Flow<BondState>

    fun changes(): Flow<Change>
    fun states(): Flow<State>

    suspend fun disconnect(): Either<Throwable, Unit>

    fun metadata(): Metadata

    suspend fun createBond(): Either<ConnectionError.CreateBondFailed, Unit>
    suspend fun removeBond(): Either<ConnectionError.RemoveBondFailed, Unit>

    suspend fun read(characteristic: FoundCharacteristic.Read): Either<ReadDataException, Change>

    suspend fun write(
        data: Data,
        characteristic: FoundCharacteristic.Write
    ): Either<WriteDataException, Change>

    suspend fun writeSplit(
        data: ByteArray,
        characteristic: FoundCharacteristic.Write
    ): Either<WriteDataException, SplitPackage>

    suspend fun subscribe(notify: FoundCharacteristic.Notify): Either<SubscribeDataException, Unit>
    suspend fun subscribe(list: List<FoundCharacteristic.Notify>): Either<Throwable, Unit>
    suspend fun unsubscribe(notify: FoundCharacteristic.Notify): Either<SubscribeDataException, Unit>
    suspend fun unsubscribe(list: List<FoundCharacteristic.Notify>): Either<Throwable, Unit>

    suspend fun requestMtu(mtu: Mtu): Either<MtuRequestError, Mtu>
    fun overrideMtu(mtu: Mtu)
    fun mtu(): Mtu

}
