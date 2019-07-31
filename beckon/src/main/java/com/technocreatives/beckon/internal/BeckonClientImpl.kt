package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothDevice
import android.content.Context
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.flatMap
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonResult
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.CharacteristicSuccess
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.DeviceDetail
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DeviceNotFoundException
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.RequirementFailedException
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.WritableDeviceMetadata
import com.technocreatives.beckon.checkRequirements
import com.technocreatives.beckon.data.DeviceRepository
import com.technocreatives.beckon.extension.subscribe
import com.technocreatives.beckon.redux.Action
import com.technocreatives.beckon.redux.Store
import com.technocreatives.beckon.util.disposedBy
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber

internal class BeckonClientImpl(
    private val context: Context,
    private val beckonStore: Store,
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
            is None -> Single.error(DeviceNotFoundException(macAddress))
            is Some -> Single.just(device.t)
        }
    }

    override fun findSavedDevice(macAddress: MacAddress): Single<WritableDeviceMetadata> {
        return deviceRepository.findDevice(macAddress).flatMap {
            when (it) {
                is None -> Single.error(DeviceNotFoundException(macAddress))
                is Some -> Single.just(it.t)
            }
        }
    }

    override fun connectedDevices(): Observable<List<DeviceMetadata>> {
        return beckonStore.states().map { it.devices.map { it.metadata() } }
    }

    override fun savedDevices(): Observable<List<WritableDeviceMetadata>> {
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

        val manager = BeckonBleManager(context)
        val request = manager.connect(result.device)
            .retry(3, 100)
            .useAutoConnect(true)

        return manager.connect(request)
            .map { either -> either.flatMap { checkRequirements(it, descriptor, result.device) } }
            .flatMap {
                when (it) {
                    is Either.Right -> {
                        val beckonDevice = BeckonDeviceImpl(result.device, manager, it.b)
                        beckonStore.dispatch(Action.AddConnectedDevice(beckonDevice))
                        Single.just(beckonDevice)
                    }
                    is Either.Left -> {
                        // todo better way to disconnect a device if it is not fulfil the requirement.
                        if (it.a is RequirementFailedException) {
                            manager.disconnect().enqueue()
                        }
                        Single.error(it.a)
                    }
                }
            }.flatMap { subscribe(it, descriptor) }
    }

    private fun subscribe(beckonDevice: BeckonDevice, descriptor: Descriptor): Single<BeckonDevice> {
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
    ): BeckonResult<DeviceMetadata> {
        return checkRequirements(descriptor.requirements, detail.services, detail.characteristics)
            .map { DeviceMetadata(device.address, device.name, detail.services, detail.characteristics, descriptor) }
    }

    override fun disconnect(macAddress: String): Completable {
        return when (val device = beckonStore.currentState().findDevice(macAddress)) {
            is None -> Completable.error(DeviceNotFoundException(macAddress))
            is Some -> device.t.disconnect()
        }
    }

    // error can happen
    override fun save(macAddress: String): Single<String> {
        return when (val device = beckonStore.currentState().findDevice(macAddress)) {
            is None -> Single.error(DeviceNotFoundException(macAddress))
            is Some -> createBond(device.t).andThen(saveDevice(device.t))
        }
    }

    /***
     * Create Bond for a device
     */
    private fun createBond(device: BeckonDevice): Completable {
        return Completable.complete()
        // return device.createBond()
    }

    /**
     * Remove Bond and disconnect device
     */
    private fun removeBond(device: BeckonDevice): Completable {
        return device.removeBond()
    }

    private fun saveDevice(device: BeckonDevice): Single<MacAddress> {
        return deviceRepository.addDevice(device.metadata().writableDeviceMetadata())
            .map { device.metadata().macAddress }
    }

    private fun removeSavedDevice(device: BeckonDevice): Single<MacAddress> {
        return deviceRepository.removeDevice(device.metadata().macAddress).map { device.metadata().macAddress }
    }

    override fun remove(macAddress: String): Single<MacAddress> {
        return beckonStore.currentState().findDevice(macAddress).fold({
            Single.error(DeviceNotFoundException(macAddress))
        }, { device ->
            device.disconnect().andThen(removeSavedDevice(device))
        })
    }

    override fun register(context: Context) {
        bluetoothReceiver.register(context)

        beckonStore.states().subscribe {
            Timber.d("State $it")
        }.disposedBy(bag)

        beckonStore.states()
            .map { it.devices }
            .doOnNext { Timber.d("All saved devices $it") }
            .map { it.map { it.currentState() } }
            .distinctUntilChanged()
            .subscribe {
                // process devices states
                Timber.d("State of Beckon devices $it")
            }.disposedBy(bag)

        beckonStore.states()
            .map { it.devices }
            .doOnNext { Timber.d("All discovered devices $it") }
            .distinctUntilChanged()
            .subscribe {
                Timber.d("State of discovered devices $it")
            }.disposedBy(bag)

        // do scan to check if bluetooth turn on from off state
        beckonStore.states()
            .map { it.bluetoothState }
            .distinctUntilChanged()
            .filter { it == BluetoothState.ON }
            .switchMap { deviceRepository.devices().take(1) }
            .subscribe {
                reconnectSavedDevices(it)
            }.disposedBy(bag)
    }

    private fun reconnectSavedDevices(devices: List<WritableDeviceMetadata>) {
        devices.forEach {
            connect(it)
        }
    }

    // to do reimplement when we have a bondable device to test
    private fun connect(metadata: WritableDeviceMetadata): Observable<DeviceMetadata> {
        return Observable.empty()
//        return when (val device = findBeckonDevice(metadata)) {
//            is None -> Observable.error(BluetoothDeviceNotFoundException(metadata.macAddress))
//            is Some -> {
//                beckonStore.dispatch(AddSavedDevice(device.t))
//                device.t.connect()
//            }
//        }
    }

    // to do reimplement when we have a bondable device to test
    private fun findBeckonDevice(metadata: WritableDeviceMetadata): Option<BeckonDevice> {
        return Option.empty()
//        return bluetoothManager.findDevice(metadata.macAddress)
//                .map { BeckonDeviceImpl(context, metadata, it) }
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

    override fun read(macAddress: MacAddress, characteristic: CharacteristicSuccess.Read): Single<Change> {
        return findConnectedDevice(macAddress).flatMap { it.read(characteristic) }
    }
}