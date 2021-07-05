package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import arrow.core.Either
import arrow.core.left
import arrow.fx.coroutines.parTraverseEither
import com.technocreatives.beckon.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import no.nordicsemi.android.ble.data.Data
import java.util.*

internal class BeckonDeviceImpl(
    private val bluetoothDevice: BluetoothDevice,
    private val manager: BeckonBleManager,
    private val metadata: Metadata
) : BeckonDevice {
    override fun connectionStates(): Flow<ConnectionState> {
        return manager.connectionState()
    }

    override fun bondStates(): Flow<BondState> {
        return manager.bondStates()
    }

    override fun changes(): Flow<Change> {
        return manager.changes()
    }

    override fun states(): Flow<State> {
        return manager.states()
    }

    override suspend fun disconnect(): Either<Throwable, Unit> {
        return Either.catch {
            withContext(Dispatchers.IO) {
                manager.disconnect().await()
            }
        }
    }

    override fun metadata(): Metadata {
        return metadata
    }

    override suspend fun createBond(): Either<ConnectionError.CreateBondFailed, Unit> {
        return manager.doCreateBond()
    }

    override suspend fun removeBond(): Either<ConnectionError.RemoveBondFailed, Unit> {
        return manager.doRemoveBond()
    }

    override suspend fun read(characteristic: CharacteristicSuccess.Read): Either<ReadDataException, Change> {
        return manager.read(characteristic.id, characteristic.gatt)
    }

    override suspend fun write(
        data: Data,
        characteristic: CharacteristicSuccess.Write
    ): Either<WriteDataException, Change> {
        return manager.write(data, characteristic.id, characteristic.gatt)
    }

    override fun sendPdu(
        data: ByteArray,
        characteristic: CharacteristicSuccess.Write
    ): Flow<Either<WriteDataException, PduPackage>> {
        return manager.sendPdu(data, characteristic.id, characteristic.gatt)
    }
//
//    override fun sendPdu(
//        data: ByteArray,
//        uuid: UUID
//    ): Flow<Either<WriteDataException, PduPackage>> {
//        TODO("Not yet implemented")
//        metadata.findCharacteristic()
//    }

    override suspend fun subscribe(notify: CharacteristicSuccess.Notify): Either<Throwable, Unit> {
        return manager.subscribe(notify.id, notify.gatt)
    }

    override suspend fun subscribe(list: List<CharacteristicSuccess.Notify>): Either<Throwable, Unit> {
        if (list.isEmpty()) return IllegalArgumentException("Empty notify list").left()
        return list.parTraverseEither { subscribe(it) }.map { }
    }

    override suspend fun unsubscribe(notify: CharacteristicSuccess.Notify): Either<Throwable, Unit> {
        return manager.unsubscribe(notify)
    }

    override suspend fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Either<Throwable, Unit> {
        if (list.isEmpty()) return IllegalArgumentException("Empty notify list").left()
        return list.parTraverseEither { unsubscribe(it) }.map { }
    }
}
