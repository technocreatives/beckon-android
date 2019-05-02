package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.getSystemService
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceInfo
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.disposedBy
import com.technocreatives.beckon.redux.AddDevice
import com.technocreatives.beckon.redux.RemoveDevice
import com.technocreatives.beckon.redux.createBeckonStore
import com.technocreatives.beckon.util.debugInfo
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber

internal class BeckonClientImpl(private val context: Context) : BeckonClient {

    private val connectedDevices: MutableMap<MacAddress, BeckonDevice> = HashMap()
    private val beckonStore by lazy { createBeckonStore() }
    private val bluetoothManager by lazy { context.getSystemService<BluetoothManager>()!! }
    private val receiver by lazy { BluetoothAdapterReceiver(beckonStore) }
    private val scanner by lazy { BluetoothLeScannerCompat.getScanner() }

    private val bag = CompositeDisposable()

    override fun scan(setting: ScannerSetting): Observable<BeckonScanResult> {
        return scanner.scan(setting)
    }

    override fun scanList(setting: ScannerSetting): Observable<List<BeckonScanResult>> {
        return scanner.scanList(setting)
    }

    override fun findDevice(macAddress: String): BeckonDevice? {
        return connectedDevices[macAddress]
    }

    override fun devices(): Observable<List<MacAddress>> {
        return beckonStore.states().map { it.devices }.map { it.map { device -> device.macAddress } }
    }

    override fun getDevices(): List<BeckonDevice> {
        return connectedDevices.values.toList()
    }

    override fun connect(result: BeckonScanResult, characteristics: List<Characteristic>, autoConnect: Boolean): BeckonDevice {
        Timber.d("Connect $result")
        Timber.d("Add device to map: ${result.device.debugInfo()}")
        val device = connectedDevices[result.device.address]
                ?: BeckonDeviceImpl.create(context, result.device, characteristics, autoConnect)
        doConnect(device)
        return device
    }

    private fun doConnect(beckonDevice: BeckonDevice) {
        connectedDevices[beckonDevice.deviceData().macAddress] = beckonDevice
        Timber.d("connected devices $connectedDevices")
        beckonDevice.doConnect()
        beckonStore.dispatch(AddDevice(beckonDevice.deviceData()))
    }

    override fun disconnect(device: BeckonDevice): Observable<ConnectionState> {
        beckonStore.dispatch(RemoveDevice(device.deviceData()))
        return device.doDisconnect()
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
                        onBluetoothTurnedOn()
                    }
                }.disposedBy(bag)
    }

    override fun unregister(context: Context) {
        receiver.unregister(context)
        bag.clear()
    }

    private fun onBluetoothTurnedOn() {
        Timber.d("onBluetoothTurnedOn")
        val devices = connectedDevices.values
        devices.forEach { device ->
            Timber.d("try reconnect device: ${(device as BeckonDeviceImpl).bluetoothDevice().debugInfo()}")
            val info = device.info
            scan(settings(info))
                    .doOnNext { Timber.d("Scan $it") }
                    .take(1)
                    .map { connect(it, info.characteristics, true) }
                    .subscribe {
                        Timber.d("reconnect success $it")
                    }
        }
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

        return ScannerSetting(settings, filters)
    }
}

//    override fun states(): Observable<List<DeviceChange>> = TODO("Need to be implemented later")
//    {
//        return devices().flatMapIterable { it }
//                .map { it to findDevice(it) }
//                .filter { it.second != null }
//    }

//    override fun saveDevices(devices: List<BeckonDevice>): Single<Boolean> = TODO("Need to be implemented later")