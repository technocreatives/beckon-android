package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import arrow.core.*
import arrow.core.continuations.either
import arrow.fx.coroutines.parTraverseEither
import com.technocreatives.beckon.*
import com.technocreatives.beckon.redux.BeckonAction
import com.technocreatives.beckon.redux.BeckonStore
import com.technocreatives.beckon.util.toBondState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectRequest
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.DataSentCallback
import no.nordicsemi.android.ble.callback.MtuCallback
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val STATUS_TIME_OUT = -73
const val STATUS_FAILED_AFTER_CONNECT = -137
const val STATUS_INVALID_REQUEST = -138

// This one should be private and safe with Either
internal class BeckonBleManager(
    context: Context,
    private val beckonStore: BeckonStore,
    private val device: BluetoothDevice,
    private val descriptor: Descriptor
) : BleManager(context), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

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

    private val deviceConnectionEmitter =
        CompletableDeferred<Either<ConnectionError, DeviceDetail>>()

    private val states by lazy {
        changes()
            .scan(emptyMap()) { state: State, change -> state + change }
    }

    init {

        job.invokeOnCompletion { cause ->
            when (cause) {
                null -> {
                    Timber.w("BeckonBleManager: ${device.address} is completed normally")
                }
                is CancellationException -> {
                    Timber.w(cause, "BeckonBleManager: ${device.address} is cancelled normally")
                }
                else -> {
                    Timber.w(cause, "BeckonBleManager: ${device.address} is failed")
                }
            }
        }

        val onStateChange: (BleConnectionState) -> Unit = {
            val newState = processState(it, ConnectionState.NotConnected)
            if (newState == ConnectionState.NotConnected) {
                runBlocking {
                    beckonStore.dispatch(BeckonAction.RemoveConnectedDevice(device.address))
                }
            }
            runBlocking {
                Timber.d("Emit new connection state of ${device.address} $it")
                stateSubject.emit(newState)
            }
        }

        setConnectionObserver(BeckonConnectionObserver(onStateChange))
        setBondingObserver(BeckonBondingObserver(bondSubject))
    }

    fun states() = states

    private suspend fun connect(request: ConnectRequest): Either<ConnectionError, Unit> {
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                Timber.w("ConnectRequest: ${device.address} got cancelled")
                Timber.w("isActive: $isActive, isCompleted: $isCompleted")
                Timber.w("Cont isActive: ${cont.isActive}, isCompleted: ${cont.isCompleted}, isCancelled: ${cont.isCancelled}")

//                if (!isCompleted) {
//                    cont.resume(
//                        ConnectionError.BleConnectFailed(
//                            device.address,
//                            STATUS_TIME_OUT
//                        ).left()
//                    )
//                }

            }

            request
                .done {
                    Timber.w("isActive: $isActive, isCompleted: $isCompleted")
                    if (isActive) {
                        cont.resume(Unit.right())
                    }
                }
                .fail { device, status ->
                    Timber.e("ConnectionError ${device.address} status: $status")
                    Timber.w("isActive: $isActive, isCompleted: $isCompleted")
                    if (isActive) {
                        cont.resume(
                            ConnectionError.BleConnectFailed(
                                device.address,
                                status
                            ).left()
                        )
                    }
                }.enqueue()
            Timber.d("Connect ${request.device}")
        }

    }

    internal suspend fun disconnect(lol: Unit): Either<ConnectionError.DisconnectDeviceFailed, Unit> {
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                Timber.w("DisconnectRequest: ${device.address} got cancelled")
                Timber.w("isActive: $isActive, isCompleted: $isCompleted")
                Timber.w("Cont isActive: ${cont.isActive}, isCompleted: ${cont.isCompleted}, isCancelled: ${cont.isCancelled}")
            }
            val request = disconnect()
            request
                .done {
                    Timber.w("isActive: $isActive, isCompleted: $isCompleted")
                    if (isActive) {
                        cont.resume(Unit.right())
                    }
                }
                .fail { device, status ->
                    Timber.e("ConnectionError ${device.address} status: $status")
                    Timber.w("isActive: $isActive, isCompleted: $isCompleted")
                    if (isActive) {
                        cont.resume(
                            ConnectionError.DisconnectDeviceFailed(
                                device.address,
                                status
                            ).left()
                        )
                    }
                }.enqueue()
            Timber.d("disconnect ${device.address}")
        }

    }

    // TODO maybe disconnect here after timeout
    suspend fun connect(
        retryAttempts: Int = 3,
        retryDelay: Int = 100,
        autoConnect: Boolean = false,
        timeOut: Long = 60000L
    ): Either<ConnectionError, DeviceDetail> {
        Timber.d("SingleZ connect")
        val request = connect(device)
//            .timeout(30000)
            .retry(retryAttempts, retryDelay)
            .useAutoConnect(autoConnect)
        return withTimeout(
            timeOut,
            { connect(request) }) { ConnectionError.Timeout(device.address) }
            .flatMap { deviceConnectionEmitter.await() }
    }

    suspend fun applyActions(
        actions: List<BleAction>,
        detail: DeviceDetail
    ): Either<BeckonError, Unit> {
        return actions.parTraverseEither { applyAction(it, detail) }.map { }
    }

    private suspend fun applyAction(
        action: BleAction,
        detail: DeviceDetail
    ): Either<BeckonError, Unit> {
        return when (action) {
            is BleAction.Subscribe -> {
                subscribe(action.characteristic, detail)
            }
            is BleAction.Read -> {
                read(action.characteristic, detail)
            }
            is BleAction.RequestMTU -> {
                doRequestMtu(action.mtu.value).map { }
            }
            is BleAction.Write -> {
                // TODO fix
                Unit.right()
            }
            is BleAction.RequestMTUWithExpectation -> {
                doRequestMtu(action.mtu.value).map { }
            }
        }
    }

    // TODO figure out how to get mtu from device
    suspend fun doRequestMtu(mtu: Int): Either<MtuRequestError, Int> {
        return suspendCancellableCoroutine { cont ->
            val callback = MtuCallback { device, mtu ->
                cont.resume(mtu.right())
            }
            requestMtu(mtu)
                .with(callback)
                .fail { device, status ->
                    cont.resume(MtuRequestError(device.address, status).left())
                }
                .invalid {
                    cont.resume(
                        MtuRequestError(
                            device.address,
                            STATUS_INVALID_REQUEST
                        ).left()
                    )
                }
                .enqueue()
        }

    }

    // TODO figure out how to get mtu from device
    suspend fun doRequestMtu(mtu: Int, expected: Int): Either<MtuRequestError, Int> {
        val currentMtu = getMtu()
        Timber.d("Current Mtu = $currentMtu")
        if (getMtu() == expected) return expected.right()
        return suspendCancellableCoroutine { cont ->
            val callback = MtuCallback { device, mtu ->
                cont.resume(mtu.right())
            }
            requestMtu(mtu)
                .with(callback)
                .fail { device, status ->
                    cont.resume(MtuRequestError(device.address, status).left())
                }
                .invalid {
                    cont.resume(
                        MtuRequestError(
                            device.address,
                            STATUS_INVALID_REQUEST
                        ).left()
                    )
                }
                .enqueue()
        }

    }

    fun doOverrideMtu(mtu: Int) {
        overrideMtu(mtu)
    }

    fun mtu(): Int {
        return mtu
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
    fun getMaximumPacketSize(): Int {
        return mtu() - 3
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return object : BleManagerGattCallback() {


            // This function has to be done before enqueue works.
            override fun initialize() {
                Timber.d("initialize")
                if (bluetoothGatt != null) {

                    launch {
                        // TODO Add timeout error???
                        val delayTime = 1600L
                        delay(delayTime)
                        val services = bluetoothGatt!!.services.map { it.uuid }
                        val characteristics = allCharacteristics(bluetoothGatt!!)
                        val detail = DeviceDetail(services, characteristics)

                        either<BeckonError, Unit> {
                            // val delayTime = 0L
//                            if (descriptor.actionsOnConnected.isEmpty()) {
                            subscribe(descriptor.subscribes, detail).bind()
                            read(descriptor.reads, detail).bind()
//                            } else {
//                                applyActions(descriptor.actionsOnConnected, detail)
//                            }
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
                                Timber.d("Initialize Success: $detail ${this@BeckonBleManager.isConnected}")
                                if (this@BeckonBleManager.isConnected) {
                                    deviceConnectionEmitter.complete(detail.right())
                                } else {
                                    deviceConnectionEmitter.complete(
                                        ConnectionError.BleConnectFailed(
                                            device.address,
                                            STATUS_FAILED_AFTER_CONNECT
                                        ).left()
                                    )
                                }
                            }
                        )
                    }
                } else {
                    deviceConnectionEmitter.complete(
                        ConnectionError.BluetoothGattNull(device.address).left()
                    )
                }
            }

            private val MTU_SIZE_DEFAULT = 23

            override fun onDeviceDisconnected() {
                overrideMtu(MTU_SIZE_DEFAULT)
                Timber.d("onDeviceDisconnected gattCallback")
            }

            override fun onServicesInvalidated() {
                // todo recalculate DeviceDetail
                Timber.d("onServicesInvalidated gattCallback")
            }

            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                Timber.d("isRequiredServiceSupported $gatt")
                val services = gatt.services.map { it.uuid }
                Timber.d("All discovered services $services")
                bluetoothGatt = gatt
                // always return true because we'll check in initialize phrase
                return true
            }

            private fun allCharacteristics(gatt: BluetoothGatt): List<FoundCharacteristic> {
                return gatt.services.flatMap { allCharacteristics(it) }
            }

            private fun allCharacteristics(service: BluetoothGattService): List<FoundCharacteristic> {
                Timber.w("All characteristics of ${service.uuid}: ${service.characteristics.map { "${it.uuid}-${it.properties}" }}")
                return service.characteristics.flatMap {
                    allCharacteristics(service, it)
                }
            }

            private fun allCharacteristics(
                service: BluetoothGattService,
                char: BluetoothGattCharacteristic
            ): List<FoundCharacteristic> {
                return Property.values().toList()
                    .mapNotNull { findCharacteristic(service, char, it) }
            }

            private fun findCharacteristic(
                service: BluetoothGattService,
                char: BluetoothGattCharacteristic,
                type: Property
            ): FoundCharacteristic? {
                return when (type) {
                    Property.WRITE -> writeCharacteristic(service, char)
                    Property.READ -> readCharacteristic(service, char)
                    Property.NOTIFY -> notifyCharacteristic(service, char)
                }
            }

            private fun notifyCharacteristic(
                service: BluetoothGattService,
                char: BluetoothGattCharacteristic
            ): FoundCharacteristic.Notify? {
//                return if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0 &&
//                    char.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0
//                )
                return if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                    FoundCharacteristic.Notify(char.uuid, service.uuid, char)
                } else {
                    null
                }
            }

            private fun readCharacteristic(
                service: BluetoothGattService,
                char: BluetoothGattCharacteristic
            ): FoundCharacteristic.Read? {
                return if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                    FoundCharacteristic.Read(char.uuid, service.uuid, char)
                } else {
                    null
                }
            }

            private fun writeCharacteristic(
                service: BluetoothGattService,
                char: BluetoothGattCharacteristic
            ): FoundCharacteristic.Write? {
                Timber.w("char: ${char.uuid} with properties ${char.properties}")
                return if (char.properties.isWriteProperty()) {
                    FoundCharacteristic.Write(char.uuid, service.uuid, char)
                } else {
                    null
                }
            }

            private fun Int.isWriteProperty(): Boolean {
                val write = this and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                val writeNoResponse =
                    this and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
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

    // todo thinking about write type
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

    suspend fun writeSplit(
        pdu: ByteArray,
        uuid: UUID,
        gatt: BluetoothGattCharacteristic
    ): Either<WriteDataException, SplitPackage> {
        return suspendCoroutine { cont ->
            // This callback will be called each time the data were sent.
            val callback = DataSentCallback { device, data ->
                Timber.d("sendPdu DataSentCallback uuid: $uuid device: $device data: $data")
                runBlocking {
                    cont.resume(SplitPackage(uuid, getMaximumPacketSize(), data).right())
                }
            }

            // Write the right characteristic.
            writeCharacteristic(gatt, pdu)
                .split()
                .with(callback)
                .fail { device, status ->
                    runBlocking {
                        cont.resume((WriteDataException(device.address, uuid, status).left()))
                    }
                }
                .enqueue()
        }
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
            .invalid {
                result.complete(
                    ReadDataException(
                        device.address,
                        uuid,
                        STATUS_INVALID_REQUEST
                    ).left()
                )
            }
            .enqueue()
        return result.await()
    }

    suspend fun read(list: List<FoundCharacteristic.Read>): Either<ReadDataException, List<Change>> {
        return list.parTraverseEither { read(it.id, it.gatt) }
    }

    suspend fun subscribe(list: List<FoundCharacteristic.Notify>): Either<SubscribeDataException, Unit> {
        if (list.isEmpty()) return Unit.right()
        return list.parTraverseEither { subscribe(it) }.map { }
    }

    suspend fun subscribe(notify: FoundCharacteristic.Notify): Either<SubscribeDataException, Unit> {
        return subscribe(notify.id, notify.gatt)
    }

    // TODO Only read if readable
    suspend fun subscribe(
        uuid: UUID,
        gatt: BluetoothGattCharacteristic
    ): Either<SubscribeDataException, Unit> {
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                Timber.w("subscribe request: ${device.address} got cancelled")
                Timber.w("isActive: $isActive, isCompleted: $isCompleted")
                Timber.w("Cont isActive: ${cont.isActive}, isCompleted: ${cont.isCompleted}, isCancelled: ${cont.isCancelled}")
            }
            Timber.d("setNotification callback $uuid")
            val callback = DataReceivedCallback { device, data ->
                Timber.d("notify DataReceivedCallback $device $data")
                runBlocking {
                    changeSubject.emit(Change(uuid, data))
                }
            }

            setNotificationCallback(gatt).with(callback)
            enableNotifications(gatt)
                .invalid {
                    cont.resume(
                        SubscribeDataException(
                            device.address,
                            uuid,
                            STATUS_INVALID_REQUEST
                        ).left()
                    )
                }
                .fail { device, status ->
                    Timber.w("EnableNotification request failed: $device $status")
                    cont.resume(
                        SubscribeDataException(
                            device.address,
                            uuid,
                            status
                        ).left()
                    )
                }
                .done {
                    Timber.w("EnableNotification request success: $it")
                    cont.resume(Unit.right())
                }
                .enqueue()
//        readCharacteristic(gatt).with(readCallback)
//            .fail { device, status -> Timber.w("Read request failed: $device $status") }
//            .enqueue()
            Timber.d("end of setNotification callback $uuid")
        }
    }

    suspend fun unsubscribe(notify: FoundCharacteristic.Notify): Either<SubscribeDataException, Unit> {
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

//    override fun getMinLogPriority(): Int {
//        return Log.VERBOSE
//    }

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

    internal fun unregister() {
        Timber.w("BeckonBleManager unregister isActive: ${job.isActive}, isCompleted: ${job.isCompleted}, isCancelled: ${job.isCancelled}")
        if (job.isActive) {
            job.cancel()
        }
    }

}

suspend fun <E, T> withTimeout(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> Either<E, T>,
    error: () -> E
): Either<E, T> =
    withTimeoutOrNull(timeMillis, block) ?: error().left()

public val CoroutineScope.isCompleted: Boolean
    get() = coroutineContext[Job]?.isCompleted ?: true