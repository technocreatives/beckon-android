package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.content.Context
import arrow.core.Either
import arrow.core.None
import arrow.core.Some
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import com.lenguyenthanh.rxarrow.flatMapE
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonDeviceError
import com.technocreatives.beckon.BeckonException
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.ConnectionError
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.DeviceDetail
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.SavedMetadata
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.checkRequirements
import com.technocreatives.beckon.data.DeviceRepository
import com.technocreatives.beckon.extension.subscribe
import com.technocreatives.beckon.redux.BeckonAction
import com.technocreatives.beckon.redux.BeckonStore
import com.technocreatives.beckon.util.bluetoothManager
import com.technocreatives.beckon.util.disposedBy
import com.technocreatives.beckon.util.findDevice
import com.technocreatives.beckon.util.fix
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber

internal class BeckonClientImpl(
    private val context: Context,
    private val beckonStore: BeckonStore,
    private val deviceRepository: DeviceRepository,
    private val bluetoothReceiver: Receiver,
    private val scanner: Scanner
) : BeckonClient {

    private val bag = CompositeDisposable()

    override fun startScan(setting: ScannerSetting): Observable<ScanResult> {
        return scanner.startScan(setting)
    }

    override fun stopScan() {
        if (beckonStore.currentState().bluetoothState == BluetoothState.ON) {
            scanner.stopScan()
        } else {
            // TODO Callback to application? Notify failure
            Timber.e("Stopped scan but adapter is not turned on!")
        }
    }

    override fun disconnectAllConnectedButNotSavedDevices(): Completable {
        Timber.d("disconnectAllConnectedButNotSavedDevices")

        val currentSavedDevices = deviceRepository.currentDevices().map { it.macAddress }

        Timber.d("current saved devices: $currentSavedDevices")
        Timber.d("current connected devices: ${beckonStore.currentState().devices}")

        val completables = beckonStore.currentState().devices
                .filter { it.metadata().macAddress !in currentSavedDevices }
                .map { it.disconnect() }
                .toTypedArray()

        if (completables.isEmpty()) return Completable.complete()
        return Completable.mergeArray(*completables)
    }

    // override fun scan(): Observable<ScanResult> {
    //     return scanner.results()
    // }

    override fun findConnectedDevice(macAddress: String): Single<BeckonDevice> {
        Timber.d("findDevice $macAddress in ${beckonStore.currentState()}")
        return when (val device = beckonStore.currentState().findDevice(macAddress)) {
            is None -> Single.error(ConnectionError.ConnectedDeviceNotFound(macAddress).toException())
            is Some -> Single.just(device.t)
        }
    }

    override fun findConnectedDeviceO(metadata: SavedMetadata): Observable<Either<BeckonDeviceError.ConnectedDeviceNotFound, BeckonDevice>> {
        Timber.d("findDevice ${metadata.macAddress} in ${beckonStore.currentState()}")
        return beckonStore.states()
                .map { it.devices }
                .switchMap {
                    Timber.d("List Connected Devices Change $it")
                    when (val device =
                            it.find { it.metadata().macAddress == metadata.macAddress }.toOption()) {
                        is None -> Observable.just(BeckonDeviceError.ConnectedDeviceNotFound(metadata).left())
                        is Some -> Observable.just(device.t.right() as Either<BeckonDeviceError.ConnectedDeviceNotFound, BeckonDevice>)
                    }
                }
    }

    override fun findSavedDevice(macAddress: MacAddress): Single<SavedMetadata> {
        return deviceRepository.findDevice(macAddress).flatMap {
            when (it) {
                is None -> Single.error(BeckonDeviceError.SavedDeviceNotFound(macAddress).toException())
                is Some -> Single.just(it.t)
            }
        }
    }

    override fun connectedDevices(): Observable<List<Metadata>> {
        return beckonStore.states().map { it.devices.map { it.metadata() } }
    }

    override fun savedDevices(): Observable<List<SavedMetadata>> {
        return deviceRepository.devices()
    }

    /**
     *  Connect to a scanned device and then verify if all characteristics work
     *  @param ScanResult
     *  @param Descriptor
     *  @return BeckonDevice
     */

    override fun connect(
        result: ScanResult,
        descriptor: Descriptor
    ): Single<BeckonDevice> {
        Timber.d("Connect $result")

        val manager = BeckonBleManager(context, result.device)

        return manager.connect()
                .doOnSuccess { Timber.d("Connected device: $it") }
                .map { either -> either.flatMap { checkRequirements(it, descriptor, result.device) } }
                .flatMap {
                    when (it) {
                        is Either.Right -> {
                            val beckonDevice = BeckonDeviceImpl(result.device, manager, it.b)
                            beckonStore.dispatch(BeckonAction.AddConnectedDevice(beckonDevice))
                            Single.just(beckonDevice)
                        }
                        is Either.Left -> {
                            // todo better way to disconnect a device if it is not fulfil the requirement.
                            if (it.a is ConnectionError.RequirementFailed) {
                                manager.disconnect().enqueue()
                            }
                            Single.error(BeckonException(it.a))
                        }
                    }
                }.flatMap { subscribe(it, descriptor) } // take care of on error here
    }

    private fun subscribe(
        beckonDevice: BeckonDevice,
        descriptor: Descriptor
    ): Single<BeckonDevice> {
        return if (descriptor.subscribes.isEmpty()) {
            Single.just(beckonDevice)
        } else {
            beckonDevice.subscribe(descriptor.subscribes).andThen(Single.just(beckonDevice))
        }
    }

    private fun checkRequirements(
        detail: DeviceDetail,
        descriptor: Descriptor,
        device: BluetoothDevice
    ): Either<ConnectionError.RequirementFailed, Metadata> {
        return checkRequirements(descriptor.requirements, detail.services, detail.characteristics)
                .map {
                    Metadata(
                            device.address ?: "No Address", device.name
                            ?: "No name", detail.services, detail.characteristics, descriptor
                    )
                }
    }

    override fun disconnect(macAddress: String): Completable {
        return when (val device = beckonStore.currentState().findDevice(macAddress)) {
            is None -> Completable.error(ConnectionError.ConnectedDeviceNotFound(macAddress).toException())
            is Some -> device.t.disconnect()
        }
    }

    // error can happen
    override fun save(macAddress: String): Single<String> {
        return when (val device = beckonStore.currentState().findDevice(macAddress)) {
            is None -> Single.error(ConnectionError.ConnectedDeviceNotFound(macAddress).toException())
            is Some -> createBond(device.t).andThen(saveDevice(device.t))
        }
    }

    /***
     * Create Bond for a device
     */
    private fun createBond(device: BeckonDevice): Completable {
//        return Completable.complete()
        return device.createBond()
    }

    /**
     * Remove Bond and disconnect device
     */
    private fun removeBond(device: BeckonDevice): Completable {
        return device.removeBond()
    }

    private fun saveDevice(device: BeckonDevice): Single<MacAddress> {
        return deviceRepository.addDevice(device.metadata().savedMetadata())
                .map { device.metadata().macAddress }
    }

    private fun removeSavedDevice(device: BeckonDevice): Single<MacAddress> {
        return deviceRepository.removeDevice(device.metadata().macAddress)
                .map { device.metadata().macAddress }
    }

    override fun remove(macAddress: String): Single<MacAddress> {
        return beckonStore.currentState().findDevice(macAddress).fold({
            Single.error(BeckonDeviceError.SavedDeviceNotFound(macAddress).toException())
        }, { device ->
            device.disconnect().andThen(removeSavedDevice(device))
        })
    }

    override fun register(context: Context) {
        bluetoothReceiver.register(context)

        beckonStore.states()
                .map { it.devices }
                .doOnNext { Timber.d("All connected devices $it") }
                .map { it.map { it.currentState() } }
                .distinctUntilChanged()
                .subscribe {
                    // process devices states
                    Timber.d("ConnectionSate of all BeckonDevices $it")
                }.disposedBy(bag)

        // do scan to check if bluetooth turn on from off state
        beckonStore.states()
                .map { it.bluetoothState }
                .distinctUntilChanged()
                .filter { it == BluetoothState.ON }
                .map { deviceRepository.currentDevices() }
                .subscribe { reconnectSavedDevices(it) }
                .disposedBy(bag)

//        reconnectSavedDevices(deviceRepository.currentDevices())

        beckonStore.states()
                .map { it.bluetoothState }
                .distinctUntilChanged()
                .filter { it == BluetoothState.OFF }
                .subscribe {
                    beckonStore.currentState().devices.onEach {
                        it.disconnect().subscribe({
                            Timber.d("Disconnect after BT_OFF success")
                        }, {
                            Timber.w(it, "Disconnect after BT_OFF falied")
                        })
                    }
                    beckonStore.dispatch(BeckonAction.RemoveAllConnectedDevices)
                }.disposedBy(bag)
    }

    private fun reconnectSavedDevices(devices: List<SavedMetadata>) {
        Timber.d("reconnectSavedDevices")
        devices.forEach {
            connect(it).subscribe(
                    { Timber.d("Reconnect Success $it") },
                    { Timber.e(it, "Reconnect Failed") }
            ).disposedBy(bag)
        }
    }

    // to do reimplement when we have a bondable device to test
    override fun connect(metadata: SavedMetadata): Single<BeckonDevice> {
        return when (val device =
                context.bluetoothManager().findDevice(metadata.macAddress)) {
            is Some -> tryToReconnect(device.t, metadata.descriptor).fix()
            is None -> Single.error(BeckonDeviceError.BondedDeviceNotFound(metadata).toException())
        }
    }

    private fun tryToReconnect(
        device: BluetoothDevice,
        descriptor: Descriptor
    ): Single<Either<ConnectionError, BeckonDevice>> {
        Timber.d("tryToReconnect $device")
        val manager = BeckonBleManager(context, device)
        return manager.connect()
                .doOnSuccess { Timber.d("Connected device: $it") }
                .map { either -> either.flatMap { checkRequirements(it, descriptor, device) } }
                .flatMap {
                    when (it) {
                        is Either.Right -> {
                            val beckonDevice = BeckonDeviceImpl(device, manager, it.b)
                            beckonStore.dispatch(BeckonAction.AddConnectedDevice(beckonDevice))
                            Single.just(beckonDevice.right())
                        }
                        is Either.Left -> {
                            // todo better way to disconnect a device if it is not fulfil the requirement.
                            if (it.a is ConnectionError.RequirementFailed) {
                                manager.disconnect().enqueue()
                            }
                            Single.just(it)
                        }
                    }
                }.flatMapE { subscribe(it, descriptor) }
    }

    override fun unregister(context: Context) {
        bluetoothReceiver.unregister(context)
        // todo disconnect all devices???
        bag.clear()
    }

    override fun bluetoothState(): Observable<BluetoothState> {
        return beckonStore.states().map { it.bluetoothState }.distinctUntilChanged()
    }

    override fun write(
        macAddress: MacAddress,
        characteristic: CharacteristicSuccess.Write,
        data: Data
    ): Single<Change> {
        return findConnectedDevice(macAddress).flatMap { it.write(data, characteristic) }
    }

    override fun read(
        macAddress: MacAddress,
        characteristic: CharacteristicSuccess.Read
    ): Single<Change> {
        return findConnectedDevice(macAddress).flatMap { it.read(characteristic) }
    }
}