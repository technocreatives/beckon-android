package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.computations.either
import arrow.core.filterOption
import arrow.core.left
import arrow.core.right
import arrow.fx.coroutines.parTraverseEither
import com.technocreatives.beckon.BeckonError
import com.technocreatives.beckon.BleConnectionState
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.ConnectionError
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.DeviceDetail
import com.technocreatives.beckon.Property
import com.technocreatives.beckon.ReadDataException
import com.technocreatives.beckon.State
import com.technocreatives.beckon.SubscribeDataException
import com.technocreatives.beckon.WriteDataException
import com.technocreatives.beckon.checkNotifyList
import com.technocreatives.beckon.checkReadList
import com.technocreatives.beckon.plus
import com.technocreatives.beckon.util.safe
import com.technocreatives.beckon.util.toBondState
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectRequest
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.DataSentCallback
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// This one should be private and safe with Either
internal class BeckonBleManager(
    context: Context,
    val device: BluetoothDevice,
    val descriptor: Descriptor
) : BleManager(context) {

    private var bluetoothGatt: BluetoothGatt? = null

    private val stateSubject by lazy {
        // BehaviorSubject.createDefault(ConnectionState.NotConnected)
        MutableSharedFlow<ConnectionState>(1)
    }

    private val bondSubject by lazy {
        // BehaviorSubject.createDefault(device.bondState.toBondState())
        // todo default value
        MutableSharedFlow<BondState>(1)
    }

    private val changeSubject by lazy {
        MutableSharedFlow<Change>(1)
    }

    private val devicesSubject by lazy { SingleSubject.create<Either<ConnectionError, DeviceDetail>>() }
    private val deviceConnectionEmitter =
        CompletableDeferred<Either<ConnectionError, DeviceDetail>>()

    private val states by lazy {
        changes()
            .scan(emptyMap()) { state: State, change -> state + change }
    }

    init {
        val onStateChange: (BleConnectionState) -> Unit = {
            val newState = processState(it, ConnectionState.NotConnected)
            runBlocking {
                stateSubject.emit(newState)
            }
        }

        // TODO
        setConnectionObserver(BeckonConnectionObserver(onStateChange))
        setBondingObserver(BeckonBondingObserver(bondSubject))
        // TODO Fix disposable
        // val statesDisposable = states.collect { Timber.d("New states of $device $it") }
    }

    fun states() = states

    private suspend fun connectS(request: ConnectRequest): Either<ConnectionError, DeviceDetail> {
        Timber.d("Connect ${request.device}")
        request
            .fail { device, status ->
                Timber.e("ConnectionError ${device.address} status: $status")
                deviceConnectionEmitter.complete(
                    ConnectionError.BleConnectFailed(
                        device.address,
                        status
                    ).left()
                )
            }.enqueue()
        return deviceConnectionEmitter.await()
    }

    // private fun connect(request: ConnectRequest): Single<Either<ConnectionError, DeviceDetail>> {
    //     Timber.d("Connect ${request.device}")
    //     request
    //         .fail { device, status ->
    //             Timber.e("ConnectionError ${device.address} status: $status")
    //             devicesSubject.onSuccess(
    //                 ConnectionError.BleConnectFailed(
    //                     device.address,
    //                     status
    //                 ).left()
    //             )
    //         }.enqueue()
    //     return devicesSubject.hide()
    // }

    suspend fun connectS(
        retryAttempts: Int = 3,
        retryDelay: Int = 100
    ): Either<ConnectionError, DeviceDetail> {
        Timber.d("SingleZ connect")
        val request = connect(device)
            .retry(retryAttempts, retryDelay)
            .useAutoConnect(true)
        return connectS(request)
    }

    // fun connect(
    //     retryAttempts: Int = 3,
    //     retryDelay: Int = 100
    // ): SingleZ<ConnectionError, DeviceDetail> {
    //     Timber.d("SingleZ connect")
    //     val request = connect(device)
    //         .retry(retryAttempts, retryDelay)
    //         .useAutoConnect(true)
    //     return connect(request)
    // }

    // fun subscribeBla(subscribes: List<Characteristic>, detail: DeviceDetail): Completable {
    //     return when (
    //         val list =
    //             checkNotifyList(subscribes, detail.services, detail.characteristics)
    //     ) {
    //         is Either.Left -> Completable.error(list.value.toException())
    //         is Either.Right -> subscribe(list.value)
    //     }
    // }

    suspend fun subscribeBlaS(
        subscribes: List<Characteristic>,
        detail: DeviceDetail
    ): Either<BeckonError, Unit> {
        return either {
            val list =
                checkNotifyList(subscribes, detail.services, detail.characteristics).bind()
            subscribeS(list).bind()
        }
    }
    //
    // fun readBla(reads: List<Characteristic>, detail: DeviceDetail): Observable<Change> {
    //     return when (
    //         val list =
    //             checkReadList(reads, detail.services, detail.characteristics)
    //     ) {
    //         is Either.Left -> {
    //             Observable.error(list.value.toException())
    //         }
    //         is Either.Right -> {
    //             read(list.value)
    //         }
    //     }
    // }

    suspend fun readBlaS(
        reads: List<Characteristic>,
        detail: DeviceDetail
    ): Either<BeckonError, Unit> {
        return either {
            val list =
                checkReadList(reads, detail.services, detail.characteristics).bind()
            readS(list).bind()
        }
    }

    suspend fun doCreateBondS(): Either<ConnectionError.CreateBondFailed, Unit> {
        val emitter = CompletableDeferred<Either<ConnectionError.CreateBondFailed, Unit>>()
        createBondInsecure()
            .done { emitter.complete(Unit.right()) }
            .fail { device, status ->
                emitter.complete(
                    ConnectionError.CreateBondFailed(
                        device.address,
                        status
                    ).left()
                )
            }
            .enqueue()
        bondSubject.tryEmit(BondState.CreatingBond)
        return emitter.await()
    }

    fun doCreateBond(): Completable {
        runBlocking {
            bondSubject.tryEmit(BondState.CreatingBond)
        }
        return Completable.create { emitter ->
            createBondInsecure()
                .done { emitter.onComplete() }
                .fail { device, status ->
                    emitter.onError(
                        ConnectionError.CreateBondFailed(
                            device.address,
                            status
                        ).toException()
                    )
                }
                .enqueue()
        }
    }

    suspend fun doRemoveBondS(): Either<ConnectionError.RemoveBondFailed, Unit> {
        val emitter = CompletableDeferred<Either<ConnectionError.RemoveBondFailed, Unit>>()
        removeBond()
            .done { emitter.complete(Unit.right()) }
            .fail { device, status ->
                emitter.complete(
                    ConnectionError.RemoveBondFailed(
                        device.address,
                        status
                    ).left()
                )
            }
            .enqueue()

        bondSubject.emit(BondState.RemovingBond)
        return emitter.await()
    }

    fun doRemoveBond(): Completable {
        runBlocking {
            bondSubject.tryEmit(BondState.CreatingBond)
        }
        return Completable.create { emitter ->
            removeBond()
                .done { emitter.onComplete() }
                .fail { device, status ->
                    emitter.onError(
                        ConnectionError.RemoveBondFailed(
                            device.address,
                            status
                        ).toException()
                    )
                }
                .enqueue()
        }
    }

    @DelicateCoroutinesApi
    override fun getGattCallback(): BleManagerGattCallback {
        return object : BleManagerGattCallback() {

            // This function has to be done before enqueue works.
            override fun initialize() {
                Timber.d("initialize")
                if (bluetoothGatt != null) {
                    val services = bluetoothGatt!!.services.map { it.uuid }
                    val characteristics = allCharacteristics(bluetoothGatt!!)
                    val detail = DeviceDetail(services, characteristics)
                    //
                    GlobalScope.launch {
                        // TODO Add timeout error???
                        either<BeckonError, Unit> {
                            val delayTime = 1600L
                            delay(delayTime)
                            Timber.d("$delayTime initialize")
                            subscribeBlaS(descriptor.subscribes, detail).bind()
                            Timber.d("subscribeS initialize")
                            readBlaS(descriptor.reads, detail).bind()
                            Timber.d("readS initialize")
                        }.fold({
                            Timber.w("Initialize failed: $detail")
                            deviceConnectionEmitter.complete(
                                ConnectionError.GeneralError(
                                    device.address,
                                    it.toException()
                                ).left()
                            )
                        }, {
                            Timber.d("Initialize Success: $detail")
                            deviceConnectionEmitter.complete(detail.right())
                        })
                    }

                    // TODO Fix disposable
                    // TODO wtf is 1600?
                    // val disposable =
                    //     Observable.just(Unit).delay(1600, TimeUnit.MILLISECONDS)
                    //         .flatMapCompletable {
                    //             subscribeBla(descriptor.subscribes, detail)
                    //         }
                    //         .observeOn(Schedulers.io())
                    //         .subscribeOn(Schedulers.io())
                    //         .andThen(readBla(descriptor.reads, detail).ignoreElements())
                    //         .subscribe(
                    //             {
                    //                 devicesSubject.onSuccess(detail.right())
                    //             },
                    //             {
                    //                 devicesSubject.onSuccess(
                    //                     ConnectionError.GeneralError(
                    //                         device.address,
                    //                         it
                    //                     ).left()
                    //                 )
                    //             }
                    //         )
                } else {
                    devicesSubject.onSuccess(
                        ConnectionError.BluetoothGattNull(device.address).left()
                    )
                }
            }

            override fun onDeviceDisconnected() {
                Timber.d("onDeviceDisconnected gattCallback")
            }

            override fun onServicesInvalidated() {
                Timber.d("onServicesInvalidated gattCallback")
            }

            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                Timber.d("isRequiredServiceSupported $gatt")
                val services = gatt.services.map { it.uuid }
                Timber.d("All discovered services $services")
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
                return Property.values().toList()
                    .map { findCharacteristic(service, char, it) }
                    .filterOption()
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
                    Option(CharacteristicSuccess.Notify(char.uuid, service.uuid, char))
                } else {
                    None
                }
            }

            private fun readCharacteristic(
                service: BluetoothGattService,
                char: BluetoothGattCharacteristic
            ): Option<CharacteristicSuccess.Read> {
                return if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                    Option(CharacteristicSuccess.Read(char.uuid, service.uuid, char))
                } else {
                    None
                }
            }

            private fun writeCharacteristic(
                service: BluetoothGattService,
                char: BluetoothGattCharacteristic
            ): Option<CharacteristicSuccess.Write> {
                return if (char.properties and BluetoothGattCharacteristic.PERMISSION_WRITE > 0) {
                    Option(CharacteristicSuccess.Write(char.uuid, service.uuid, char))
                } else {
                    None
                }
            }
        }
    }

    fun connectionState(): Flow<ConnectionState> {
        return stateSubject.distinctUntilChanged()
    }

    fun bondStates(): Flow<BondState> {
        return bondSubject.distinctUntilChanged()
    }

    fun changes(): Flow<Change> {
        return changeSubject.distinctUntilChanged()
    }

    // fun currentState(): ConnectionState {
    //     return currentState
    // }

    // fun currentBondState(): BondState {
        // return bondSubject.value!!
        // return device.bondState.toBondState()
    // }

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

    suspend fun writeS(
        data: Data,
        uuid: UUID,
        gatt: BluetoothGattCharacteristic
    ): Either<WriteDataException, Change> {
        return suspendCoroutine { cont ->
            val callback = DataSentCallback { device, data ->
                Timber.d("write DataSentCallback uuid: $uuid device: $device data: $data")
                val change = Change(uuid, data)
                // TODO should emit change here?
                // runBlocking {
                //     changeSubject.emit(change)
                // }
                cont.resume(change.right())
            }
            writeCharacteristic(gatt, data)
                .with(callback)
                .fail { device, status ->
                    cont.resume(WriteDataException(device.address, uuid, status).left())
                }
                .enqueue()
        }
    }

    fun read(uuid: UUID, gatt: BluetoothGattCharacteristic): Single<Change> {
        return Single.create { emitter ->
            val callback = DataReceivedCallback { device, data ->
                Timber.d("read DataReceivedCallback address: ${this.device.address} uuid: $uuid device: $device data: $data")
                val change = Change(uuid, data)
                // todo yes
                // changeSubject.emit(change)
                emitter.onSuccess(change)
            }
            readCharacteristic(gatt)
                .with(callback)
                .fail { device, status ->
                    emitter.onError(ReadDataException(device.address, uuid, status))
                }
                .enqueue()
        }
    }

    fun read(list: List<CharacteristicSuccess.Read>): Observable<Change> {
        if (list.isEmpty()) return Observable.empty()
        return Observable.merge(list.map { read(it.id, it.gatt).toObservable() })
    }

    suspend fun readS(
        uuid: UUID,
        gatt: BluetoothGattCharacteristic
    ): Either<ReadDataException, Change> {
        val result = CompletableDeferred<Either<ReadDataException, Change>>()
        val callback = DataReceivedCallback { device, data ->
            Timber.d("read DataReceivedCallback address: ${this.device.address} uuid: $uuid device: $device data: $data")
            val change = Change(uuid, data)
            runBlocking {
                Timber.d("ChangeSubject emit $change")
                changeSubject.emit(change)
            }
            result.complete(change.right())
        }
        readCharacteristic(gatt)
            .with(callback)
            .fail { device, status ->
                result.complete(ReadDataException(device.address, uuid, status).left())
            }
            .invalid { result.complete(ReadDataException(device.address, uuid, -1).left()) }
            .enqueue()
        return result.await()
    }

    suspend fun readS(list: List<CharacteristicSuccess.Read>): Either<ReadDataException, List<Change>> {
        if (list.isEmpty()) return emptyList<Change>().right()
        return list.parTraverseEither { readS(it.id, it.gatt) }
    }

    fun subscribe(list: List<CharacteristicSuccess.Notify>): Completable {
        if (list.isEmpty()) return Completable.complete()
        return Completable.merge(list.map { subscribe(it) })
    }

    fun subscribe(notify: CharacteristicSuccess.Notify): Completable {
        return subscribe(notify.id, notify.gatt)
    }

    fun subscribe(uuid: UUID, gatt: BluetoothGattCharacteristic): Completable {
        return Completable.create { emitter ->
            Timber.d("setNotification callback $uuid")
            val callback = DataReceivedCallback { device, data ->
                Timber.d("notify DataReceivedCallback $device $data")
                // changeSubject.emit(Change(uuid, data))
            }
            val readCallback = DataReceivedCallback { device, data ->
                Timber.d("Read DataReceivedCallback $device $data")
                // changeSubject.emit(Change(uuid, data))
            }
            setNotificationCallback(gatt).with(callback)
            enableNotifications(gatt)
                .fail { device, status ->
                    Timber.w("EnableNotification request failed: $device $status")
                    emitter.safe {
                        onError(
                            SubscribeDataException(
                                device.address,
                                uuid,
                                status
                            )
                        )
                    }
                }
                .done {
                    emitter.safe { onComplete() }
                }
                .enqueue()
            readCharacteristic(gatt).with(readCallback)
                .fail { device, status -> Timber.w("Read request failed: $device $status") }
                .enqueue()
        }
    }

    suspend fun subscribeS(list: List<CharacteristicSuccess.Notify>): Either<SubscribeDataException, Unit> {
        if (list.isEmpty()) return Unit.right()
        return list.parTraverseEither { subscribeS(it) }.map { }
    }

    suspend fun subscribeS(notify: CharacteristicSuccess.Notify): Either<SubscribeDataException, Unit> {
        return subscribeS(notify.id, notify.gatt)
    }

    // todo enqueue doesn't work for some reason.
    suspend fun subscribeS(
        uuid: UUID,
        gatt: BluetoothGattCharacteristic
    ): Either<SubscribeDataException, Unit> {
        val result = CompletableDeferred<Either<SubscribeDataException, Unit>>()
        Timber.d("setNotification callback $uuid")
        val callback = DataReceivedCallback { device, data ->
            Timber.d("notify DataReceivedCallback $device $data")
            runBlocking {
                changeSubject.emit(Change(uuid, data))
            }
        }
        val readCallback = DataReceivedCallback { device, data ->
            Timber.d("Read DataReceivedCallback $device $data")
            runBlocking {
                changeSubject.emit(Change(uuid, data))
            }
        }
        setNotificationCallback(gatt).with(callback)
        enableNotifications(gatt)
            .before {
                Timber.d("Before EnableNotification $uuid")
            }
            .invalid {
                result.complete(
                    SubscribeDataException(
                        device.address,
                        uuid,
                        -1
                    ).left()
                )
            }
            .fail { device, status ->
                Timber.w("EnableNotification request failed: $device $status")
                result.complete(
                    SubscribeDataException(
                        device.address,
                        uuid,
                        status
                    ).left()
                )
            }
            .done {
                Timber.w("EnableNotification request success: $it")
                result.complete(Unit.right())
            }
            .enqueue()
        readCharacteristic(gatt).with(readCallback)
            .fail { device, status -> Timber.w("Read request failed: $device $status") }
            .enqueue()
        Timber.d("end of setNotification callback $uuid")
        return result.await()
    }

    // fun unsubscribe(notify: CharacteristicSuccess.Notify): Completable {
    //     return Completable.create { emitter ->
    //         disableNotifications(notify.gatt)
    //             .fail { device, status ->
    //                 Timber.w("DisableNotification request failed: $device $status")
    //                 emitter.onError(
    //                     SubscribeDataException(
    //                         device.address,
    //                         notify.id,
    //                         status
    //                     )
    //                 )
    //             }
    //             .done { emitter.onComplete() }
    //             .enqueue()
    //     }
    // }

    suspend fun unsubscribeS(notify: CharacteristicSuccess.Notify): Either<SubscribeDataException, Unit> {
        return suspendCoroutine { emitter ->
            disableNotifications(notify.gatt)
                .fail { device, status ->
                    Timber.w("DisableNotification request failed: $device $status")
                    emitter.resume(
                        SubscribeDataException(
                            device.address,
                            notify.id,
                            status
                        ).left()
                    )
                }
                .done { emitter.resume(Unit.right()) }
                .enqueue()
        }
    }

    override fun log(priority: Int, message: String) {
        Timber.log(priority, "NRF logs: $message")
    }

    override fun toString(): String {
        return "Device Address: ${device.address}, name: ${device.name} Connection state: TODO, bond State: ${device.bondState.toBondState()}"
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
