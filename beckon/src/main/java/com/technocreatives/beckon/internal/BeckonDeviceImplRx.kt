//package com.technocreatives.beckon.internal
//
//import android.bluetooth.BluetoothDevice
//import com.technocreatives.beckon.BeckonDeviceRx
//import com.technocreatives.beckon.BondState
//import com.technocreatives.beckon.Change
//import com.technocreatives.beckon.CharacteristicSuccess
//import com.technocreatives.beckon.ConnectionError
//import com.technocreatives.beckon.ConnectionState
//import com.technocreatives.beckon.Metadata
//import com.technocreatives.beckon.State
//import io.reactivex.Completable
//import io.reactivex.Observable
//import io.reactivex.Single
//import no.nordicsemi.android.ble.data.Data
//import timber.log.Timber
//
//internal class BeckonDeviceImplRx(
//    private val bluetoothDevice: BluetoothDevice,
//    private val manager: BeckonBleManager,
//    private val metadata: Metadata
//) : BeckonDeviceRx {
//
//    override fun connectionStates(): Observable<ConnectionState> {
//        return manager.connectionState()
//    }
//
//    override fun changes(): Observable<Change> {
//        return manager.changes()
//    }
//
//    override fun states(): Observable<State> {
//        return manager.states()
//    }
//
//    override fun currentState(): ConnectionState {
//        return manager.currentState()
//    }
//
//    // This may never complete if bluetooth system is messed up
//    override fun disconnect(): Completable {
//        Timber.d("disconnect ${metadata.macAddress}")
//        return Completable.create { emitter ->
//            manager.disconnect()
//                .done {
//                    Timber.d("Disconnect success ${metadata.macAddress}")
//                    emitter.onComplete()
//                }
//                .fail { device, status -> emitter.onError(ConnectionError.DisconnectDeviceFailed(device.address, status).toException()) }
//                .enqueue()
//        }
//    }
//
//    internal fun bluetoothDevice(): BluetoothDevice {
//        return bluetoothDevice
//    }
//
//    override fun metadata(): Metadata {
//        return metadata
//    }
//
//    override fun bondStates(): Observable<BondState> {
//        return manager.bondStates()
//    }
//
//    override fun createBond(): Completable {
//        return manager.doCreateBond()
//    }
//
//    override fun removeBond(): Completable {
//        return manager.doRemoveBond()
//    }
//
//    override fun read(characteristic: CharacteristicSuccess.Read): Single<Change> {
//        return manager.read(characteristic.id, characteristic.gatt)
//    }
//
//    override fun write(data: Data, characteristic: CharacteristicSuccess.Write): Single<Change> {
//        return manager.write(data, characteristic.id, characteristic.gatt)
//    }
//
//    override fun subscribe(notify: CharacteristicSuccess.Notify): Completable {
//        return manager.subscribe(notify)
//    }
//
//    override fun subscribe(list: List<CharacteristicSuccess.Notify>): Completable {
//        if (list.isEmpty()) return Completable.error(IllegalArgumentException("Empty notify list"))
//        return Completable.merge(list.map { subscribe(it) })
//    }
//
//    override fun unsubscribe(notify: CharacteristicSuccess.Notify): Completable {
//        return manager.unsubscribe(notify)
//    }
//
//    override fun unsubscribe(list: List<CharacteristicSuccess.Notify>): Completable {
//        if (list.isEmpty()) return Completable.error(IllegalArgumentException("Empty notify list"))
//        return Completable.merge(list.map { unsubscribe(it) })
//    }
//
//    override fun toString(): String {
//        return "BeckonDevice address: ${metadata.macAddress} connectionState: ${currentState()} bondState: ${manager.currentBondState()}"
//    }
//}
