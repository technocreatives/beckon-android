// package com.technocreatives.beckon.internal
//
// import android.bluetooth.BluetoothDevice
// import android.content.Context
// import arrow.core.Either
// import arrow.core.None
// import arrow.core.Some
// import arrow.core.flatMap
// import arrow.core.left
// import arrow.core.right
// import com.lenguyenthanh.rxarrow.SingleZ
// import com.lenguyenthanh.rxarrow.fix
// import com.technocreatives.beckon.BeckonClientRx
// import com.technocreatives.beckon.BeckonDeviceRx
// import com.technocreatives.beckon.BeckonDeviceError
// import com.technocreatives.beckon.BeckonException
// import com.technocreatives.beckon.BluetoothState
// import com.technocreatives.beckon.Change
// import com.technocreatives.beckon.CharacteristicSuccess
// import com.technocreatives.beckon.ConnectionError
// import com.technocreatives.beckon.Descriptor
// import com.technocreatives.beckon.DeviceDetail
// import com.technocreatives.beckon.MacAddress
// import com.technocreatives.beckon.Metadata
// import com.technocreatives.beckon.SavedMetadata
// import com.technocreatives.beckon.ScanResult
// import com.technocreatives.beckon.ScannerSetting
// import com.technocreatives.beckon.checkRequirements
// import com.technocreatives.beckon.data.DeviceRepositoryRx
// import com.technocreatives.beckon.extension.subscribe
// import com.technocreatives.beckon.redux.BeckonAction
// import com.technocreatives.beckon.redux.BeckonStore
// import com.technocreatives.beckon.util.bluetoothManager
// import com.technocreatives.beckon.util.connectedDevices
// import com.technocreatives.beckon.util.disposedBy
// import com.technocreatives.beckon.util.findDevice
// import io.reactivex.Completable
// import io.reactivex.Observable
// import io.reactivex.Single
// import io.reactivex.disposables.CompositeDisposable
// import no.nordicsemi.android.ble.data.Data
// import timber.log.Timber
//
// internal class BeckonClientImplX(
//    private val context: Context,
//    private val beckonStore: BeckonStore,
//    private val deviceRepository: DeviceRepositoryRx,
//    private val bluetoothReceiver: Receiver,
//    private val scanner: Scanner
// ) : BeckonClientRx {
//
//    private val bag = CompositeDisposable()
//
//    override fun startScan(setting: ScannerSetting): Observable<ScanResult> {
//        val originalScanStream = scanner.startScan(setting)
//        return if (setting.useFilter) {
//            val connected = beckonStore.currentState().connectedDevices.map { it.metadata().macAddress }
//            val saved = deviceRepository.currentDevices().map { it.macAddress }
//            originalScanStream
//                .filter { it.device.address !in connected }
//                .filter { it.device.address !in saved }
//        } else {
//            originalScanStream
//        }
//    }
//
//    override fun stopScan() {
//        if (beckonStore.currentState().bluetoothState == BluetoothState.ON) {
//            scanner.stopScan()
//        } else {
//            // TODO Callback to application? Notify failure
//            Timber.e("Stopped scan but adapter is not turned on!")
//        }
//    }
//
//    override fun disconnectAllConnectedButNotSavedDevices(): Completable {
//        Timber.d("disconnectAllConnectedButNotSavedDevices is called")
//
//        val currentSavedDevices = deviceRepository.currentDevices().map { it.macAddress }
//
//        Timber.d("current saved devices: $currentSavedDevices")
//        Timber.d("current connected devices: ${beckonStore.currentState().connectedDevices}")
//
//        val completables = beckonStore.currentState().connectedDevices
//            .filter { it.metadata().macAddress !in currentSavedDevices }
//            .map { disconnect(it) }
//            .toTypedArray()
//
//        if (completables.isEmpty()) return Completable.complete()
//        return Completable.mergeArray(*completables)
//    }
//
//    override fun search(
//        setting: ScannerSetting,
//        descriptor: Descriptor
//    ): Observable<Either<ConnectionError, BeckonDeviceRx>> {
//        Timber.d("Search: $setting")
//        val connectedDevicesInSystem = context.bluetoothManager()
//            .connectedDevices()
//            .filter { device -> setting.filters.any { it.filter(device) } }
//
//        val connectedDevices = if (setting.useFilter) {
//            val connected = beckonStore.currentState().connectedDevices.map { it.metadata().macAddress }
//            val saved = deviceRepository.currentDevices().map { it.macAddress }
//            Timber.d("Search connected: $connected saved: $saved")
//            connectedDevicesInSystem
//                .filter { it.address !in connected }
//                .filter { it.address !in saved }
//        } else {
//            connectedDevicesInSystem
//        }
//
//        return Observable.fromArray(*connectedDevices.toTypedArray())
//            .flatMapSingle { connect(it, descriptor) }
//    }
//
//    override fun findConnectedDevice(macAddress: String): Single<BeckonDeviceRx> {
//        Timber.d("findConnectedDevice $macAddress in ${beckonStore.currentState()}")
//        return when (val device = beckonStore.currentState().findConnectedDevice(macAddress)) {
//            is None -> Single.error(ConnectionError.ConnectedDeviceNotFound(macAddress).toException())
//            is Some -> Single.just(device.value)
//        }
//    }
//
//    override fun findConnectedDeviceO(metadata: SavedMetadata): Observable<Either<BeckonDeviceError, BeckonDeviceRx>> {
//        Timber.d("findConnectedDeviceO ${metadata.macAddress} in ${beckonStore.currentState()}")
//        return beckonStore.states()
//            .distinctUntilChanged()
//            .map { state ->
//                Timber.d("State changed $state")
//                when (val device = state.findConnectedDevice(metadata.macAddress)) {
//                    is None -> {
//                        when (state.findConnectingDevice(metadata.macAddress)) {
//                            is None ->
//                                if (context.bluetoothManager().adapter.isEnabled &&
//                                    context.bluetoothManager().findDevice(metadata.macAddress) is None
//                                ) {
//                                    BeckonDeviceError.BondedDeviceNotFound(metadata).left()
//                                } else {
//                                    BeckonDeviceError.ConnectedDeviceNotFound(metadata).left()
//                                }
//                            is Some -> BeckonDeviceError.Connecting(metadata).left()
//                        }
//                    }
//                    is Some -> device.value.right()
//                }
//            }
//    }
//
//    override fun findSavedDevice(macAddress: MacAddress): Single<SavedMetadata> {
//        return deviceRepository.findDevice(macAddress).flatMap {
//            when (it) {
//                is None -> Single.error(BeckonDeviceError.SavedDeviceNotFound(macAddress).toException())
//                is Some -> Single.just(it.value)
//            }
//        }
//    }
//
//    override fun connectedDevices(): Observable<List<Metadata>> {
//        return beckonStore.states().map { it.connectedDevices.map { it.metadata() } }
//    }
//
//    override fun savedDevices(): Observable<List<SavedMetadata>> {
//        return deviceRepository.devices()
//    }
//
//    override fun connect(
//        result: ScanResult,
//        descriptor: Descriptor
//    ): Single<BeckonDeviceRx> {
//        Timber.d("Connect ScanResult: $result")
//        return connect(result.device, descriptor).fix { BeckonException(it) }
//    }
//
//    private fun connect(
//        device: BluetoothDevice,
//        descriptor: Descriptor
//    ): SingleZ<ConnectionError, BeckonDeviceRx> {
//        Timber.d("Connect BluetoothDevice: $device")
//
//        val manager = BeckonBleManager(context, device, descriptor)
//        val savedMetadata = SavedMetadata(device.address, device.name, descriptor)
//        beckonStore.dispatch(BeckonAction.AddConnectingDevice(savedMetadata))
//
//        return manager.connect()
//            .doOnSuccess { Timber.d("Connected to device success: $it") }
//            .map { either -> either.flatMap { checkRequirements(it, descriptor, device) } }
//            .map {
//                it.fold(
//                    { error ->
//                        if (error is ConnectionError.RequirementFailed) {
//                            manager.disconnect().enqueue()
//                        }
//                        beckonStore.dispatch(BeckonAction.RemoveConnectingDevice(savedMetadata))
//                        it as Either<ConnectionError, BeckonDeviceRx>
//                    },
//                    { metadata ->
//                        val beckonDevice = BeckonDeviceImplRx(device, manager, metadata)
//                        beckonStore.dispatch(BeckonAction.AddConnectedDevice(beckonDevice))
//                        beckonDevice.right()
//                    }
//                )
//            }
//    }
//
//    private fun subscribe(
//        beckonDevice: BeckonDeviceRx,
//        descriptor: Descriptor
//    ): Single<BeckonDeviceRx> {
//        Timber.d("Subscribe data after connected $beckonDevice")
//        return if (descriptor.subscribes.isEmpty()) {
//            Single.just(beckonDevice)
//        } else {
//            beckonDevice.subscribe(descriptor.subscribes).andThen(Single.just(beckonDevice))
//        }
//    }
//
//    private fun checkRequirements(
//        detail: DeviceDetail,
//        descriptor: Descriptor,
//        device: BluetoothDevice
//    ): Either<ConnectionError.RequirementFailed, Metadata> {
//        return checkRequirements(descriptor.requirements, detail.services, detail.characteristics)
//            .map {
//                Metadata(
//                    device.address ?: "No Address",
//                    device.name
//                        ?: "No name",
//                    detail.services, detail.characteristics, descriptor
//                )
//            }
//    }
//
//    override fun disconnect(macAddress: String): Completable {
//        return when (val device = beckonStore.currentState().findConnectedDevice(macAddress)) {
//            is None -> Completable.error(ConnectionError.ConnectedDeviceNotFound(macAddress).toException())
//            is Some -> disconnect(device.value)
//        }
//    }
//
//    /**
//     * Disconnect bluetooth and then remove that device from BeckonStore
//     * in case of error remove it any way
//     */
//    private fun disconnect(device: BeckonDeviceRx): Completable {
//        return device.disconnect()
//            .doFinally { beckonStore.dispatch(BeckonAction.RemoveConnectedDevice(device)) }
//    }
//
//    // exception can happen
//    override fun save(macAddress: String): Single<String> {
//        return when (val device = beckonStore.currentState().findConnectedDevice(macAddress)) {
//            is None -> Single.error(ConnectionError.ConnectedDeviceNotFound(macAddress).toException())
//            is Some -> createBond(device.value).andThen(saveDevice(device.value))
//        }
//    }
//
//    private fun createBond(device: BeckonDeviceRx): Completable {
//        return device.createBond()
//    }
//
//    private fun removeBond(device: BeckonDeviceRx): Completable {
//        return device.removeBond()
//    }
//
//    private fun saveDevice(device: BeckonDeviceRx): Single<MacAddress> {
//        return deviceRepository.addDevice(device.metadata().savedMetadata())
//            .map { device.metadata().macAddress }
//    }
//
//    private fun removeSavedDevice(macAddress: MacAddress): Single<MacAddress> {
//        return deviceRepository.removeDevice(macAddress)
//            .map { macAddress }
//    }
//
//    override fun remove(macAddress: String): Single<MacAddress> {
//
//        return beckonStore.currentState().findConnectedDevice(macAddress).fold(
//            {
//                removeSavedDevice(macAddress)
//            },
//            { device ->
//                removeSavedDevice(macAddress).flatMap { disconnect(device).toSingle { macAddress } }
//            }
//        )
//    }
//
//    override fun register(context: Context) {
//        bluetoothReceiver.register(context)
//
//        beckonStore.states()
//            .map { it.connectedDevices }
//            .doOnNext { Timber.d("All connected devices $it") }
//            .map { it.map { it.currentState() } }
//            .distinctUntilChanged()
//            .subscribe {
//                // process devices states
//                Timber.d("ConnectionSate of all BeckonDevices $it")
//            }.disposedBy(bag)
//
//        // do scan to check if bluetooth turn on from off state
//        beckonStore.states()
//            .map { it.bluetoothState }
//            .distinctUntilChanged()
//            .filter { it == BluetoothState.ON }
//            .map { deviceRepository.currentDevices() }
//            .subscribe { reconnectSavedDevices(it) }
//            .disposedBy(bag)
//
//        beckonStore.states()
//            .map { it.bluetoothState }
//            .distinctUntilChanged()
//            .filter { it == BluetoothState.OFF }
//            .subscribe {
//                beckonStore.currentState().connectedDevices.onEach {
//                    it.disconnect().subscribe(
//                        {
//                            Timber.d("Disconnect after BT_OFF success")
//                        },
//                        {
//                            Timber.w(it, "Disconnect after BT_OFF failed")
//                        }
//                    )
//                }
//                beckonStore.dispatch(BeckonAction.RemoveAllConnectedDevices)
//            }.disposedBy(bag)
//    }
//
//    private fun reconnectSavedDevices(devices: List<SavedMetadata>) {
//        Timber.d("reconnectSavedDevices $devices")
//        devices.forEach {
//            connect(it).subscribe(
//                { Timber.d("Reconnect Success $it") },
//                { Timber.e(it, "Reconnect Failed") }
//            ).disposedBy(bag)
//        }
//    }
//
//    // to do reimplement when we have a bondable device to test
//    override fun connect(metadata: SavedMetadata): Single<BeckonDeviceRx> {
//        Timber.d("connect SavedMetadata: $metadata")
//        return when (
//            val device =
//                context.bluetoothManager().findDevice(metadata.macAddress)
//        ) {
//            is Some -> connect(device.value, metadata.descriptor).fix { BeckonException(it) }
//            is None -> Single.error(BeckonDeviceError.BondedDeviceNotFound(metadata).toException())
//        }
//    }
//
//    override fun unregister(context: Context) {
//        bluetoothReceiver.unregister(context)
//        bag.clear()
//    }
//
//    override fun bluetoothState(): Observable<BluetoothState> {
//        return beckonStore.states().map { it.bluetoothState }.distinctUntilChanged()
//    }
//
//    override fun write(
//        macAddress: MacAddress,
//        characteristic: CharacteristicSuccess.Write,
//        data: Data
//    ): Single<Change> {
//        return findConnectedDevice(macAddress).flatMap { it.write(data, characteristic) }
//    }
//
//    override fun read(
//        macAddress: MacAddress,
//        characteristic: CharacteristicSuccess.Read
//    ): Single<Change> {
//        return findConnectedDevice(macAddress).flatMap { it.read(characteristic) }
//    }
// }
