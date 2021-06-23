package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.traverseEither
import arrow.fx.coroutines.parTraverseEither
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonDeviceRx
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.ConnectionError
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.State
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

internal class BeckonDeviceImpl(
    private val bluetoothDevice: BluetoothDevice,
    private val manager: BeckonBleManager,
    private val metadata: Metadata
) : BeckonDevice {
    override fun connectionStates(): Flow<ConnectionState> {
        return manager.connectionState().asFlow()
    }

    override fun bondStates(): Flow<BondState> {
        return manager.bondStates().asFlow()
    }

    override fun changes(): Flow<Change> {
        return manager.changes().asFlow()
    }

    override fun states(): Flow<State> {
        return manager.states().asFlow()
    }

    override fun currentState(): ConnectionState {
        return manager.currentState()
    }

    override suspend fun disconnect(): Either<Throwable, Unit> {
        return Either.catch {
            manager.disconnect().await()
        }
    }

    override fun metadata(): Metadata {
        return metadata
    }

    override suspend fun createBond(): Either<Throwable, Unit> {

        return manager.doCreateBond().await().right()
    }

    override suspend fun removeBond(): Either<Throwable, Unit> {
        return manager.doRemoveBond().await().right()

    }

    override suspend fun read(characteristic: CharacteristicSuccess.Read): Change {
        return manager.read(characteristic.id, characteristic.gatt).await()
    }

    override suspend fun write(data: Data, characteristic: CharacteristicSuccess.Write): Change {
        return manager.write(data, characteristic.id, characteristic.gatt).await()
    }

    override suspend fun subscribe(notify: CharacteristicSuccess.Notify): Either<Throwable, Unit> {
        return manager.subscribe(notify).await().right()
    }

    override suspend fun subscribe(list: List<CharacteristicSuccess.Notify>): Either<Throwable, Unit> {
        if (list.isEmpty()) return IllegalArgumentException("Empty notify list").left()
        return list.parTraverseEither { subscribe(it) }.map { Unit }
    }

    override suspend fun unsubscribe(notify: CharacteristicSuccess.Notify): Either<Throwable, Unit> {
        return manager.unsubscribe(notify).await().right()
    }

    override suspend fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Either<Throwable, Unit> {
        if (list.isEmpty()) return IllegalArgumentException("Empty notify list").left()
        return list.parTraverseEither { unsubscribe(it) }.map { Unit }
    }

}
