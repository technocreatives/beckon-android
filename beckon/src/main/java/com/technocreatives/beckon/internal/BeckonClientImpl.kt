package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.getSystemService
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicDetail
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DeviceNotFoundException
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.WritableDeviceMetadata
import com.technocreatives.beckon.data.DeviceRepository
import com.technocreatives.beckon.data.DeviceRepositoryImpl
import com.technocreatives.beckon.redux.Action
import com.technocreatives.beckon.redux.createBeckonStore
import com.technocreatives.beckon.util.disposedBy
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.util.UUID

internal class BeckonClientImpl(private val context: Context) : BeckonClient {

    private val beckonStore by lazy { createBeckonStore() }
    private val bluetoothManager by lazy { context.getSystemService<BluetoothManager>()!! }
    private val receiver by lazy { BluetoothAdapterReceiver(beckonStore) }
    private val scanner by lazy { Scanner() }
    private val deviceRepository: DeviceRepository by lazy { DeviceRepositoryImpl(context) }

    private val bag = CompositeDisposable()

    override fun startScan(setting: ScannerSetting) {
        scanner.scan(setting)
    }

    override fun stopScan() {
        if (bluetoothManager.adapter.isEnabled) {
            scanner.stopScan()
        } else {
            // TODO Callback to application? Notify failure
            Timber.e("Stopped scan but adapter is not turned on!")
        }
    }

    override fun disconnectAllConnectedButNotSavedDevices() {
        beckonStore.currentState().connected.forEach {
            it.disconnect()
        }
    }

    override fun disconnectAllExcept(addresses: List<String>) {
        beckonStore.currentState().connected.filter {
            it.metadata().macAddress !in addresses
        }.forEach {
            it.disconnect()
        }
    }

    override fun scan(): Observable<BeckonScanResult> {
        return scanner.scan()
    }

    override fun scanAndConnect(characteristics: List<Characteristic>): Observable<DeviceMetadata> {
        return scan()
                .distinct { it.macAddress }
                .flatMapSingle { connect(it, characteristics) }
    }

    override fun findDevice(macAddress: String): Single<BeckonDevice> {
        Timber.d("findDevice $macAddress in ${beckonStore.currentState()}")
        return when (val device = beckonStore.currentState().findDevice(macAddress)) {
            is None -> Single.error(DeviceNotFoundException(macAddress))
            is Some -> Single.just(device.t)
        }
    }

    override fun devices(): Observable<List<DeviceMetadata>> {
        return beckonStore.states().map { it.allDevices().map { it.metadata() } }
    }

    override fun savedDevices(): Observable<List<DeviceMetadata>> {
        return beckonStore.states().map { it.saved.map { it.metadata() } }
    }

    override fun currentDevices(): List<DeviceMetadata> {
        return beckonStore.currentState().saved.map { it.metadata() }
    }

    override fun connectedDevices(): Observable<List<DeviceMetadata>> {
        return beckonStore.states().map { it.connected.map { it.metadata() } }
    }

    /*
    *  Connect to a scanned device and then verify if all characteristics work
    * */

    override fun connect(
        result: BeckonScanResult,
        characteristics: List<Characteristic>
    ): Single<DeviceMetadata> {
        Timber.d("Connect $result")

        val manager = BeckonBleManager(context, characteristics)
        val request = manager.connect(result.device)
                .retry(3, 100)
                .useAutoConnect(true)

        return manager.connect(request)
                .map { DeviceMetadata(result.device.address, result.device.name, it) }
                .map {
                    val beckonDevice = BeckonDeviceImpl(result.device, manager, it)
                    beckonStore.dispatch(Action.AddConnectedDevice(beckonDevice))
                    it
                }
    }

    override fun disconnect(macAddress: String): Boolean {
        // disconnect a saved device or discovered device??
        return beckonStore.currentState()
                .findDevice(macAddress)
                .map { it.disconnect() }
                .nonEmpty()
    }

    override fun save(macAddress: String): Completable {
        return when (val device = beckonStore.currentState().findDevice(macAddress)) {
            is None -> Completable.error(DeviceNotFoundException(macAddress))
            is Some -> createBond(device.t).andThen(saveDevice(device.t))
        }
    }

    /***
     * Create Bond for a device
     */
    private fun createBond(device: BeckonDevice): Completable {
        // return Completable.complete()
        return device.createBond()
    }

    /**
     * Remove Bond and disconnect device
     */
    private fun removeBond(device: BeckonDevice): Completable {
        return device.removeBond()
    }

    private fun saveDevice(device: BeckonDevice): Completable {
        beckonStore.dispatch(Action.AddSavedDevice(device))
        return deviceRepository.addDevice(device.metadata().writableDeviceMetadata()).ignoreElement()
    }

    private fun removeSavedDevice(device: BeckonDevice): Completable {
        beckonStore.dispatch(Action.RemoveSavedDevice(device))
        return deviceRepository.removeDevice(device.metadata().macAddress).ignoreElement()
    }

    override fun remove(macAddress: String): Completable {
        return when (val device = beckonStore.currentState().findSavedDevice(macAddress)) {
            is None -> Completable.error(DeviceNotFoundException(macAddress))
            is Some -> removeBond(device.t).andThen(removeSavedDevice(device.t))
        }
    }

    override fun register(context: Context) {
        receiver.register(context)

        beckonStore.states().subscribe {
            Timber.d("State $it")
        }.disposedBy(bag)

        beckonStore.states()
                .map { it.saved }
                .doOnNext { Timber.d("All saved devices $it") }
                .map { it.map { it.currentState() } }
                .distinctUntilChanged()
                .subscribe {
                    // process devices states
                    Timber.d("State of saved devices $it")
                }.disposedBy(bag)

        beckonStore.states()
                .map { it.connected }
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
        receiver.unregister(context)
        // todo disconnect all devices???
        bag.clear()
    }

    override fun bluetoothState(): Observable<BluetoothState> {
        return beckonStore.states().map { it.bluetoothState }.distinctUntilChanged()
    }

    override fun write(macAddress: MacAddress, characteristic: CharacteristicDetail.Write, data: Data): Single<Change> {
        return findDevice(macAddress).flatMap { it.write(data, characteristic) }
    }

    override fun write(macAddress: MacAddress, characteristicUuid: UUID, data: Data): Single<Change> {
        return findDevice(macAddress).flatMap { it.write(data, characteristicUuid) }
    }

    override fun read(macAddress: MacAddress, characteristic: CharacteristicDetail.Read): Single<Change> {
        return findDevice(macAddress).flatMap { it.read(characteristic) }
    }
}
