package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import arrow.core.Either
import arrow.core.left
import arrow.fx.coroutines.parTraverseEither
import com.technocreatives.beckon.*
import kotlinx.coroutines.flow.Flow
import no.nordicsemi.android.ble.data.Data

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

    override suspend fun disconnect(): Either<ConnectionError.DisconnectDeviceFailed, Unit> =
        manager.disconnect(Unit)
            .also { manager.unregister() }

    override fun metadata(): Metadata {
        return metadata
    }

    override suspend fun createBond(): Either<ConnectionError.CreateBondFailed, Unit> {
        return manager.doCreateBond()
    }

    override suspend fun removeBond(): Either<ConnectionError.RemoveBondFailed, Unit> {
        return manager.doRemoveBond()
    }

    override suspend fun read(characteristic: FoundCharacteristic.Read): Either<ReadDataException, Change> {
        return manager.read(characteristic.id, characteristic.gatt)
    }

    override suspend fun write(
        data: Data,
        characteristic: FoundCharacteristic.Write
    ): Either<WriteDataException, Change> {
        return manager.write(data, characteristic.id, characteristic.gatt)
    }

    override suspend fun writeSplit(
        data: ByteArray,
        characteristic: FoundCharacteristic.Write
    ): Either<WriteDataException, SplitPackage> {
        return manager.writeSplit(data, characteristic.id, characteristic.gatt)
    }

    override suspend fun requestMtu(mtu: Mtu): Either<MtuRequestError, Mtu> =
        manager.doRequestMtu(mtu.value).map { Mtu(it) }

    override fun overrideMtu(mtu: Mtu) {
        manager.doOverrideMtu(mtu.value)
    }

    override fun mtu(): Mtu =
        Mtu(manager.mtu())

    override suspend fun subscribe(notify: FoundCharacteristic.Notify): Either<SubscribeDataException, Unit> {
        return manager.subscribe(notify.id, notify.gatt)
    }

    override suspend fun subscribe(list: List<FoundCharacteristic.Notify>): Either<Throwable, Unit> {
        if (list.isEmpty()) return IllegalArgumentException("Empty notify list").left()
        return list.parTraverseEither { subscribe(it) }.map { }
    }

    override suspend fun unsubscribe(notify: FoundCharacteristic.Notify): Either<SubscribeDataException, Unit> {
        return manager.unsubscribe(notify)
    }

    override suspend fun unsubscribe(list: List<FoundCharacteristic.Notify>): Either<Throwable, Unit> {
        if (list.isEmpty()) return IllegalArgumentException("Empty notify list").left()
        return list.parTraverseEither { unsubscribe(it) }.map { }
    }
}
