package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.getSystemService
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.toOption
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.BluetoothDeviceNotFoundException
import com.technocreatives.beckon.BondFailureException
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.DeviceNotFoundException
import com.technocreatives.beckon.DiscoveredDevice
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.data.DeviceRepositoryImpl
import com.technocreatives.beckon.redux.AddDiscoveredDevice
import com.technocreatives.beckon.redux.AddSavedDevice
import com.technocreatives.beckon.redux.RemoveSavedDevice
import com.technocreatives.beckon.redux.createBeckonStore
import com.technocreatives.beckon.util.debugInfo
import com.technocreatives.beckon.util.disposedBy
import com.technocreatives.beckon.util.findDevice
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber

internal class BeckonClientImpl(private val context: Context) : BeckonClient {

    private val beckonStore by lazy { createBeckonStore() }
    private val bluetoothManager by lazy { context.getSystemService<BluetoothManager>()!! }
    private val receiver by lazy { BluetoothAdapterReceiver(beckonStore) }
    private val scanner by lazy { Scanner() }
    private val deviceRepository by lazy { DeviceRepositoryImpl(context) }

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

    override fun disconnectAllConnectedDevicesButNotSavedDevices() {
        beckonStore.currentState().discovered.forEach {
            it.disconnect()
        }
    }

    override fun scan(): Observable<BeckonScanResult> {
        return scanner.scan()
    }

    override fun scanAndConnect(characteristics: List<Characteristic>): Observable<DiscoveredDevice> {
        return scan().distinct { it.macAddress }
                .flatMap { connect(it, characteristics) }
    }

    override fun findDevice(macAddress: String): Observable<BeckonDevice> {
        Timber.d("findDevice $macAddress")
        return beckonStore.states()
                .take(1)
                .map { it.findDevice(macAddress) }
                .doOnNext { Timber.d("findDevice $macAddress $it") }
                .filter { it.nonEmpty() }
                .map { it.orNull()!! }
    }

    override fun devices(): Observable<List<DeviceMetadata>> {
        return beckonStore.states().map { it.saved.map { it.metadata() } }
    }

    override fun currentDevices(): List<DeviceMetadata> {
        return beckonStore.currentState().saved.map { it.metadata() }
    }

    override fun connect(
        result: BeckonScanResult,
        characteristics: List<Characteristic>
    ): Observable<DiscoveredDevice> {
        Timber.d("Connect $result")
        Timber.d("Add device to map: ${result.device.debugInfo()}")
        val beckonDevice = BeckonDeviceImpl.create(context, result.device, characteristics)
        beckonStore.dispatch(AddDiscoveredDevice(beckonDevice))
        return beckonDevice.connect()
    }

    override fun disconnect(macAddress: String): Boolean {
        // disconnect a saved device or discovered device??
        return beckonStore.currentState()
                .findDevice(macAddress)
                .map { it.disconnect() }
                .nonEmpty()
    }

    override fun save(macAddress: String): Observable<Unit> {
        return when (val device = beckonStore.currentState().findDevice(macAddress)) {
            is None -> Observable.error<Unit>(DeviceNotFoundException(macAddress))
            is Some -> createBond(device.t).publish { shared ->
                Observable.merge(
                        shared.filter { it is BondState.Bonded }
                                .flatMap { saveDevice(device.t) },
                        shared.filter { it is BondState.BondingFailed }
                                .flatMap { Observable.error<Unit>(BondFailureException(macAddress)) }
                )
            }
        }
    }

    private fun createBond(device: BeckonDevice): Observable<BondState> {
        // todo check if a device is bonded if not try to create bond
        device.createBond()
        // TODO temporary fix for working with non bond devices
        // return device.bondStates()
        return Observable.just(BondState.Bonded)
    }

    private fun saveDevice(device: BeckonDevice): Observable<Unit> {
        beckonStore.dispatch(AddSavedDevice(device))
        return deviceRepository.addDevice(device.metadata()).map { Unit }
    }

    override fun remove(macAddress: String): Observable<Unit> {
        beckonStore.currentState().findDevice(macAddress)
                .map {
                    it.disconnect()
                    beckonStore.dispatch(RemoveSavedDevice(it))
                }

        return deviceRepository.removeDevice(macAddress).map { Unit }
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
                .map { it.discovered }
                .doOnNext { Timber.d("All discovered devices $it") }
                .distinctUntilChanged()
                .subscribe {
                    Timber.d("State of discovered devices $it")
                }.disposedBy(bag)

        beckonStore.states()
                .map { it.bluetoothState }
                .distinctUntilChanged()
                .subscribe {
                    if (it == BluetoothState.ON) {
//                        onBluetoothTurnedOn()
                    }
                }.disposedBy(bag)

        deviceRepository.devices()
                .subscribe {
                    reconnectSavedDevices(it)
                }.disposedBy(bag)
    }

    private fun reconnectSavedDevices(devices: List<DeviceMetadata>) {
        devices.forEach {
            connect(it)
        }
    }

    private fun connect(metadata: DeviceMetadata): Observable<DiscoveredDevice> {
        return when (val device = findBeckonDevice(metadata)) {
            is None -> Observable.error<DiscoveredDevice>(BluetoothDeviceNotFoundException(metadata.macAddress))
            is Some -> {
                beckonStore.dispatch(AddSavedDevice(device.t))
                device.t.connect()
            }
        }
    }

    private fun findBeckonDevice(metadata: DeviceMetadata): Option<BeckonDevice> {
        return bluetoothManager.findDevice(metadata.macAddress)
                .toOption()
                .map { BeckonDeviceImpl(context, metadata, it) }
    }

    override fun unregister(context: Context) {
        receiver.unregister(context)
        // todo disconnect all devices???
        bag.clear()
    }

    override fun bluetoothState(): Observable<BluetoothState> {
        return beckonStore.states().map { it.bluetoothState }.distinctUntilChanged()
    }
}
