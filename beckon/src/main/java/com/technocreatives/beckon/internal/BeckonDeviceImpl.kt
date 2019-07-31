package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DisconnectDeviceFailedException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber

internal class BeckonDeviceImpl(
    private val bluetoothDevice: BluetoothDevice,
    private val manager: BeckonBleManager,
    private val deviceMetadata: DeviceMetadata
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
        Timber.d("disconnect $this")
        return Completable.create { emitter ->
            manager.disconnect()
                .done {
                    Timber.d("Disconnect success")
                    emitter.onComplete()
                }
                .fail { device, status -> emitter.onError(DisconnectDeviceFailedException(device.address, status)) }
                .enqueue()
        }
    }

    internal fun bluetoothDevice(): BluetoothDevice {
        return bluetoothDevice
    }

    override fun metadata(): DeviceMetadata {
        return deviceMetadata
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

    override fun read(characteristic: CharacteristicSuccess.Read): Single<Change> {
        return manager.read(characteristic.id, characteristic.gatt)
    }

    override fun write(data: Data, characteristic: CharacteristicSuccess.Write): Single<Change> {
        return manager.write(data, characteristic.id, characteristic.gatt)
    }

    override fun subscribe(notify: CharacteristicSuccess.Notify): Completable {
        return manager.subscribe(notify)
    }

    override fun subscribe(list: List<CharacteristicSuccess.Notify>): Completable {
        if (list.isEmpty()) return Completable.error(IllegalArgumentException("Empty notify list"))
        return Completable.merge(list.map { subscribe(it) })
    }

    override fun unsubscribe(notify: CharacteristicSuccess.Notify): Completable {
        return manager.unsubscribe(notify)
    }

    override fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Completable {
        if (list.isEmpty()) return Completable.error(IllegalArgumentException("Empty notify list"))
        return Completable.merge(list.map { unsubscribe(it) })
    }

    override fun toString(): String {
        return deviceMetadata.toString()
    }
}
