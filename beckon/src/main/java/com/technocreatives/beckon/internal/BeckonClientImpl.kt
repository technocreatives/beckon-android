package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.getSystemService
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.DeviceInfo
import com.technocreatives.beckon.DiscoveredDevice
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.redux.AddDiscoveredDevice
import com.technocreatives.beckon.redux.AddSavedDevice
import com.technocreatives.beckon.redux.RemoveSavedDevice
import com.technocreatives.beckon.redux.createBeckonStore
import com.technocreatives.beckon.util.debugInfo
import com.technocreatives.beckon.util.disposedBy
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber

internal class BeckonClientImpl(private val context: Context) : BeckonClient {
    private val beckonStore by lazy { createBeckonStore() }
    private val bluetoothManager by lazy { context.getSystemService<BluetoothManager>()!! }
    private val receiver by lazy { BluetoothAdapterReceiver(beckonStore) }
    private val scanner by lazy { Scanner() }

    private val bag = CompositeDisposable()

    override fun scan(setting: ScannerSetting): Observable<BeckonScanResult> {
        return scanner.scan(setting)
    }

    override fun scanList(setting: ScannerSetting): Observable<List<BeckonScanResult>> {
        return scanner.scanList(setting)
    }

    override fun scanAndConnect(descriptor: Descriptor): Observable<DiscoveredDevice> {
        return scan(descriptor.setting)
                .flatMap { connect(it, descriptor.characteristics) }
    }

    override fun findDevice(macAddress: String): Observable<BeckonDevice> {
        return beckonStore.states().map { it.findDevice(macAddress) }.filter { it.nonEmpty() }.map { it.orNull()!! }
    }

    override fun devices(): Observable<List<DeviceInfo>> {
        return beckonStore.states().map { it.saved.map { it.deviceInfo() } }
    }

    override fun getDevices(): List<BeckonDevice> {
        return beckonStore.currentState().saved
    }

    override fun connect(result: BeckonScanResult, characteristics: List<Characteristic>): Observable<DiscoveredDevice> {
        Timber.d("Connect $result")
        Timber.d("Add device to map: ${result.device.debugInfo()}")
        val beckonDevice = BeckonDeviceImpl.create(context, result.device, characteristics)
        beckonStore.dispatch(AddDiscoveredDevice(beckonDevice))
        return beckonDevice.connect()
    }

    override fun disconnect(device: DeviceInfo): Boolean {
//        beckonStore.dispatch(RemoveDevice(device.deviceInfo()))
//        return device.disconnect()
        return beckonStore.currentState()
                .findDevice(device.macAddress)
                .map { it.disconnect() }
                .isEmpty()
    }

    override fun save(device: BeckonDevice): Observable<Boolean> {
        return Observable.fromCallable {
            beckonStore.dispatch(AddSavedDevice(device))
            beckonStore.currentState().findSavedDevice(device.deviceInfo().macAddress).isEmpty()
        }
    }

    override fun remove(device: BeckonDevice): Observable<Boolean> {
        return Observable.fromCallable {
            beckonStore.dispatch(RemoveSavedDevice(device))
            beckonStore.currentState().findSavedDevice(device.deviceInfo().macAddress).nonEmpty()
        }
    }

    override fun register(context: Context) {
        receiver.register(context)

        beckonStore.states().subscribe {
            Timber.d("State $it")
        }.disposedBy(bag)

        beckonStore.states()
                .map { it.bluetoothState }
                .distinctUntilChanged()
                .subscribe {
                    if (it == BluetoothState.ON) {
//                        onBluetoothTurnedOn()
                    }
                }.disposedBy(bag)
    }

    override fun unregister(context: Context) {
        receiver.unregister(context)
        bag.clear()
    }

    private fun onBluetoothTurnedOn() {
        Timber.d("onBluetoothTurnedOn")
//        val devices = connectedDevices.values
//        devices.forEach { device ->
//            Timber.d("try reconnect device: ${(device as BeckonDeviceImpl).bluetoothDevice().debugInfo()}")
//            val info = device.info
//            scan(settings(info))
//                    .doOnNext { Timber.d("Scan $it") }
//                    .take(1)
//                    .map { connect(it, info.characteristics) }
//                    .subscribe {
//                        Timber.d("reconnect success $it")
//                    }
//        }
    }

    private fun settings(device: DeviceInfo): ScannerSetting {
        val settings = ScanSettings.Builder()
                .setLegacy(false)
                .setUseHardwareFilteringIfSupported(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(false)
                .build()

        val filters = listOf(ScanFilter.Builder()
                .setDeviceAddress(device.macAddress).build())

        return ScannerSetting(settings, emptyList())
    }
}

//    override fun states(): Observable<List<DeviceChange>> = TODO("Need to be implemented later")
//    {
//        return devices().flatMapIterable { it }
//                .map { it to findDevice(it) }
//                .filter { it.second != null }
//    }

//    override fun saveDevices(devices: List<BeckonDevice>): Single<Boolean> = TODO("Need to be implemented later")