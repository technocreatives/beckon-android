package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import arrow.core.Either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicDetail
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DisconnectDeviceFailedException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.util.UUID

internal class BeckonDeviceImpl(
    private val bluetoothDevice: BluetoothDevice,
    private val manager: BeckonBleManager,
    private val discoveredDevice: DeviceMetadata
) : BeckonDevice {

    override fun connectionStates(): Observable<ConnectionState> {
        return manager.connectionState()
    }

    override fun changes(): Observable<Change> {
        return manager.changes()
    }

    override fun currentState(): ConnectionState {
        return manager.currentState()
    }

    override fun disconnect(): Completable {
        Timber.d("disconnect")
        return Completable.create { emitter ->
            manager.disconnect()
                .done { emitter.onComplete() }
                .fail { device, status -> emitter.onError(DisconnectDeviceFailedException(device.address, status)) }
                .enqueue()
        }
    }

    internal fun bluetoothDevice(): BluetoothDevice {
        return bluetoothDevice
    }

    override fun metadata(): DeviceMetadata {
        return discoveredDevice
    }

    override fun bondStates(): Observable<BondState> {
        return manager.bondStates()
    }

    override fun createBond(): Completable {
        return manager.doCreateBond()
    }

    override fun removeBond(): Completable {
        return manager.doRemoveBond()
    }

    override fun read(characteristic: CharacteristicDetail.Read): Single<Change> {
        return manager.read(characteristic.characteristic, characteristic.gatt)
    }

    override fun read(characteristicUUID: UUID): Single<Change> {
        return when (val result =
            discoveredDevice.findReadCharacteristic(characteristicUUID)) {
            is Either.Left -> Single.error(result.a)
            is Either.Right -> read(result.b)
        }
    }

    override fun write(data: Data, characteristic: CharacteristicDetail.Write): Single<Change> {
        return manager.write(data, characteristic.characteristic, characteristic.gatt)
    }

    override fun write(data: Data, characteristicUUID: UUID): Single<Change> {
        return when (val result = discoveredDevice.findWriteCharacteristic(characteristicUUID)) {
            is Either.Left -> Single.error(result.a)
            is Either.Right -> write(data, result.b)
        }
    }

    override fun toString(): String {
        return discoveredDevice.toString()
    }
}
