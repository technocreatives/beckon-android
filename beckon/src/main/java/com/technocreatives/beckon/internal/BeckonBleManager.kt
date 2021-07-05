package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import arrow.core.*
import arrow.core.computations.either
import arrow.fx.coroutines.parTraverseEither
import com.technocreatives.beckon.*
import com.technocreatives.beckon.util.toBondState
import io.reactivex.subjects.SingleSubject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectRequest
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.DataSentCallback
import no.nordicsemi.android.ble.callback.MtuCallback
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.util.*
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
        MutableSharedFlow<ConnectionState>(1)
    }

    private val bondSubject by lazy {
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

        setConnectionObserver(BeckonConnectionObserver(onStateChange))
        setBondingObserver(BeckonBondingObserver(bondSubject))
    }

    fun states() = states

    private suspend fun connect(request: ConnectRequest): Either<ConnectionError, DeviceDetail> {
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

    suspend fun connect(
        retryAttempts: Int = 3,
        retryDelay: Int = 100
    ): Either<ConnectionError, DeviceDetail> {
        Timber.d("SingleZ connect")
        val request = connect(device)
            .retry(retryAttempts, retryDelay)
//            .useAutoConnect(true)
        return connect(request)
    }

    suspend fun applyActions(actions: List<BleAction>, detail: DeviceDetail): Either<BeckonError, Unit> {
        return actions.parTraverseEither { applyAction(it, detail) }.map { }
    }

    suspend fun applyAction(action: BleAction, detail: DeviceDetail): Either<BeckonError, Unit> {
        return when (action) {
            is BleAction.Subscribe -> {
                subscribe(action.characteristic, detail)
            }
            is BleAction.Read -> {
                read(action.characteristic, detail)
            }
            is BleAction.RequestMTU -> {
                doRequestMtu(action.mtu).map { }
            }
        }
    }

    // TODO figure out how to get mtu from device
    suspend fun doRequestMtu(mtu: Int): Either<MtuRequestException, Int> {
        val result = CompletableDeferred<Either<MtuRequestException, Int>>()
        val callback = MtuCallback { device, mtu ->
            result.complete(mtu.right())
        }
        requestMtu(mtu)
            .with(callback)
            .fail { device, status ->
                result.complete(MtuRequestException(device.address, status).left())
            }
            .invalid { result.complete(MtuRequestException(device.address, -1).left()) }
            .enqueue()
        return result.await()
    }

    suspend fun subscribe(
        subscribes: List<Characteristic>,
        detail: DeviceDetail
    ): Either<BeckonError, Unit> {
        return either {
            val list =
                checkNotifyList(subscribes, detail.services, detail.characteristics).bind()
            subscribe(list).bind()
        }
    }

    suspend fun subscribe(
        subscribes: Characteristic,
        detail: DeviceDetail
    ): Either<BeckonError, Unit> {
        return subscribe(listOf(subscribes), detail)
    }

    suspend fun read(
        reads: List<Characteristic>,
        detail: DeviceDetail
    ): Either<BeckonError, Unit> {
        return either {
            val list =
                checkReadList(reads, detail.services, detail.characteristics).bind()
            read(list).bind()
        }
    }
    suspend fun read(
        reads: Characteristic,
        detail: DeviceDetail
    ): Either<BeckonError, Unit> {
        return read(listOf(reads), detail)
    }

    suspend fun doCreateBond(): Either<ConnectionError.CreateBondFailed, Unit> {
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

    suspend fun doRemoveBond(): Either<ConnectionError.RemoveBondFailed, Unit> {
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

    // MTU specifies the maximum number of bytes that can be sent in a single write operation.
    // 3 bytes are used for internal purposes, so the maximum size is MTU-3
    //  todo get for Tyri light only
    fun getMaximumPacketSize(): Int {
        return 69 - 3 //mtu - 3
    }

    override fun getMtu(): Int {
        return super.getMtu()
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
                            // val delayTime = 0L
                            delay(delayTime)
                            if (descriptor.actionsOnConnected.isEmpty()) {
                                subscribe(descriptor.subscribes, detail).bind()
                                read(descriptor.reads, detail).bind()
                            } else {
                                applyActions(descriptor.actionsOnConnected, detail)
                            }
                        }.fold(
                            {
                                Timber.w("Initialize failed: $detail")
                                deviceConnectionEmitter.complete(
                                    ConnectionError.GeneralError(
                                        device.address,
                                        it.toException()
                                    ).left()
                                )
                            },
                            {
                                Timber.d("Initialize Success: $detail")
                                deviceConnectionEmitter.complete(detail.right())
                            }
                        )
                    }
                } else {
                    devicesSubject.onSuccess(
                        ConnectionError.BluetoothGattNull(device.address).left()
                    )
                }
            }
            private val MTU_SIZE_DEFAULT = 23
            private val MTU_SIZE_MAX = 517

            override fun onDeviceDisconnected() {
                overrideMtu(MTU_SIZE_DEFAULT)
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
                Timber.w("All characteristics of ${service.uuid}: ${service.characteristics.map {"${it.uuid}-${it.properties}"}}")
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
//                return if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0 &&
//                    char.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0
//                )
                return if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
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
                Timber.w("char: ${char.uuid} with properties ${char.properties}")
                return if (char.properties.isWriteProperty()) {
                    Option(CharacteristicSuccess.Write(char.uuid, service.uuid, char))
                } else {
                    None
                }
            }

            private fun Int.isWriteProperty(): Boolean {
                val write = this and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                val writeNoResponse = this and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                val writePermission = this and BluetoothGattCharacteristic.PERMISSION_WRITE != 0
//                Timber.w("write: $write || writeNoResponse: $writeNoResponse || writePermission: $writePermission")
                return (write || writeNoResponse || writePermission)
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

    suspend fun write(
        data: Data,
        uuid: UUID,
        gatt: BluetoothGattCharacteristic
    ): Either<WriteDataException, Change> {
        return suspendCoroutine { cont ->
            val callback = DataSentCallback { device, data ->
                Timber.d("write DataSentCallback uuid: $uuid device: $device data: $data")
                val change = Change(uuid, data)
                runBlocking {
                    changeSubject.emit(change)
                }
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

   fun sendPdu(pdu: ByteArray, uuid: UUID, gatt: BluetoothGattCharacteristic): Flow<Either<WriteDataException, PduPackage>> {
        val subject = MutableSharedFlow<Either<WriteDataException, PduPackage>>()
           // This callback will be called each time the data were sent.
           val callback = DataSentCallback { device, data ->
               Timber.d("sendPdu DataSentCallback uuid: $uuid device: $device data: $data")
               runBlocking {
                   subject.emit(PduPackage(uuid, getMaximumPacketSize(), data).right())
               }
           }

           // Write the right characteristic.
           writeCharacteristic(gatt, pdu)
               .split()
               .with(callback)
               .fail { device, status ->
                   runBlocking {
                       subject.emit((WriteDataException(device.address, uuid, status).left()))
                   }
               }
               .enqueue()
       return subject.asSharedFlow()
   }

    suspend fun read(
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

    suspend fun read(list: List<CharacteristicSuccess.Read>): Either<ReadDataException, List<Change>> {
        if (list.isEmpty()) return emptyList<Change>().right()
        return list.parTraverseEither { read(it.id, it.gatt) }
    }

    suspend fun subscribe(list: List<CharacteristicSuccess.Notify>): Either<SubscribeDataException, Unit> {
        if (list.isEmpty()) return Unit.right()
        return list.parTraverseEither { subscribe(it) }.map { }
    }

    suspend fun subscribe(notify: CharacteristicSuccess.Notify): Either<SubscribeDataException, Unit> {
        return subscribe(notify.id, notify.gatt)
    }

    // Only read if readable
    suspend fun subscribe(
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
//        val readCallback = DataReceivedCallback { device, data ->
//            Timber.d("Read DataReceivedCallback $device $data")
//            runBlocking {
//                changeSubject.emit(Change(uuid, data))
//            }
//        }
        setNotificationCallback(gatt).with(callback)
        enableNotifications(gatt)
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
//        readCharacteristic(gatt).with(readCallback)
//            .fail { device, status -> Timber.w("Read request failed: $device $status") }
//            .enqueue()
        Timber.d("end of setNotification callback $uuid")
        return result.await()
    }

    suspend fun unsubscribe(notify: CharacteristicSuccess.Notify): Either<SubscribeDataException, Unit> {
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
