package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import arrow.core.Either
import arrow.core.Option
import arrow.core.k
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BleConnectionState
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.ConnectionError
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceDetail
import com.technocreatives.beckon.Property
import com.technocreatives.beckon.ReadDataException
import com.technocreatives.beckon.SubscribeDataException
import com.technocreatives.beckon.WriteDataException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.SingleSubject
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectRequest
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.DataSentCallback
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.util.UUID

// This one should be private and safe with Either
internal class BeckonBleManager(context: Context, val device: BluetoothDevice) :
        BleManager<BeckonManagerCallbacks>(context) {

    private var bluetoothGatt: BluetoothGatt? = null

    private val stateSubject: BehaviorSubject<ConnectionState> =
            BehaviorSubject.createDefault(ConnectionState.NotConnected)
    private val bondSubject: BehaviorSubject<BondState> =
            BehaviorSubject.createDefault(BondState.NotBonded)
    private val changeSubject = PublishSubject.create<Change>()
    private val devicesSubject by lazy { SingleSubject.create<Either<ConnectionError, DeviceDetail>>() }

    init {
        val onStateChange: (BleConnectionState) -> Unit = {
            val newState = processState(it, stateSubject.value!!)
            stateSubject.onNext(newState)
        }

        mCallbacks = BeckonManagerCallbacks(bondSubject, onStateChange)
    }

    private fun connect(request: ConnectRequest): Single<Either<ConnectionError, DeviceDetail>> {
        request.fail { device, status ->
            Timber.e("ConnectFailedException ${device.address} status: $status")
            devicesSubject.onSuccess(ConnectionError.ConnectFailed(device.address, status).left())
        }.enqueue()
        return devicesSubject.hide()
    }

    fun connect(): Single<Either<ConnectionError, DeviceDetail>> {
        val request = connect(device)
                .retry(3, 100)
                .useAutoConnect(true)
        return connect(request)
    }

    private val gattCallback = object : BleManagerGattCallback() {

        override fun initialize() {
            Timber.d("initialize")
            if (bluetoothGatt != null) {
                val services = bluetoothGatt!!.services.map { it.uuid }
                val characteristics = allCharacteristics(bluetoothGatt!!)
                devicesSubject.onSuccess(DeviceDetail(services, characteristics).right())
            } else {
                devicesSubject.onSuccess(ConnectionError.BluetoothGattNull(device.address).left())
            }
        }

        override fun onDeviceDisconnected() {
            Timber.d("onDeviceDisconnected gattCallback")
        }

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            Timber.d("isRequiredServiceSupported $gatt")
            val services = gatt.services.map { it.uuid }
            Timber.d("All services $services")
            bluetoothGatt = gatt

            return true
        }

        private fun allCharacteristics(gatt: BluetoothGatt): List<CharacteristicSuccess> {
            return gatt.services.flatMap { allCharacteristics(it) }
        }

        private fun allCharacteristics(service: BluetoothGattService): List<CharacteristicSuccess> {
            return service.characteristics.flatMap {
                allCharacteristics(service, it)
            }
        }

        fun allCharacteristics(
            service: BluetoothGattService,
            char: BluetoothGattCharacteristic
        ): List<CharacteristicSuccess> {
            return Property.values().toList().k()
                    .filterMap { findCharacteristic(service, char, it) }
        }

        private fun findCharacteristic(
            service: BluetoothGattService,
            char: BluetoothGattCharacteristic,
            type: Property
        ): Option<CharacteristicSuccess> {
            return when (type) {
                Property.WRITE -> writeCharacteristic(service, char)
                Property.READ -> readCharacteristic(service, char)
                Property.NOTIFY -> notifyCharacteristic(service, char)
            }
        }

        private fun notifyCharacteristic(
            service: BluetoothGattService,
            char: BluetoothGattCharacteristic
        ): Option<CharacteristicSuccess.Notify> {
            return if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0 &&
                    char.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0
            ) {
                Option.just(CharacteristicSuccess.Notify(char.uuid, service.uuid, char))
            } else {
                Option.empty()
            }
        }

        private fun readCharacteristic(
            service: BluetoothGattService,
            char: BluetoothGattCharacteristic
        ): Option<CharacteristicSuccess.Read> {
            return if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                Option.just(CharacteristicSuccess.Read(char.uuid, service.uuid, char))
            } else {
                Option.empty()
            }
        }

        private fun writeCharacteristic(
            service: BluetoothGattService,
            char: BluetoothGattCharacteristic
        ): Option<CharacteristicSuccess.Write> {
            return if (char.properties and BluetoothGattCharacteristic.PERMISSION_WRITE > 0) {
                Option.just(CharacteristicSuccess.Write(char.uuid, service.uuid, char))
            } else {
                Option.empty()
            }
        }
    }

    fun doCreateBond(): Completable {
        bondSubject.onNext(BondState.CreatingBond)
        return Completable.create { emitter ->
            createBond()
                    .done { emitter.onComplete() }
                    .fail { device, status -> emitter.onError(ConnectionError.CreateBondFailed(device.address, status).toException()) }
                    .enqueue()
        }
    }

    fun doRemoveBond(): Completable {
        bondSubject.onNext(BondState.RemovingBond)
        return Completable.create { emitter ->
            removeBond()
                    .done { emitter.onComplete() }
                    .fail { device, status -> emitter.onError(ConnectionError.RemoveBondFailed(device.address, status).toException()) }
                    .enqueue()
        }
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return gattCallback
    }

    fun connectionState(): Observable<ConnectionState> {
        return stateSubject.distinctUntilChanged().hide()
    }

    fun bondStates(): Observable<BondState> {
        return bondSubject.distinctUntilChanged().hide()
    }

    fun changes(): Observable<Change> {
        return changeSubject.hide()
    }

    fun currentState(): ConnectionState {
        return stateSubject.value!!
    }

    fun write(data: Data, uuid: UUID, gatt: BluetoothGattCharacteristic): Single<Change> {
        return Single.create { emitter ->
            val callback = DataSentCallback { device, data ->
                Timber.d("write DataSentCallback uuid: $uuid device: $device data: $data")
                emitter.onSuccess(Change(uuid, data))
            }
            writeCharacteristic(gatt, data)
                    .with(callback)
                    .fail { device, status ->
                        emitter.onError(WriteDataException(device.address, uuid, status))
                    }
                    .enqueue()
        }
    }

    fun read(uuid: UUID, gatt: BluetoothGattCharacteristic): Single<Change> {
        return Single.create { emitter ->
            val callback = DataReceivedCallback { device, data ->
                Timber.d("read DataReceivedCallback uuid: $uuid device: $device data: $data")
                emitter.onSuccess(Change(uuid, data))
            }
            readCharacteristic(gatt)
                    .with(callback)
                    .fail { device, status ->
                        emitter.onError(ReadDataException(device.address, uuid, status))
                    }
                    .enqueue()
        }
    }

    fun subscribe(notify: CharacteristicSuccess.Notify): Completable {
        return Completable.create { emitter ->
            Timber.d("setNotification callback $notify")
            val callback = DataReceivedCallback { device, data ->
                Timber.d("DataReceivedCallback $device $data")
                changeSubject.onNext(Change(notify.id, data))
            }
            readCharacteristic(notify.gatt).with(callback).enqueue()
            setNotificationCallback(notify.gatt).with(callback)
            enableNotifications(notify.gatt)
                    .fail { device, status ->
                        emitter.onError(
                                SubscribeDataException(
                                        device.address,
                                        notify.id,
                                        status
                                )
                        )
                    }
                    .done { emitter.onComplete() }
                    .enqueue()
        }
    }

    fun unsubscribe(notify: CharacteristicSuccess.Notify): Completable {
        return Completable.create { emitter ->
            disableNotifications(notify.gatt)
                    .fail { device, status ->
                        emitter.onError(
                                SubscribeDataException(
                                        device.address,
                                        notify.id,
                                        status
                                )
                        )
                    }
                    .done { emitter.onComplete() }
                    .enqueue()
        }
    }

    override fun log(priority: Int, message: String) {
        Timber.d(message)
    }

    override fun toString(): String {
        return "Device Address: ${device.address}, name: ${device.name} Connection state: ${stateSubject.value}, bond State: ${bondSubject.value}"
    }

    private fun processState(new: BleConnectionState, current: ConnectionState): ConnectionState {
        return when (new) {
            BleConnectionState.Disconnected, BleConnectionState.NotStarted, BleConnectionState.NotSupported, is BleConnectionState.Failed -> ConnectionState.NotConnected
            BleConnectionState.Disconnecting -> ConnectionState.Disconnecting
            BleConnectionState.Connecting -> ConnectionState.Connecting
            BleConnectionState.Connected, BleConnectionState.Ready -> ConnectionState.Connected
        }
    }
}